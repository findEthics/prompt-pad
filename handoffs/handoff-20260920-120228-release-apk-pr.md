## Context
- Fixed Prompt-Pad’s missing media controls and broken Settings left-swipe app picker.
- Built and validated a signed release after the original v1.2/code-3 APK was rejected as invalid on the target device.

## Current state
- Branch `fix/media-controls-left-swipe-picker` is pushed to `findEthics/prompt-pad`.
- Commits: `de6ac66` (feature fixes) and `1084711` (release signing/version 1.3 code 4).
- Signed release APK: `app/build/outputs/apk/release/prompt-pad-release.apk`.
- Current APK is v1.3/code 4; SHA-256: `e2b9bceac1f9213ade00b21f270e2a1eb1d64efa38b9b7ec884bb9592c2ebd96`.
- Unit tests pass: 25 tests, zero failures. Release clean-installed with `adb` on API 36 emulator.
- `AGENTS.md` updated; `.env` and `prompt-pad-release.jks` are ignored and were not committed.

## Pending tasks
- Create PR manually from `findEthics:fix/media-controls-left-swipe-picker` to `hermes-ss:main`: https://github.com/findEthics/prompt-pad/compare/main...fix/media-controls-left-swipe-picker?expand=1
- Confirm v1.3/code-4 APK installs on the physical target device and validate live media controls with a real MediaSession.
- Merge PR after review.

## Key files / paths
- `app/src/main/java/com/hermes/promptpad/MediaWidget.kt` — refreshes active sessions whenever Notifier opens; watches idle sessions so later playback transitions surface controls.
- `app/src/main/java/com/hermes/promptpad/Settings.kt` — Settings-scoped reuse of `AppPicker` for left-swipe app.
- `app/build.gradle.kts` — v1.3/code 4 and release signing settings.
- `app/build/outputs/apk/release/prompt-pad-release.apk` — release artifact.
