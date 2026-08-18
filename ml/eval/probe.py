#!/usr/bin/env python3
"""Probe a local merged Promptpad model for command false positives."""
import argparse
import json
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
ML_ROOT = ROOT / "ml"
sys.path.insert(0, str(ML_ROOT / "dataset"))

from generate_dataset import SYSTEM_PROMPT  # noqa: E402


PROBE_INPUTS = [
    # Reported failures and their obvious variants.
    "wake", "wake me", "remind", "remind me", "buy", "get", "add",
    "sh", "ala", "cal", "rem", "tim", "gro", "tex", "not", "cam", "tor",
    # Bare command-like words that must not invent required slots.
    "call", "ring", "dial", "text", "message", "note", "alarm",
    "set an alarm", "timer", "event", "calendar", "hermes", "torch",
    # Valid controls and slot-bearing requests.
    "flashlight", "camera", "set an alarm for 10pm", "set a timer for 5 minutes",
    "buy milk", "remind me to call mom", "open spotify", "what's the weather",
]


def extract_object(text):
    start = text.find("{")
    end = text.rfind("}")
    if start < 0 or end < start:
        return None
    try:
        return json.loads(text[start:end + 1])
    except json.JSONDecodeError:
        return None


def map_command(value):
    if not isinstance(value, dict):
        return None
    command = value.get("command")
    slots = {
        "call": ["contact"],
        "text": ["contact", "body"],
        "hermes": ["body"],
        "timer": ["duration"],
        "alarm": ["time"],
        "todo": ["text"],
        "note": ["text"],
        "grocery": ["item"],
        "event": ["date", "time", "title"],
    }
    if command in ("none", "todos", "notes", "groceries", "torch", "camera"):
        return None if command == "none" else "!" + ("t" if command == "torch" else command)
    if command not in slots or any(not str(value.get(slot, "")).strip() for slot in slots[command]):
        return None
    return "!" + command + " " + " ".join(str(value[slot]).strip() for slot in slots[command])


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--model", default=str(ML_ROOT / "export" / "merged"))
    args = parser.parse_args()

    from mlx_lm import generate, load
    from mlx_lm.sample_utils import make_sampler

    model, tokenizer = load(args.model)
    sampler = make_sampler(temp=0)
    for user in PROBE_INPUTS:
        messages = [
            {"role": "system", "content": SYSTEM_PROMPT},
            {"role": "user", "content": user},
        ]
        prompt = tokenizer.apply_chat_template(
            messages, tokenize=False, add_generation_prompt=True)
        raw = generate(model, tokenizer, prompt, max_tokens=64, sampler=sampler).strip()
        parsed = extract_object(raw)
        print(json.dumps({
            "input": user,
            "raw": raw,
            "parsed": parsed,
            "mapped": map_command(parsed),
        }, ensure_ascii=False))


if __name__ == "__main__":
    main()
