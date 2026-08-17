# On-Device LLM Command Layer for CLauncher (Titan) — Exploration & Plan

> **Mode:** Plan + explore only. No implementation performed. Repo not modified.

**Goal:** Add a tiny, fully on-device open-source LLM to the CLauncher app-drawer so that free-text search input (not just `!`-prefixed commands) can be interpreted into the launcher's *existing* safe command actions — mirroring the "Commands" feature from *Introducing Commands on Minimal Phone 2*.

**Repo:** `findEthics/cLauncher` (branded "Titan"), Java, package `me.pompel.elauncher`. Based on NoLauncher / OLauncher lineage.

---

## 1. Current context (what the repo already gives you)

Your command subsystem is already clean, framework-decoupled, and TDD-covered. The pieces:

- `CommandParser.java` — pure parser (no Android deps). Input must start with `!`. Emits typed `*Command` objects: call, text, hermes (Telegram), timer, alarm, note, todo, todos, notes, event, torch (`!t`), camera, help.
- `CommandQueryClassifier.java` — live-as-you-type classifier for the drawer; decides app-search vs command-search, produces suggestions/previews/validation. Never executes.
- `CommandIntentFactory.java` — builds **prefilled** system intents only (dial, SMS compose, Telegram resolve, timer/alarm form, calendar insert, camera). Deliberately never performs an action directly — "safe outcome after explicit submission" is a documented invariant.
- Local repositories: `NotesRepository`, `TodosRepository` (+ `KeyValueStore`).
- Tests: `CommandParserTest`, `CommandQueryClassifierTest`, `CommandContractFixtureTest`, plus instrumented tests. Machine-readable acceptance cases in `app/src/test/resources/me/pompel/elauncher/command-cases.tsv`.

**Key insight:** You do NOT need the LLM to *do* anything. You already have a safe, deterministic execution layer. The LLM's only job is **natural-language → structured command**. It sits *in front of* `CommandParser`, translating "remind me to call mom at 6" into `!alarm 18:00` / `!call mom`, then the existing pipeline takes over with the same human-approval-before-action guarantees. This keeps the risky part (intents) deterministic and testable, and makes the model swappable.

## 2. What the Minimal Phone 2 "Commands" video shows (target UX)

From the official description (transcript disabled). Commands executed *directly from the home screen / search field*: send a text, set a timer, add a to-do, make a call, create a calendar event, write a note. That is almost exactly your existing command set — the difference is the **input is natural language**, not a `!`-syntax. So the feature to build is: an optional NL interpretation layer that resolves free text into one of your existing command types, shows a preview, and requires explicit confirmation (matching your "never send/never call automatically" invariant).

## 2b. Target hardware baseline — the average ~€250 phone (2026)

Purpose: size the model against what a *typical* buyer's device can actually run, not a flagship. Sourced from current under-€250 buyer guides (Kimovil, frontum, freenet) and Android on-device LLM benchmarks.

**Representative spec envelope for a ~€250 (or currency-equivalent) phone in 2026:**

| Component | Typical value at ~€250 | Implication for on-device LLM |
| --- | --- | --- |
| **RAM** | **6 GB** (range 4–8 GB; 2026 DRAM price spike is pushing many midrange back to 6 GB) | This is the binding constraint. After OS + background apps, a foreground app realistically gets **~1.5–2.5 GB** usable. |
| **SoC** | **MediaTek Dimensity 6100+ / Helio G-series, or Snapdragon 4 Gen 2** class | Modest CPU/GPU. **No usable dedicated NPU path** for third-party LLMs at this tier — inference runs on CPU/GPU via MediaPipe/XNNPACK. |
| **Storage** | 128–256 GB | Fine for a downloaded model file; not a constraint. |
| **Example devices** | Samsung Galaxy A17, Xiaomi Redmi Note series, Motorola G-series | — |

**What that envelope can actually run (Q4/INT4, MediaPipe on-device):**

| Model class | On-disk (Q4/INT4) | Peak RAM while loaded | Throughput on this tier | Verdict for a €250 phone |
| --- | --- | --- | --- | --- |
| **~270M (Gemma 3 270M)** | ~**125 MB** (INT4) | ~**0.2–0.4 GB** | very fast, **>50 tok/s** | ✅ Comfortable, huge headroom, instant feel |
| **~0.5B (Qwen2.5-0.5B)** | ~300–400 MB | ~0.5–0.7 GB | fast | ✅ Fits |
| **~1B (Gemma 3 1B)** | ~550–700 MB | ~1.0–1.5 GB | **~20–30 tok/s** | ⚠️ Fits but tight on a 6 GB device under memory pressure; larger download |
| **2B–3B** | 1.2–2.5 GB | ~2–3 GB+ | usable only on flagships | ❌ Risky/OOM on 6 GB midrange |

**Reading of the data:** benchmarks show even budget chips (Snapdragon 6 Gen 3 class) run **1B models at 20+ tok/s**, and the practical range on *most* phones is 1B–3B. But the €250 tier sits at the *bottom* of that range (6 GB RAM, no NPU), so 1B is the ceiling for a *comfortable* experience, and anything ≥2B is unsafe here.

## 2c. Conclusion — single model selection (no backups, minimal footprint)

Given (a) the task is deliberately narrow — natural language → **one of ~12 existing commands** — and (b) your hard constraints are *smallest possible app* and *no backup models*, the decision is:

### ✅ Selected: **Gemma 3 270M** (INT4 / Q4), single model, no fallback.

Rationale:
- **Footprint:** ~125 MB INT4 is an order of magnitude smaller than a 1B model — directly serves the "keep the app tiny" mandate. (Note: the community ONNX Android repack `smartvest-llc/...` is ~900 MB because it's a different quant/packaging; prefer the MediaPipe `.task` / LiteRT Community Q4 build at ~125–300 MB, not the ONNX repack.)
- **Fits with massive headroom:** ~0.2–0.4 GB RAM on a 6 GB device — no OOM risk even under memory pressure, unlike 1B.
- **Speed:** >50 tok/s → command previews feel instant, critical for a launcher search box.
- **Task-appropriate:** you are not doing open chat. Constrained JSON output over a 12-command vocabulary is well within 270M's ability, *especially* with the function-calling-tuned variant. The existing `CommandParser` re-validates every output, so the model only needs to get the *intent* right, not produce perfect prose.

**Still keep APK small regardless of model:** do **not** bundle the model in the APK. Download the `.task` file on first enable of the NL feature, store in app files dir, checksum-verify. APK stays lean; the 125 MB lands only for users who opt in.

**Open sub-decision (defer to Phase A):** `google/functiongemma-270m-it` (function-calling fine-tune, best intent mapping) **vs** `google/gemma-3-270m-it` + strict system prompt (plainer, may have a cleaner MediaPipe `.task` path). Both are 270M — pick whichever gives cleaner accuracy on your command set in the Phase A device test. This is *one* model shipped, not a runtime fallback chain — it satisfies "no backup models."

## 2d. Community capability & limitations review (Reddit / HN / GitHub / vendor benchmarks)

This section captures what real users and evaluators report about Gemma 3 270M, and the single most important consequence for our design.

### What it's genuinely good at (verified strengths)
- **Instruction-following for its size:** 51.2% on IFEval — beats Qwen 2.5 0.5B (~28–42% depending on source) and SmolLM2. Best-in-class among sub-0.5B models (only Liquid LFM2-350M scores higher, at +80M params). HN commenters call its instruction-following "good for most cases."
- **Structured text / classification / extraction:** The consistent community verdict (r/LocalLLaMA, Google's own positioning, multiple blogs) is that this model is built for **task-specific fine-tuning** on classification, entity extraction, and structured output — *not* open chat. When specialized it hits "astonishing accuracy" on narrow tasks.
- **Efficiency (the headline):** INT4 runs in ~125 MB, ~0.75% battery for 25 conversations on a Pixel 9 Pro, <2% quality loss from quantization (it was quantization-aware trained). This is exactly the profile we need.
- **Fine-tune is cheap and fast:** ~2,000 examples, 12–18 min on a consumer GPU / free Colab. Real deployments cited (e.g. medical PII masking) use exactly this recipe.

### Documented limitations (the honest caveats)
- **Base/instruct model out-of-the-box is "nothing special":** top r/LocalLLaMA comment — *"It feels very much like a 270m model to me… even basic completions have repetitive phrases."* Edge-eval writeups report **high hallucination rates and reasoning flaws** when used as a general assistant.
- **Tiny world knowledge:** trades knowledge for instruction-following. It should never be asked open questions or to reason — it will confidently make things up.
- **Tends to continue/ramble** rather than answer directly unless constrained.
- **Multimodal in name only at this size** — treat as text-only for our purposes.

### The decisive finding: base vs fine-tuned function calling
This is the key input for our use case. Google shipped **FunctionGemma-270M** specifically for NL→function-call, but the numbers are stark:
- **Base FunctionGemma on Mobile Actions dataset: ~58%** (Google's own card). distil labs measured base multi-turn tool-calling at just **10–39%** — they bluntly call base FunctionGemma *"unusable for multi-turn tool calling."*
- **After task-specific fine-tuning: 85%** (Google's "Mobile Actions Fine-Tune"), and distil labs reached **90–97%**, matching a 120B teacher model, still at ~288 MB quantized.

**Implication for CLauncher:** 270M is the right *size and cost*, but shipping it **zero-shot / prompt-only will not be reliable enough** for command routing — expect it to misroute a meaningful fraction of utterances. The model becomes excellent **only after a small supervised fine-tune on our own command vocabulary.** This is affordable (2k examples, one short Colab run) and turns a coin-flip into 85–97% accuracy.

### How this refines our fit
- ✅ Size, battery, RAM, speed: ideal for the €250 baseline (Section 2b/2c unchanged).
- ✅ Task shape (narrow structured classification/extraction) is *exactly* Gemma 270M's sweet spot — this is the intended use, not a stretch.
- ⚠️ **Correction to earlier assumption:** do **not** plan on a pure system-prompt approach. Plan for a **fine-tuned** Gemma 3 270M (or fine-tuned FunctionGemma-270M) trained on ~2,000 synthetic examples mapping natural-language phrasings → our command schema. Prompt-only is acceptable *only* as a Phase-A throwaway probe, not as the shipped design.
- ✅ Safety net already in place: our `CommandParser` re-validates every model output, and previews require explicit confirmation — so even the residual ~5–15% error rate degrades gracefully to "no command / use app search," never to a wrong action.

### Consequence for the build plan
Add a **model-specialization workstream** ahead of integration:
1. Define the command schema + generate ~2,000 synthetic NL→command training pairs (cover all 12 command types, paraphrases, contacts, durations, times, negatives/out-of-vocabulary).
2. Fine-tune Gemma 3 270M (Unsloth/Colab, QLoRA) on that set; export INT4 `.task` for MediaPipe.
3. Evaluate against a held-out set of real phrasings; target ≥85% top-1 command accuracy before any launcher code is wired.

## 2e. Decision analysis: is an LLM even necessary?

This is the pivotal question. Given only ~12 commands, the honest engineering answer is: **you do not need an LLM to have a working, even *good*, natural-language command bar. An LLM is a value-add for one specific dimension — messy free-text robustness and text rewriting — not a requirement.**

### The task, precisely stated
Map a short utterance to: (a) one of ~12 intents, and (b) a few slots (contact, time/duration, note/todo/message text). That is **intent classification + slot filling over a tiny closed label set** — one of the most solvable problems in NLP, well handled by non-neural methods long before LLMs.

### What a deterministic (no-ML) layer already achieves
Rules + fuzzy-match on top of the existing `CommandQueryClassifier`:
- **Verb/keyword triggers:** text/message/sms→text; call/ring/dial→call; remind/alarm/wake→alarm; timer/countdown→timer; note→note; todo/task/"remember to"→todo; event/meeting→event; torch/flashlight→torch.
- **Synonym tables + stemming** per intent (deterministic, debuggable).
- **Slot extraction via regex/known parsers:** times ("6pm", "18:00", "in 10 min", "tomorrow 9"), durations; contacts via existing `ContactsResolver`.
- **Fuzzy matching** (Levenshtein/Jaro-Winkler) for typos and names.
- **Ambiguity → existing preview UI:** low confidence or multi-match → ranked tap-to-pick suggestions (already built).

For 12 intents with distinctive trigger verbs, a good deterministic layer realistically handles **~80–90% of natural phrasings** at **0 MB, 0 battery, 0 latency, fully offline, 100% predictable, trivially unit-testable** (extends the existing `command-cases.tsv`). Very strong baseline, near-zero cost, zero bloat.

### Where deterministic genuinely breaks (the LLM's value zone)
The LLM earns its ~125 MB **only** here:
1. **Paraphrase/idiom variety** not hand-coded — "shoot mom a quick line", "let the wife know I'm late", "ping dad".
2. **Compound/implicit intent** — "leaving now, tell Sarah I'll be 20 late"; "wake me 7h from now".
3. **Body rewriting/normalization** — "im gonna be late soz" → clean SMS body (the Minimal Phone demo leans on this).
4. **Long-tail recall** — the 10–20% rules never anticipated; fine-tuned Gemma 270M pushes toward 85–97%.

None of these are essential to a *functional* command bar — they're "it just understands me" polish, which is exactly Minimal Phone's selling point, but a want, not a need.

### When to just extend the command list (skip the LLM)
- Command set stays small and closed (it is).
- New "understanding" is really a **new trigger word or slot pattern** — a few-line synonym/regex add, not semantic reasoning.
- You prioritize tiny APK, zero battery, offline determinism, testability (matches your "keep it as small as possible" constraint).
- Failure must be predictable — a launcher that occasionally *hallucinates* an action is worse than one that says "no match, here are apps."

**Rule of thumb:** if the new capability is *"recognize these words/this pattern,"* extend the list. If it's *"understand what the user means,"* that's the LLM.

### When the LLM clearly adds value (ship it)
- You want the **Minimal-Phone-grade "type anything, it figures it out"** experience as a headline feature.
- You want **message-body rewriting**, relative-time reasoning, or compound utterances.
- You'll invest in the fine-tune + eval workstream (§2d) and accept a ~125 MB opt-in download and a small, bounded misroute rate (caught by `CommandParser` re-validation + confirm-before-act).

### Recommended path (staged, low-regret)
1. **Phase 0 — build the deterministic NL layer first.** Extend the classifier/parser with verb-synonym tables, fuzzy matching, slot parsers; measure against an expanded `command-cases.tsv`. Cheap, ships value now, zero bloat, correct baseline regardless of what follows.
2. **Decide with data:** if Phase 0 already clears ~90% of what you actually type, **extend the list and stop** — the LLM isn't worth 125 MB.
3. **Phase 1 — add the LLM only as an optional, downloaded upgrade *behind* the deterministic layer:** rules handle clear cases instantly; the model is invoked only on low-confidence/no-match inputs (fallback interpreter) and for body-rewriting. Base app stays tiny, the model is strictly additive, and its failures can only improve on "no match," never override a correct deterministic hit.

**Bottom line:** deterministic-first, **LLM-as-fallback**. This honors every constraint — smallest base app, no wasted model when rules suffice, and the LLM's real value (long-tail understanding + rewriting) captured only where it pays for itself. Don't lead with the model; earn it.

## 3. On-device model options (Hugging Face survey)

Ranked for this exact use case (tiny, action/function-oriented, Android-runnable):

| Model | Size | Why it fits | Notes |
| --- | --- | --- | --- |
| **`google/functiongemma-270m-it`** ⭐ | 270M | Gemma-3 270M **fine-tuned for function/tool calling** — literally built to turn NL into structured action calls. Google benchmarked it on a Samsung S25 Ultra. | Best semantic match. Emits structured calls you map to your `Command` enum. Apache-ish Gemma license. |
| **`smartvest-llc/gemma-3-270m-it-genai-int4-android`** | 270M (INT4) | Pre-quantized INT4, packaged for Android on-device. Fastest path to "runs on the phone today". | Community repackage; verify provenance/license before shipping. |
| `google/gemma-3-270m-it` (base IT) | 270M | Official instruction-tuned base; pair with a tight custom system prompt to force JSON command output. | Needs quantization to `.task`/GGUF yourself; more prompt engineering. |
| `Qwen/Qwen2.5-0.5B-Instruct` (+ GGUF) | 500M | Strong tiny instruct model, good JSON adherence, permissive license. | llama.cpp/GGUF route rather than MediaPipe. Larger than Gemma 270M. |

**Recommendation:** Start with **`functiongemma-270m-it`** (or the INT4 Android repack of gemma-3-270m-it) because the "pretrained or set with custom system prompts" requirement is best met by a model already tuned to emit actions. Fall back to `gemma-3-270m-it` + strict system prompt if the function-calling variant's output format is awkward to map.

## 4. On-device runtime options (how it actually runs on Android)

| Runtime | Model format | Fit |
| --- | --- | --- |
| **MediaPipe LLM Inference API (Google AI Edge)** ⭐ | `.task` / `.litertlm` | Cleanest Android integration, official Gemma path, handles tokenizer + prompt. Gemma-3 270M is on the LiteRT Community page in MediaPipe-friendly format (no conversion). Recommended default. |
| **Google AI Edge Gallery** (reference app) | `.task` | Not a library — use as a working reference for how to load/run Gemma on-device. Good for prototyping/validating the model on your device before wiring code. |
| **llama.cpp (Android JNI)** | GGUF | Use if you pick Qwen2.5-0.5B or want maximum control/quantization options. More integration work. |

**Recommendation:** MediaPipe LLM Inference API + a Gemma-3 270M `.task` model.

## 5. Proposed architecture (minimal, swappable, safe)

```
Drawer search text (no leading '!')
        │
        ▼
 [NL enabled? + heuristic gate]  ── no ──▶ existing fuzzy app search (unchanged)
        │ yes (looks like an intent)
        ▼
 LlmCommandInterpreter (new)  ──▶ on-device model (MediaPipe)  ──▶ structured guess
        │                                                           {type, args, confidence}
        ▼
 Map guess ──▶ synthesize "!<command> <args>" string
        │
        ▼
 EXISTING CommandParser.parse(...)   ← single source of truth for validation
        │
        ▼
 CommandQueryClassifier-style PREVIEW row ("Set alarm 18:00 · tap to confirm")
        │ explicit tap / Enter
        ▼
 EXISTING CommandIntentFactory ──▶ prefilled system intent (human confirms in system UI)
```

Design rules that preserve your invariants:
- The LLM never produces an Intent. It only produces a candidate `!command` string, which is re-validated by the *existing* `CommandParser`. If the model hallucinates, the parser rejects it → falls back to app search or shows validation error. No new trust surface for execution.
- NL mode is **opt-in** (Settings toggle) and **gated** by a cheap heuristic (length/verbs) so the model isn't invoked on every keystroke — protects battery and latency.
- Inference runs off the UI thread; only fire on submit or debounce, never per-character.
- Model file is downloaded on first enable (not bundled in the APK) to keep the APK small; store under app files dir; verify checksum.

## 6. Step-by-step plan (bite-sized, TDD, when you move to build)

> Ordered so the risky/uncertain model work is validated *before* any launcher code changes.

### Phase A — Validate the model manually (no code changes)
- **A1.** Install Google AI Edge Gallery on your device; download Gemma-3 270M (or functiongemma-270m-it) `.task`. Confirm it loads and runs on your actual phone (RAM/latency sanity check).
- **A2.** Hand-write the system prompt that constrains output to your command vocabulary (JSON: `{command, args}` limited to the enum in `CommandParser`). Test ~20 utterances covering each command type in the Gallery/prompt playground. Record accuracy + failure modes. **Decision gate:** if 270M can't hit acceptable accuracy on your command set, drop to Qwen2.5-0.5B before writing code.

### Phase B — Pure interpreter layer (JVM-testable, no Android)
- **B1.** Add `LlmCommandInterpreter` interface + a `SystemPrompt` constant (the vocabulary-constrained prompt). File: `app/src/main/java/me/pompel/elauncher/LlmCommandInterpreter.java`.
- **B2.** Add `LlmOutputMapper` that converts raw model JSON → a `!command` string, then delegates to existing `CommandParser`. **Write failing tests first** (`LlmOutputMapperTest`) feeding canned model JSON strings (no real model) and asserting the resulting `ParseResult`. Reuse `command-cases.tsv` style fixtures.
- **B3.** Handle malformed / low-confidence / out-of-vocabulary output → return "no command, use app search". Test these explicitly.

### Phase C — On-device runtime binding
- **C1.** Add MediaPipe LLM Inference dependency to `app/build.gradle`. Implement `MediaPipeLlmInterpreter implements LlmCommandInterpreter` (Android-only, not unit-tested; covered by instrumented test).
- **C2.** Model lifecycle: first-run download to files dir + checksum verify + load/unload; run inference on a background executor. Instrumented test `LlmInterpreterInstrumentedTest` on one canned prompt.

### Phase D — Drawer integration + UX
- **D1.** Settings toggle "Natural-language commands (on-device)" + model-download state. Files: `SettingsActivity.java`, `ThemePreference`/prefs store.
- **D2.** In the drawer input path, add the heuristic gate and, when enabled and gated-in, route non-`!` submits through the interpreter → preview row (reuse `CommandQueryClassifier` preview/validation result types). Files: `MainActivity.java`, `CommandAdapter.java`.
- **D3.** Preview requires explicit confirm before `CommandIntentFactory` fires — assert via instrumented test that no intent is launched pre-confirmation (extends existing `MainActivityInputInstrumentedTest`).

### Phase E — Docs + guardrails
- **E1.** Update `README.md` "command-search baseline" with an NL section + the invariant that the LLM only *proposes* existing commands.
- **E2.** Add NL→command acceptance cases alongside `command-cases.tsv`.

## 7. Files likely to touch
- New: `LlmCommandInterpreter.java`, `LlmOutputMapper.java`, `MediaPipeLlmInterpreter.java`, `LlmOutputMapperTest.java`, `LlmInterpreterInstrumentedTest.java`.
- Modify: `app/build.gradle`, `MainActivity.java`, `CommandAdapter.java`, `SettingsActivity.java`, `README.md`, test resources.
- Unchanged (intentionally): `CommandParser.java`, `CommandIntentFactory.java` remain the deterministic core.

## 8. Risks / tradeoffs / open questions
- **Accuracy vs size:** 270M may miss ambiguous phrasing. Mitigation: constrained JSON prompt + parser re-validation + confidence threshold + always-available `!` explicit path.
- **Latency/battery:** even tiny models cost. Mitigation: opt-in, heuristic gate, submit-only inference, off-thread. Consider unload-after-idle.
- **APK size:** don't bundle the model — download on enable.
- **Licensing:** verify Gemma license terms for redistribution; vet the community INT4 repack (`smartvest-llc/...`) provenance before shipping. `functiongemma-270m-it` output schema needs mapping work — confirm its exact call format.
- **E-ink/low-RAM targets:** if targeting phones like Minimal Phone class hardware, confirm RAM headroom (AI Edge Gallery guidance suggests 6GB+ for larger Gemma; 270M is far lighter — validate in Phase A).
- **Open question:** should NL mode reuse the same submit affordance as `!` commands, or a distinct visual treatment so users know it's a guess? (UX decision for Phase D.)

## 9. Recommended first move
Do **Phase A only** first — prove `functiongemma-270m-it` (or gemma-3-270m-it INT4) runs on your device and hits acceptable accuracy on your 12-command vocabulary via the AI Edge Gallery + a constrained system prompt. That single validation de-risks the entire feature before any Java is written.

## 10. Unsloth trial workstream — fine-tune → export → run on device

Goal of the trial: produce **one** fine-tuned Gemma 3 270M that maps your NL phrasings → command JSON, exported to an on-device format, and confirm it loads in the AI Edge Gallery on your phone. This is the "include the LLM" trial you asked for. Entirely free-tier (Colab T4).

### 10.1 Why Unsloth fits here
- Official free **`Gemma3_(270M).ipynb`** Colab notebook exists (`unslothai/notebooks`), preconfigured with `unsloth/gemma-3-270m-it`. Unsloth also ships a dedicated **FunctionGemma** notebook — relevant since our task is function/command mapping.
- Unsloth = 1.6× faster, ~60% less VRAM; 270M trains in minutes on a free T4. QLoRA (4-bit) fine-tune + LoRA-adapter merge, then export.
- Handles the annoying parts: chat template, 4-bit base load, GGUF/merged-16bit export helpers.

### 10.2 The dataset (the real work of the trial)
The model is only as good as this. Produce ~1,500–3,000 examples, each a chat pair:
- **Input:** a natural utterance ("shoot mom a text I'll be late", "wake me at 6:30", "note buy milk").
- **Output:** strict JSON constrained to the CommandParser enum, e.g. `{"command":"text","contact":"mom","body":"I'll be late"}` / `{"command":"alarm","time":"06:30"}` / `{"command":"note","text":"buy milk"}`.
- Cover: all 12 command types, paraphrases/idioms, contact-name variety, absolute + relative times ("in 10 min", "tomorrow 9"), durations, and **negatives** ("what's the weather" → `{"command":"none"}`) so it learns to *decline* out-of-vocabulary input (critical — see §2d hallucination risk).
- Generation approach: template + slot permutation for breadth, then a larger LLM (e.g. via API) to paraphrase for natural variety. Hand-verify a sample. Keep a held-out eval split (~150 examples) it never trains on.
- Format as the Gemma chat template (system = the constrained instruction from §2e/Phase A, user = utterance, model = JSON). This is a plan step, not built yet.

### 10.3 Training recipe (Colab, free T4)
1. Open `Gemma3_(270M).ipynb` (or the FunctionGemma notebook) from `github/unslothai/notebooks`.
2. `FastModel.from_pretrained("unsloth/gemma-3-270m-it", load_in_4bit=True, max_seq_length=1024)` — 270M + short command strings means 512–1024 seq len is plenty.
3. Attach LoRA (`get_peft_model`, r=8–16, target attn+MLP proj). Load your JSONL dataset, apply Gemma chat template.
4. `SFTTrainer` (TRL) — 1–3 epochs, lr ~2e-4, small batch + grad-accum. 270M on a few thousand short examples ≈ 10–20 min.
5. Quick in-notebook inference sanity check on the held-out split; compute top-1 command accuracy. **Gate: ≥85%** (per §2d) before bothering to export/integrate.

### 10.4 Export → on-device format (the step that actually matters for "on the phone")
Two viable target formats; pick per runtime (§4):

**Path 1 — MediaPipe `.task` (recommended, matches §4 default):**
- Merge LoRA to 16-bit: `model.save_pretrained_merged(...)` → HF safetensors.
- Convert safetensors → `.task` using Google's official guide **"Convert Hugging Face Safetensors to MediaPipe Task"** (`ai.google.dev/gemma/docs/conversions/hf-to-mediapipe-task`) with the AI Edge `converter` (INT4/INT8 quant here → lands ~125–290 MB).
- Load the `.task` in AI Edge Gallery / MediaPipe LLM Inference API.

**Path 2 — `.litertlm` via AI Edge Torch (Google's fine-tuned-270M tutorial):**
- Google publishes an end-to-end **"Deploy a fine-tuned Gemma 270M model"** tutorial (`developers.google.com/edge/litert-lm/tutorials/convert-and-run`) — converts the HF checkpoint → `.litertlm` and runs via the LiteRT-LM Kotlin library on Android. This is the most 270M-specific, officially-documented path and is a strong default for the trial.

**Path 3 — GGUF (only if you switch to llama.cpp runtime):** Unsloth `save_pretrained_gguf(..., quantization_method="q4_k_m")`. Not needed for the MediaPipe route; listed for completeness.

> Known pitfall (from LiteRT-LM issue tracker): some `q4_block128 .task` variants emit **zero tokens silently on the GPU backend** (fine on CPU). During the trial, test on CPU backend first, and if using GPU, verify non-empty output before concluding the model is broken.

### 10.5 Trial acceptance criteria (what "the trial worked" means)
1. Fine-tuned 270M reaches **≥85% top-1 command accuracy** on the held-out split (§10.3).
2. Exported `.task`/`.litertlm` **loads and generates on your actual phone** via AI Edge Gallery (RAM/latency/battery sane on the €250 baseline — §2b).
3. On ~20 live spoken-style utterances typed on-device, it produces valid, parser-acceptable JSON and correctly **declines** out-of-vocabulary input.
- If all three pass → proceed to Phase B integration (§6) using this model file.
- If (1) fails → enlarge/clean dataset or try the FunctionGemma base; if (2) fails → check backend/quant pitfall above or step down quant.

### 10.6 Concrete resources (for when you build)
- Unsloth 270M notebook: `colab.research.google.com/github/unslothai/notebooks/blob/main/nb/Gemma3_(270M).ipynb`
- Unsloth notebooks index (incl. FunctionGemma): `github.com/unslothai/notebooks`
- Unsloth Gemma 3 blog/how-to: `unsloth.ai/blog/gemma3`, `unsloth.ai/docs/models/tutorials/gemma-3-how-to-run-and-fine-tune`
- Safetensors → MediaPipe `.task`: `ai.google.dev/gemma/docs/conversions/hf-to-mediapipe-task`
- Fine-tuned 270M → `.litertlm` on Android: `developers.google.com/edge/litert-lm/tutorials/convert-and-run`
- Base model: `huggingface.co/unsloth/gemma-3-270m-it` (+ `-GGUF` variant)

### 10.7 How this stays consistent with your constraints
- **Single model, no backups:** the trial fine-tunes exactly one 270M and ships one file. FunctionGemma vs gemma-3-270m-it is a *training-base* choice made once, not a runtime fallback chain.
- **App stays tiny:** the `.task`/`.litertlm` is **downloaded on first enable**, never bundled — trial or not, APK stays lean.
- **Plan-only now:** §10 is the recipe; no dataset generated, no training run, no repo change performed yet.

## 11. Natural-language dataset design (the core of the trial) + new `grocery` command

**Framing (your directive):** the whole point is to make the launcher *natural-language capable*. The dataset's **input side must be natural language**, NOT `!`-syntax. The model learns `NL utterance → structured command JSON`; only the *output* is canonical. Users type "set timer for 30s" / "wake me at 10pm" / "buy milk" — never `!timer 30s`.

### 11.1 Decision: add a 13th `grocery` command
"buy milk" had no target in the 12-command vocab (grocery list lived only Hermes-side). **Decision: add a `grocery` command to the launcher** (opencode implements the Java). This mirrors the existing `todo`/`note` repository pattern, so it's low-risk.

- New `CommandParser.Type` entry: `GROCERY("grocery", "!grocery <item>")` (canonical token TBD by opencode — `grocery` or `g`).
- New `GroceryRepository` + `KeyValueStore` persistence, mirroring `TodosRepository`/`NotesRepository`.
- Add a no-arg list form too (`groceries` → open list), mirroring `todos`/`notes`, so "what's on my grocery list" resolves.
- This is now part of the opencode Java integration spec (Phase B/§7), and the dataset labels against **13 commands** (+ their list forms).
- ⚠️ Keep distinct from `todo`: "buy milk" → grocery; "remind me to call the plumber" → todo. Seed contrastive pairs so the model learns grocery-item vs task.

### 11.2 NL variation is the dataset's whole job
For each command, generate **many** paraphrase templates × realistic slot values. Target breadth over raw count (~150–400 utterances/command, ~2.5–4k total). Variation axes:
- **Verb synonyms:** timer→set/start/ping/count down; alarm→wake/get me up/set alarm; call→call/ring/dial/phone; text→text/message/shoot a text/tell; grocery→buy/get/add/need/pick up; todo→remind/add task/don't let me forget.
- **Register/noise:** casual, terse, typo'd, filler words ("umm set a timer real quick for like 30 secs").
- **Slot format variety:** times ("10pm", "22:00", "quarter past 7", "tomorrow 9"), durations ("30s", "half a min", "an hour and a half"), contacts (first name, "mom", full name, "the office"), free-text bodies/items.
- **Word-order & implicit intent:** "30 second timer" / "in 30 seconds remind me" both → timer.

### 11.3 Illustrative NL → JSON pairs (label schema)
| NL input | Output JSON |
|---|---|
| set timer for 30s | `{"command":"timer","duration":"30s"}` |
| ping me in half a min | `{"command":"timer","duration":"30s"}` |
| set an alarm for 10pm | `{"command":"alarm","time":"22:00"}` |
| wake me at quarter past 7 | `{"command":"alarm","time":"07:15"}` |
| buy milk | `{"command":"grocery","item":"milk"}` |
| add eggs and bread to my list | `{"command":"grocery","item":"eggs, bread"}` |
| remind me to call the plumber | `{"command":"todo","text":"call the plumber"}` |
| text mom I'll be late | `{"command":"text","contact":"mom","body":"I'll be late"}` |
| ring the office | `{"command":"call","contact":"the office"}` |
| jot down wifi pass is hunter2 | `{"command":"note","text":"wifi pass is hunter2"}` |
| what's on my grocery list | `{"command":"groceries"}` |
| show my todos | `{"command":"todos"}` |
| turn on the flashlight | `{"command":"torch"}` |
| take a photo | `{"command":"camera"}` |
| event dentist friday 3pm | `{"command":"event","date":"friday","time":"15:00","title":"dentist"}` |
| tell the family group I'm omw (Telegram) | `{"command":"hermes","body":"I'm omw"}` |
| what's the weather tomorrow | `{"command":"none"}` |

### 11.4 Must-have dataset properties
- **Negatives / declines:** open questions, chit-chat, app-launch phrasing ("open spotify") → `{"command":"none"}` so the launcher falls back to app search instead of hallucinating.
- **Contrastive clusters:** grocery vs todo; text (SMS) vs hermes (Telegram); note vs todo vs grocery; singular-add vs list-open (todo/todos, note/notes, grocery/groceries).
- **Slot normalization targets:** the *output* time/duration should be normalized (10pm→22:00) so downstream `!command` synthesis is clean; alternatively keep raw and normalize in `LlmOutputMapper` — decide in build, but be consistent in labels.
- **Held-out eval split (~150–200)** with fresh phrasings/slot values the model never trains on; eval computes **top-1 command accuracy + slot accuracy** against the ≥85% gate.
- **Generator approach:** deterministic template+slot permutation for coverage → LLM paraphrasing pass for natural variety → dedup → hand-verify a sample. Output JSONL in Gemma chat-template form (system = constrained instruction, user = NL, model = JSON).

## 12. Repo setup plan — local branch + move training into `ml/`  (PLAN ONLY, not executed)

**Directive:** use the `findEthics` token from Bitwarden; check out `physical-keyboard-LLM` locally from `physical-keyboard`; add an `ml/` folder in the repo and move the prepared training setup there.

### 12.1 Auth (isolated, read-only token retrieval)
- The repo is **not cloned on this host yet** — so "checkout locally" = clone first, then branch.
- Retrieve the `findEthics` PAT from Bitwarden Secrets Manager (read-only):
  `~/.local/bin/bws secret list` → identify the findEthics GitHub PAT item → capture its value into a shell var only (never written to disk/config).
- **Isolation rule (per memory):** the user's work GitHub was deliberately removed from this device; Hermes uses isolated creds. Do NOT `gh auth login` the findEthics account globally. Use the token inline in a scoped remote URL for this one repo only:
  `https://<TOKEN>@github.com/findEthics/cLauncher.git`. Scrub the token from any command echo/history.

### 12.2 Clone + branch
```
git clone https://<TOKEN>@github.com/findEthics/cLauncher.git ~/projects/cLauncher
cd ~/projects/cLauncher
git checkout physical-keyboard
git checkout -b physical-keyboard-LLM
# reset origin URL to tokenless to avoid persisting the PAT in .git/config:
git remote set-url origin https://github.com/findEthics/cLauncher.git
```

### 12.3 Layout decision (confirmed): `ml/` folder INSIDE the repo
Move the prepared setup from `~/projects/clauncher-llm/` into `~/projects/cLauncher/ml/`:
```
cLauncher/ml/
├── README.txt
├── INSTRUCTIONS.md
├── INTEGRATION_SPEC.md
├── dataset/{generate_dataset.py, train.jsonl, eval.jsonl}
├── training/train_unsloth.py
├── eval/evaluate.py
├── notes/PLAN.md
└── export/            # gitignored — never committed
```

### 12.4 Guardrails before committing (critical)
- Add/extend `.gitignore` so model weights + envs never land in app history:
  ```
  ml/export/
  ml/**/__pycache__/
  ml/**/*.task
  ml/**/*.gguf
  ml/**/merged/
  ml/**/lora/
  ml/**/.venv/
  *.pyc
  ```
- Verify `git status` shows only scripts + the two JSONL files + the `.md`/`.txt` docs (no weights, no venv, no caches).
- Sanity-check dataset size committed: `train.jsonl` (~1.1k lines) + `eval.jsonl` (~76) are small text — fine to version.

### 12.5 Commit + push
```
git add ml .gitignore
git commit -m "Add ml/ training setup for on-device NL command model (dataset, Unsloth trainer, eval gate, opencode specs)"
git push -u origin physical-keyboard-LLM
```
- Then verify the branch + `ml/` tree exist on GitHub (via API with the token).
- **Boundary preserved:** this only publishes the *training scaffold* to a new branch. No training run, no app/Java changes, no PR/merge — those are opencode's job on the M4 (per INSTRUCTIONS.md).

### 12.6 What stays NOT done in this step
- No fine-tune (no GPU on this host).
- No Java / `grocery` command / interpreter code (opencode, per INTEGRATION_SPEC.md).
- No PR, no merge into `physical-keyboard`.
