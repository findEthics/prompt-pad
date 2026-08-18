# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

Titan is a lightweight Android launcher, forked from thypon/eLauncher, being extended with an explicit `!`-prefixed command-search mode (calling, texting, timers, notes, todos, calendar events, torch, camera) for a Unihertz Titan 2 physical-keyboard device. Package: `me.pompel.elauncher`. Single Gradle module (`app`), plain Java (no Kotlin), View-based UI (no Compose).

See `../PLAN.md` (repo root, one level up) for the full product spec, command contract, and phase history — read it before making behavioral changes to command routing/parsing. See `README.md` in this directory for the user-facing command-search contract (routing rule, per-command syntax/behavior, result states).

## Commands

Build and test from `eLauncher/`:
- Build debug: `./gradlew assembleDebug`
- Build release (minified, ProGuard, size-constrained — see PLAN.md APK size goal): `./gradlew assembleRelease`
- Unit tests: `./gradlew testDebugUnitTest`
- Single unit test: `./gradlew testDebugUnitTest --tests "me.pompel.elauncher.CommandParserTest"`
- Instrumented tests (requires running emulator/device): `./gradlew connectedDebugAndroidTest`
- Single instrumented test: `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=me.pompel.elauncher.MainActivityGestureInstrumentedTest`
- Install to connected device/emulator: `./gradlew installDebug`

No lint/ktlint/detekt config exists. No CI (`.github/workflows`) is configured. `fastlane/` only holds Play Store listing metadata, not build automation.

Build config: compileSdk/targetSdk 36, minSdk 19, Java 8 source/target compatibility, AGP 8.9.1.

## Natural-Language Model Workflow

The on-device model is trained and exported locally; model weights and `.task` files live under the ignored `ml/export/` directory and are never bundled into the APK.

Run these commands from `eLauncher/` after changing `ml/dataset/generate_dataset.py`:

```bash
# Regenerate the deterministic train/eval split.
ml/.venv/bin/python ml/dataset/generate_dataset.py --out-dir ml/dataset --seed 42

# Fine-tune Gemma 3 270M and generate held-out predictions.
ml/.venv/bin/python ml/training/train_unsloth.py \
  --train ml/dataset/train.jsonl --eval ml/dataset/eval.jsonl --out ml/export

# Hard gate before any Android/device work.
ml/.venv/bin/python ml/eval/evaluate.py \
  --eval ml/dataset/eval.jsonl --pred ml/export/preds.jsonl --gate 0.85

# Targeted false-positive probe against the merged HF model.
ml/.venv/bin/python ml/eval/probe.py --model ml/export/merged
```

The probe must return `"command":"none"` for incomplete inputs such as `wake`, `remind`, `buy`, `sh`, and `ala`, while slot-bearing commands such as `buy milk` and `set an alarm for 10pm` must remain valid. Do not proceed if the standard command gate or targeted probe fails.

Export the validated merged model to the MediaPipe TFLite model, using the eager MoE setting required by the current local exporter/Transformers combination:

```bash
uv pip install --python ml/.venv/bin/python --index-url https://pypi.org/simple 'protobuf>=5.28'
mkdir -p ml/export/retrained-mediapipe
ml/.venv/bin/python -m litert_torch.generative.export_hf \
  ml/export/merged ml/export/retrained-mediapipe \
  --prefill_lengths=128 --cache_length=512 \
  --quantization_recipe=dynamic_wi8_afp32 \
  --bundle_litert_lm=False --use_jinja_template=True \
  --sampler_top_k=1 --sampler_top_p=1.0 --sampler_temperature=0.0 \
  --moe_exports_implementation=eager

mkdir -p ml/export/retrained-mediapipe/task-staging
cp ml/export/retrained-mediapipe/tmp*/model_quantized.tflite \
  ml/export/retrained-mediapipe/task-staging/TF_LITE_PREFILL_DECODE
unzip -p ml/export/clauncher-gemma3-270m.task TOKENIZER_MODEL \
  > ml/export/retrained-mediapipe/task-staging/TOKENIZER_MODEL
unzip -p ml/export/clauncher-gemma3-270m.task METADATA \
  > ml/export/retrained-mediapipe/task-staging/METADATA
(cd ml/export/retrained-mediapipe/task-staging && \
  zip -0 -j ../clauncher-gemma3-270m.task \
  TF_LITE_PREFILL_DECODE TOKENIZER_MODEL METADATA)
```

The task ZIP entry names are required exactly: `TF_LITE_PREFILL_DECODE`, `TOKENIZER_MODEL`, and `METADATA`. Smoke-test the TFLite locally, then build/install the app and push the task before running `connectedDebugAndroidTest`:

```bash
./gradlew testDebugUnitTest assembleDebug
adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5554 push ml/export/retrained-mediapipe/clauncher-gemma3-270m.task \
  /sdcard/Android/data/me.pompel.elauncher/files/clauncher-gemma3-270m.task
./gradlew connectedDebugAndroidTest
```

Keep the model opt-in and preserve the no-auto-call/send invariant. A missing or malformed model output must fall back to app search; only the existing parser and intent path may execute a command.

## Architecture

All source is a flat package: `app/src/main/java/me/pompel/elauncher/`. There are no sub-packages — group by responsibility mentally, not by directory.

**Command-search pipeline** (the main area of active development):
1. `CommandQueryClassifier` — decides app-search vs. command mode. Routing rule is strict: command mode iff `query.isNotEmpty() && query.charAt(0) == '!'`. This must never fall back silently to app search on unknown commands.
2. `CommandParser` — parses the raw `!command args` string into a structured form once in command mode.
3. `CommandIntentFactory` — builds the actual Android `Intent` for each command (dialer, SMS, calendar, clock, camera). Commands only ever *open* system apps with prefilled data — they must never auto-send/auto-call/auto-insert.
4. `ContactsResolver` — resolves contact names/numbers for `!call` and `!text`; must degrade gracefully (not crash) when Contacts permission is denied, falling back to raw-number support only.
5. `CommandAdapter` — RecyclerView rows for command help/suggestions/preview/validation-error/unknown/contact-choice/permission-denied/unavailable/confirmation/success states (see README for the full state list).
6. `TorchController` — rear-flash toggle for `!t`; must report on/off/unavailable/denied without crashing, including on virtual cameras (see known emulator limitation in latest handoff under `handoffs/`).

**App-search / home screen**: `MainActivity` is the `CATEGORY_HOME` launcher Activity and hosts both the sparse homescreen and the app-drawer search (fuzzy app matching via `recyclerAdapter`, unrelated to command mode). Gesture handling (swipe up/down, double-tap to previous launcher) lives here too.

**Persistence**: `NotesRepository`/`TodosRepository` wrap `KeyValueStore` (`SharedPreferencesKeyValueStore` + `PersistentValueCodec`) — all local-only, no network/sync by design (V1 scope). `Note`/`Todo` are plain POJOs. `NotesActivity`/`TodosActivity` are the standalone list screens opened by `!notes`/`!todos`.

**Testing pattern**: `CommandContractFixtureTest` is data-driven off `app/src/test/resources/me/pompel/elauncher/command-cases.tsv` — this TSV is the source of truth for routing/parsing contract cases and should be updated alongside any change to command syntax or validation behavior, in lockstep with the README command-contract table and `PLAN.md`. `TimeSource` is an injected time abstraction used to keep timer/note/todo timestamp logic testable. `InMemoryKeyValueStore` is the test double for persistence tests.

## Constraints (from PLAN.md — do not violate without explicit user direction)

- No new command types, aliases, macros, or arbitrary-intent execution beyond the fixed V1 command list.
- No network calls, analytics, telemetry, or cloud sync.
- No "type-anywhere" home-screen key capture (deferred to a separate future branch, not this codebase's current scope).
- Keep release APK size minimal — avoid adding new libraries; this is a hard project constraint, not a style preference.
- eLauncher upstream is GPL-3.0 — this fork must stay GPL-compatible if ever distributed beyond personal use.
