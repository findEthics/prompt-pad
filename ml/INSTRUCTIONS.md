# CLauncher LLM — OpenCode Execution Brief

**You (opencode) are running this on the M4 MacBook.** Hermes prepared the dataset, training script,
and eval harness in this folder (`~/projects/clauncher-llm/` — copy/clone it to the Mac). Your job:
train the model, validate it passes the gate, then integrate it into the app on a new branch.

Do the phases in order. **Do not skip the eval gate.** If a phase fails, stop and report — do not
hand-wave past a failing metric.

---

## Goal

Make the CLauncher search bar natural-language capable. A tiny on-device Gemma 3 270M model
translates free text ("set an alarm for 10pm", "buy milk", "text mom I'll be late") into the
launcher's existing structured commands. The model ONLY produces a command intent; the existing
`CommandParser` / `CommandIntentFactory` pipeline still validates and requires user confirmation
before any action (never auto-execute — this is a hard invariant).

Model choice, hardware rationale, and full design are in `notes/PLAN.md`. TL;DR: single model,
no fallbacks, ~125 MB INT4, downloaded on first enable (never bundled in the APK).

---

## Repo facts (branch `physical-keyboard`)

- Repo: `findEthics/cLauncher`, Java, package `me.pompel.elauncher`.
- Command vocabulary lives in `CommandParser.Type` (enum). Today it has 12 commands.
- **You will add a 13th command `grocery` (+ `groceries` list form)** — see `INTEGRATION_SPEC.md`.
- Command list (canonical output tokens): call, text, hermes, timer, alarm, todo, todos,
  note, notes, grocery, groceries, event, torch, camera.
  - ⚠️ torch is invoked internally as `!t` (the enum name is `t`), NOT `!torch`.
  - todos/notes/groceries are no-arg "open the list" forms; todo/note/grocery take content.

---

## Phase 0 — Environment (macOS, Apple Silicon)

Unsloth officially supports Mac training ("Mac: Training, MLX and GGUF inference are ALL supported").
Use **Unsloth Core** (the Python package), NOT the Desktop app or Studio web UI.

```bash
brew install git cmake openssl
cd ~/projects/clauncher-llm
uv venv --python 3.11 .venv && source .venv/bin/activate
# Current install line per https://unsloth.ai/docs/get-started/install/pip-install.md
uv pip install unsloth --torch-backend=auto
uv pip install trl datasets transformers
```

If the pip line has changed, fetch the current one:
`curl -sL https://unsloth.ai/docs/get-started/install/pip-install.md`
(You can also query the docs: append `?ask=<question>` to any docs URL, or read
`https://unsloth.ai/docs/llms-full.txt`.)

---

## Phase 1 — (Re)generate dataset  [optional; already generated]

`dataset/train.jsonl` (~1.1k) and `dataset/eval.jsonl` (~76, stratified) already exist.
Regenerate only if you change the command set or want more variety:

```bash
cd dataset && python3 generate_dataset.py --out-dir . --seed 42
```

The generator is the place to add more natural-language paraphrases. Add templates to the
`*_T` banks and slot values to the pools, then regenerate. Keep the SYSTEM_PROMPT identical to
what the app sends at runtime (see INTEGRATION_SPEC.md — they must match exactly).

---

## Phase 2 — Train

```bash
cd training
python3 train_unsloth.py --train ../dataset/train.jsonl --eval ../dataset/eval.jsonl --out ../export
```

Produces `export/lora/`, `export/merged/`, and `export/preds.jsonl`. ~10–20 min on M4.
Tune `--epochs`, `--lr`, `--lora-r` if underfitting/overfitting (see failure guide below).

---

## Phase 3 — Validate  (HARD GATE)

```bash
cd eval
python3 evaluate.py --eval ../dataset/eval.jsonl --pred ../export/preds.jsonl --gate 0.85
```

**PASS = command accuracy ≥ 85%.** The script also reports slot accuracy, JSON validity,
decline recall (negatives), per-command accuracy, and top misclassifications.

If FAIL:
- Low JSON validity → raise epochs to 4–5; check chat template is `gemma-3`.
- One command confused for another (e.g. grocery↔todo) → add more contrastive examples of
  BOTH to the generator, regenerate, retrain.
- Slots wrong but command right → add more slot-format variety (times/durations) to the pools.
- Overfit (train loss ~0, eval poor) → lower epochs to 2, lower lora-r to 8.

Do NOT proceed to integration until the gate passes.

---

## Phase 4 — Export for on-device

Convert `export/merged/` to the MediaPipe `.task` (INT4) for Android. Follow Google's current
"convert a fine-tuned Gemma 270M for on-device" guide (AI Edge / MediaPipe). Notes:
- Prefer the CPU backend for first-run validation — some `q4_block128 .task` GPU builds emit
  zero tokens silently (known LiteRT-LM issue). Verify CPU works, then try GPU.
- Validate the `.task` loads and generates in the AI Edge Gallery app before wiring it into CLauncher.
- Keep the file OUT of the APK. It is downloaded on first enable of the NL feature into the app
  files dir, checksum-verified.

---

## Phase 5 — Integrate into the app

1. From `physical-keyboard`, cut the new branch:
   ```bash
   git fetch origin
   git checkout physical-keyboard
   git checkout -b physical-keyboard-LLM
   ```
2. Implement the changes in `INTEGRATION_SPEC.md` (the new `grocery` command + the NL interpreter
   layer + MediaPipe runtime + tests).
3. Keep the "never auto-execute" invariant: the LLM only prefills; user still confirms.
4. Run the existing unit + instrumented tests; add the new ones from the spec. All green before PR.
5. Open a PR from `physical-keyboard-LLM` → `physical-keyboard`. Do not merge without review.

---

## What Hermes did NOT do (so you know the boundary)

- Did not train (no GPU on that host) — the trained model does not exist yet; you produce it.
- Did not modify the `findEthics/cLauncher` repo or create the branch — you do that on the Mac.
- Did not write Java — the integration is specified in `INTEGRATION_SPEC.md` for you to implement,
  because you built the app and have the full repo + Android toolchain locally.

Report back: the eval gate output (Phase 3) and the PR link (Phase 5).
