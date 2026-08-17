#!/usr/bin/env python3
"""
Evaluate a fine-tuned CLauncher command model against the held-out split.

Computes:
  - top-1 command accuracy (did it pick the right command?)
  - slot accuracy (did the arguments match, per command?)
  - JSON validity rate (did it emit parseable JSON at all?)
  - negative/decline accuracy ({"command":"none"} recall)
  - a confusion breakdown for the worst clusters

Two modes:
  A) --pred FILE.jsonl : you already ran inference; FILE has one predicted
     assistant string per line, aligned to eval.jsonl order. (Framework-agnostic;
     use this after generating predictions in the Unsloth notebook.)
  B) --model DIR : load a merged HF model locally and run inference here
     (requires transformers+torch; slow on CPU, intended for the M4/GPU box).

Gate: PASS if command_accuracy >= --gate (default 0.85).

Usage:
  python3 evaluate.py --eval eval.jsonl --pred preds.jsonl
  python3 evaluate.py --eval eval.jsonl --model ../export/merged --gate 0.85
"""
import argparse
import json
import re
import sys
from collections import Counter, defaultdict

def load_eval(path):
    rows = []
    with open(path) as f:
        for line in f:
            line = line.strip()
            if not line:
                continue
            msgs = json.loads(line)["messages"]
            system = next(m["content"] for m in msgs if m["role"] == "system")
            user = next(m["content"] for m in msgs if m["role"] == "user")
            gold = json.loads(next(m["content"] for m in msgs if m["role"] == "assistant"))
            rows.append({"system": system, "user": user, "gold": gold})
    return rows

def extract_json(text):
    """Best-effort: pull the first {...} object out of a model response."""
    if text is None:
        return None
    m = re.search(r"\{.*\}", text, re.DOTALL)
    if not m:
        return None
    try:
        return json.loads(m.group(0))
    except json.JSONDecodeError:
        # tolerate single quotes / trailing junk
        try:
            return json.loads(m.group(0).replace("'", '"'))
        except Exception:
            return None

def slots_match(gold, pred):
    """All non-command keys must match (case-insensitive, whitespace-normalized)."""
    def norm(v):
        return re.sub(r"\s+", " ", str(v).strip().lower())
    gkeys = {k: gold[k] for k in gold if k != "command"}
    pkeys = {k: pred.get(k) for k in gkeys}
    if set(gkeys) != set(k for k in pred if k != "command"):
        return False
    return all(norm(gkeys[k]) == norm(pkeys.get(k)) for k in gkeys)

def run_local_model(rows, model_dir):
    from transformers import AutoModelForCausalLM, AutoTokenizer
    import torch
    tok = AutoTokenizer.from_pretrained(model_dir)
    model = AutoModelForCausalLM.from_pretrained(model_dir, torch_dtype="auto",
                                                 device_map="auto")
    preds = []
    for r in rows:
        chat = [{"role": "system", "content": r["system"]},
                {"role": "user", "content": r["user"]}]
        inputs = tok.apply_chat_template(chat, add_generation_prompt=True,
                                         return_tensors="pt").to(model.device)
        out = model.generate(inputs, max_new_tokens=64, do_sample=False,
                             pad_token_id=tok.eos_token_id)
        text = tok.decode(out[0][inputs.shape[1]:], skip_special_tokens=True)
        preds.append(text)
    return preds

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--eval", default="../dataset/eval.jsonl")
    ap.add_argument("--pred", help="jsonl/txt of predicted assistant strings, aligned to eval")
    ap.add_argument("--model", help="local merged HF model dir to run inference")
    ap.add_argument("--gate", type=float, default=0.85)
    args = ap.parse_args()

    rows = load_eval(args.eval)

    if args.pred:
        with open(args.pred) as f:
            preds = [l.rstrip("\n") for l in f if l.strip() != ""]
        if len(preds) != len(rows):
            print(f"WARN: {len(preds)} preds vs {len(rows)} eval rows", file=sys.stderr)
    elif args.model:
        preds = run_local_model(rows, args.model)
    else:
        print("ERROR: provide --pred or --model", file=sys.stderr)
        sys.exit(2)

    n = len(rows)
    cmd_correct = slot_correct = json_valid = 0
    neg_total = neg_correct = 0
    confusion = defaultdict(Counter)
    per_cmd = defaultdict(lambda: [0, 0])  # cluster -> [correct, total]
    failures = []

    for r, praw in zip(rows, preds):
        gold = r["gold"]; gcmd = gold["command"]
        pred = extract_json(praw)
        per_cmd[gcmd][1] += 1
        if gcmd == "none":
            neg_total += 1
        if pred is not None and "command" in pred:
            json_valid += 1
            pcmd = pred["command"]
            confusion[gcmd][pcmd] += 1
            if pcmd == gcmd:
                cmd_correct += 1
                per_cmd[gcmd][0] += 1
                if gcmd == "none":
                    neg_correct += 1
                if slots_match(gold, pred):
                    slot_correct += 1
                else:
                    failures.append((r["user"], gold, pred, "slot"))
            else:
                failures.append((r["user"], gold, pred, "command"))
        else:
            confusion[gcmd]["<invalid>"] += 1
            failures.append((r["user"], gold, praw, "invalid_json"))

    cmd_acc = cmd_correct / n
    slot_acc = slot_correct / n
    print(f"=== CLauncher command-model evaluation ({n} held-out) ===")
    print(f"JSON validity     : {json_valid/n:6.1%}")
    print(f"Command accuracy  : {cmd_acc:6.1%}   (gate {args.gate:.0%})")
    print(f"Full (cmd+slots)  : {slot_acc:6.1%}")
    if neg_total:
        print(f"Decline recall    : {neg_correct/neg_total:6.1%}  ({neg_correct}/{neg_total} negatives)")
    print("\nper-command accuracy:")
    for c in sorted(per_cmd):
        cor, tot = per_cmd[c]
        print(f"  {c:12s} {cor/tot:6.1%}  ({cor}/{tot})")

    # show worst confusions
    mis = [(g, p, cnt) for g, row in confusion.items() for p, cnt in row.items()
           if p != g and cnt > 0]
    if mis:
        print("\ntop misclassifications (gold -> pred):")
        for g, p, cnt in sorted(mis, key=lambda x: -x[2])[:8]:
            print(f"  {g:10s} -> {p:12s} x{cnt}")

    if failures:
        print(f"\nsample failures (up to 10 of {len(failures)}):")
        for user, gold, pred, kind in failures[:10]:
            print(f"  [{kind}] {user!r}\n      gold={json.dumps(gold)}  pred={pred}")

    status = "PASS" if cmd_acc >= args.gate else "FAIL"
    print(f"\nGATE: {status}  (command accuracy {cmd_acc:.1%} vs {args.gate:.0%})")
    sys.exit(0 if status == "PASS" else 1)

if __name__ == "__main__":
    main()
