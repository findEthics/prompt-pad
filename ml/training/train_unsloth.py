#!/usr/bin/env python3
"""
Fine-tune Gemma 3 270M on the CLauncher NL->command dataset with Unsloth Core.

Runs on Apple Silicon (M4) per Unsloth docs: "Mac: Training, MLX and GGUF
inference are ALL supported." Also runs on NVIDIA/Colab unchanged.

Prereqs (macOS, once):
    brew install git cmake openssl
    # then install Unsloth Core into a uv/venv (see INSTRUCTIONS.md for the
    # exact current pip line from https://unsloth.ai/docs/get-started/install/pip-install )

Train:
    python3 train_unsloth.py \
        --train ../dataset/train.jsonl \
        --out   ../export

Outputs:
    ../export/lora/           LoRA adapters
    ../export/merged/         merged 16-bit HF model (for eval + conversion)
    ../export/preds.jsonl     predictions on the eval split (for eval/evaluate.py)
    ../export/gguf/           optional q4_k_m GGUF (--gguf)
"""
import argparse
import json
import os
from pathlib import Path

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--train", default="../dataset/train.jsonl")
    ap.add_argument("--eval", default="../dataset/eval.jsonl")
    ap.add_argument("--out", default="../export")
    ap.add_argument("--model", default="unsloth/gemma-3-270m-it")
    ap.add_argument("--max-seq", type=int, default=1024)
    ap.add_argument("--epochs", type=float, default=3.0)
    ap.add_argument("--lr", type=float, default=2e-4)
    ap.add_argument("--batch", type=int, default=8)
    ap.add_argument("--grad-accum", type=int, default=2)
    ap.add_argument("--lora-r", type=int, default=16)
    ap.add_argument("--gguf", action="store_true", help="also export q4_k_m GGUF")
    ap.add_argument("--predict", action="store_true", default=True,
                    help="run inference on eval split and write preds.jsonl")
    args = ap.parse_args()

    out = Path(args.out); out.mkdir(parents=True, exist_ok=True)

    from unsloth import FastModel
    from unsloth.chat_templates import get_chat_template
    from datasets import load_dataset
    from trl import SFTTrainer, SFTConfig
    import torch

    # 1) Load base (4-bit where supported; Unsloth handles Metal/CUDA backends).
    model, tokenizer = FastModel.from_pretrained(
        model_name=args.model,
        max_seq_length=args.max_seq,
        load_in_4bit=True,
        full_finetuning=False,
    )

    # 2) Attach LoRA.
    model = FastModel.get_peft_model(
        model,
        r=args.lora_r,
        lora_alpha=args.lora_r * 2,
        lora_dropout=0.0,
        bias="none",
        target_modules=["q_proj", "k_proj", "v_proj", "o_proj",
                        "gate_proj", "up_proj", "down_proj"],
        use_gradient_checkpointing="unsloth",
        random_state=42,
    )

    tokenizer = get_chat_template(tokenizer, chat_template="gemma-3")

    # 3) Dataset: our JSONL already has {"messages":[...]} -> apply chat template.
    def fmt(ex):
        return {"text": tokenizer.apply_chat_template(
            ex["messages"], tokenize=False, add_generation_prompt=False)}

    ds = load_dataset("json", data_files=args.train, split="train").map(fmt)

    # 4) Train.
    trainer = SFTTrainer(
        model=model,
        tokenizer=tokenizer,
        train_dataset=ds,
        args=SFTConfig(
            dataset_text_field="text",
            per_device_train_batch_size=args.batch,
            gradient_accumulation_steps=args.grad_accum,
            warmup_ratio=0.05,
            num_train_epochs=args.epochs,
            learning_rate=args.lr,
            logging_steps=10,
            optim="adamw_8bit",
            weight_decay=0.01,
            lr_scheduler_type="linear",
            seed=42,
            output_dir=str(out / "trainer"),
            report_to="none",
        ),
    )
    trainer.train()

    # 5) Save LoRA + merged 16-bit (merged is what we eval + convert to .task).
    model.save_pretrained(str(out / "lora"))
    tokenizer.save_pretrained(str(out / "lora"))
    model.save_pretrained_merged(str(out / "merged"), tokenizer,
                                 save_method="merged_16bit")

    # 6) Optional GGUF (only for a llama.cpp runtime path).
    if args.gguf:
        model.save_pretrained_gguf(str(out / "gguf"), tokenizer,
                                   quantization_method="q4_k_m")

    # 7) Predict on eval split -> preds.jsonl (feeds eval/evaluate.py --pred).
    if args.predict:
        from transformers import TextStreamer  # noqa
        rows = [json.loads(l) for l in open(args.eval) if l.strip()]
        FastModel.for_inference(model)
        preds = []
        for r in rows:
            msgs = [m for m in r["messages"] if m["role"] in ("system", "user")]
            inputs = tokenizer.apply_chat_template(
                msgs, add_generation_prompt=True, return_tensors="pt").to(model.device)
            out_ids = model.generate(input_ids=inputs, max_new_tokens=64,
                                     do_sample=False, temperature=None, top_p=None)
            text = tokenizer.decode(out_ids[0][inputs.shape[1]:],
                                    skip_special_tokens=True).strip()
            preds.append(text.replace("\n", " "))
        with open(out / "preds.jsonl", "w") as f:
            f.write("\n".join(preds) + "\n")
        print(f"[done] wrote {out/'preds.jsonl'} ({len(preds)} rows). "
              f"Now run: python3 ../eval/evaluate.py --eval {args.eval} "
              f"--pred {out/'preds.jsonl'}")

    print(f"[done] merged model at {out/'merged'} — ready for .task conversion.")

if __name__ == "__main__":
    main()
