Promptpad LLM — on-device natural-language command layer
========================================================

Make the Promptpad search bar natural-language capable with a tiny on-device
Gemma 3 270M model. See notes/PLAN.md for the full exploration + rationale.

Layout
------
  INSTRUCTIONS.md        <- START HERE. OpenCode execution brief (run on the M4).
  INTEGRATION_SPEC.md    <- Java integration spec (new grocery cmd + NL layer).
  notes/PLAN.md          <- Full plan: model choice, hardware, dataset design.

  dataset/
    generate_dataset.py  <- NL->command JSONL generator (13 commands + negatives).
    train.jsonl          <- ~1.1k training pairs (generated).
    eval.jsonl           <- ~76 stratified held-out pairs (generated).
  training/
    train_unsloth.py     <- Unsloth Core fine-tune (Mac/Apple-Silicon or CUDA).
  eval/
    evaluate.py          <- Scores preds vs held-out; HARD GATE = 85% cmd accuracy.
  export/                <- (produced on the Mac) lora/, merged/, preds.jsonl, .task

Quickstart (on the M4)
----------------------
  1. Read INSTRUCTIONS.md.
  2. Env:   brew install git cmake openssl; uv venv --python 3.11 .venv; source .venv/bin/activate
            uv pip install unsloth --torch-backend=auto trl datasets transformers
  3. Train: cd training && python3 train_unsloth.py
  4. Gate:  cd eval && python3 evaluate.py --pred ../export/preds.jsonl
  5. Export .task (MediaPipe) + integrate per INTEGRATION_SPEC.md on branch
     physical-keyboard-LLM (cut from physical-keyboard).

Status: dataset + scripts + specs prepared by Hermes. Training/export/integration
happen on the Mac (opencode). No repo changes or training done yet.
