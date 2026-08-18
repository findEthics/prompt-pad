# Plan: On-Demand Hugging Face Model Download (latest-on-first-download)

Status: IMPLEMENTED — model publication and device validation pending.
Branch: physical-keyboard-LLM

## Goal
Replace the manual adb-push of the 272 MB `prompt-pad-gemma3-270m.task` model with an
on-demand, resumable, SHA-256-verified HTTPS download from a public Hugging Face repo,
triggered from the Settings toggle. Hosting/upload becomes a confirmation-gated phase in
the existing ML training pipeline.

## Confirmed decisions
- HF repo is **public** → tokenless download (embedding a token in the APK would leak it).
- Upload handled by a small **confirmation-gated** script that runs as a final training phase.
- Versioning: app auto-picks the **latest** model at first-download time via a hosted manifest;
  a per-release SHA-256 is NOT baked into the APK.
- Update behavior: **latest on first download only, no auto-upgrade** once installed.
- Repo: `findethics-labs/prompt-pad-gemma3-270m` (created by the publish script if absent).
- Default upload artifact: `ml/export/prompt-pad-gemma3-270m.task` (`--task` overrides).
- Download UX: toggle-on starts the download, progress shown in the preference summary.
- No new libraries (PLAN.md hard constraint): stdlib `HttpsURLConnection`, `MessageDigest`, and
  a small Java standard-library manifest parser. No OkHttp / WorkManager / notification.

## Key tradeoff (acknowledged)
Auto-latest is incompatible with a single hardcoded SHA-256 in the APK. Integrity moves to a
hosted `model-manifest.json`. Corrupt/partial downloads are still caught by the manifest hash;
authenticity now rests on TLS-to-huggingface.co + HF repo-write security rather than an
APK-baked hash. Unavoidable for any self-updating download. To be marked with a `ponytail:` note.

---

## Part A — Confirmation-gated publish phase (dev-side)

### New file: `ml/publish_model.py`
Uses already-installed `huggingface_hub 1.27.0`.
- Args: `--task ml/export/prompt-pad-gemma3-270m.task` (default), `--repo findethics-labs/prompt-pad-gemma3-270m`, `--public`, `--yes` (CI bypass of prompts).
- Loads `HF_TOKEN` from `.env` at repo root; clear error if missing.
- Computes SHA-256; prints summary (file, size, hash, repo, visibility=public); requires a typed
  confirmation → the "after user confirmations" gate.
- Creates repo (public) if absent; uploads the `.task`; captures the resulting commit SHA
  (immutable revision).
- Writes/updates `model-manifest.json` on `main`:
  ```json
  { "version": "<commit-sha>",
    "url": "https://huggingface.co/findethics-labs/prompt-pad-gemma3-270m/resolve/<commit-sha>/prompt-pad-gemma3-270m.task",
    "sha256": "<hash>" }
  ```
- Prints the manifest URL. No Java edits (ml/Python stays decoupled from app/Java).

### Docs
- New "Publish model (confirmation-gated)" phase in `ml/INSTRUCTIONS.md` and the CLAUDE.md
  model workflow, placed after export/smoke-test.

### `.gitignore`
- Add `.env` and `.env.*` (currently unignored — token-leak risk).

---

## Part B — On-device downloader

### New file: `app/src/main/java/me/pompel/elauncher/ModelDownloader.java`
Plain Java and Android APIs only (no new deps).
- One hardcoded constant:
  `MANIFEST_URL = https://huggingface.co/findethics-labs/prompt-pad-gemma3-270m/resolve/main/model-manifest.json`
- Fetch + parse manifest → immutable `url` + expected `sha256`.
- Stream `url` to `prompt-pad-gemma3-270m.task.part`; resume via `Range: bytes=<partLen>-`
  (survives app restarts).
- Manual redirect loop that **re-attaches the `Range` header** across the HF→CDN 302
  (HttpsURLConnection silently drops custom headers on redirect). `ponytail:` note naming this.
- Streaming `MessageDigest` SHA-256 vs manifest hash → mismatch deletes `.part` + fails;
  match → `File.renameTo(finalFile)` (atomic same-filesystem), delete `.part`.
- Runs on a background thread (existing `ExecutorService` pattern); exposes progress %,
  success, error/cancel callbacks.
- No version marker, no re-check once installed, no background/auto-upgrade.

Destination is exactly `MediaPipeLlmInterpreter.modelFile()` — the load path is unchanged.

### `SettingsActivity.java` (replaces force-disable block at ~lines 120-129)
- Toggle-on with model absent → run `ModelDownloader`; show `NN% downloading…` in the
  preference summary; keep pref stored OFF until SHA-verified-present.
- Success → enable + persist ON. Failure/cancel → revert toggle OFF + toast reason; keep
  `.part` for later resume.
- Model already present → current enable behavior unchanged.

### `app/src/main/AndroidManifest.xml`
- Add `<uses-permission android:name="android.permission.INTERNET" />`.

### Tests: `app/src/test/.../ModelDownloaderTest.java`
- Resume-offset from an existing `.part` length.
- SHA-256 verify pass/fail.
- Manifest JSON parse (pure Java, JVM-testable).
Extract the pure pieces as static helpers. Skip a full network mock (YAGNI).

---

## Deliberately skipped (add only if later needed)
- Background auto-upgrade of installed models.
- Version-compare logic + SharedPreferences version tracking.
- WorkManager / OkHttp / status-bar notification.
- Embedded HF token / private repo.

## Files touched
| File | Change |
|------|--------|
| `ml/publish_model.py` | new — confirmation-gated upload + manifest writer |
| `ml/INSTRUCTIONS.md`, `CLAUDE.md` | publish phase docs |
| `.gitignore` | ignore `.env`, `.env.*` |
| `app/.../ModelDownloader.java` | new — manifest-driven resumable verified download |
| `app/.../SettingsActivity.java` | toggle-on triggers download + progress |
| `app/src/main/AndroidManifest.xml` | INTERNET permission |
| `app/.../ModelDownloaderTest.java` | new — offset + SHA-256 + manifest-parse tests |

## Pre-implementation checks
1. HF repo name is locked to `findethics-labs/prompt-pad-gemma3-270m`; the publish script creates
   the public model repo if it does not exist.
2. HF `resolve/` URLs support HTTP Range through the CDN redirect. Verified with a public
   LFS-backed file returning `HTTP 206`, `accept-ranges: bytes`, and `content-range`.

## Reference context (as of planning)
- Model load path: `MediaPipeLlmInterpreter.modelFile()` = `getExternalFilesDir(null)/prompt-pad-gemma3-270m.task`; `isModelPresent()` gates the feature.
- Current toggle disable logic: `SettingsActivity.java:120-129`.
- No INTERNET permission yet; spec (`ml/INTEGRATION_SPEC.md:66-69`) says add it only when the downloader lands.
- Build: minSdk 23, targetSdk/compileSdk 36; deps limited to recyclerview, appcompat, preference, mediapipe tasks-genai 0.10.28.
- Model size: 272 MB.
