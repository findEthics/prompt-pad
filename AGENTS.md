# Prompt-Pad

Minimal dark launcher for the Unihertz Titan 2 Elite (4:3, 4.04", top-left camera cutout).
Package `com.hermes.promptpad`. Kotlin + Compose, single activity, zero AI. Weather is the only network feature.

## Build
- `source ~/toolchain/env.sh` first.
- Debug: `./gradlew :app:assembleDebug`; unit tests: `./gradlew :app:testDebugUnitTest`.
- Release: export KEYSTORE_FILE / KEYSTORE_PASSWORD / KEY_ALIAS / KEY_PASSWORD from Bitwarden keys
  `PROMPTPAD_RELEASE_*` (keystore also stored base64 as `PROMPTPAD_RELEASE_KEYSTORE_B64`), then
  `./gradlew :app:assembleRelease`. R8-minified, signed, non-debuggable.
- Test AVD `promptpad43` = 1080x1440 (4:3), android-35 google_apis x86_64, hw keyboard on.

## Layout contract
Two-line lowercase date -> weather/battery caption -> two 57dp bordered glance rows -> four rounded bottom shortcuts. Katapult-derived Lato typography and black/white/orange (`#FC7703`) palette. Date, clock, weather and the four shortcuts are app-picker configurable and gain orange borders in edit mode. Clock defaults to the system alarm view; weather opens the picker until configured. Peak respects safe top insets when the status bar is visible; hidden/right-aligned Peak retains the 8dp corner gap. Notifier, Notes, Agenda and To Do keep content inset-safe with 26sp centered top-edge titles. An open note uses its editable title as that heading, without a second title row.

## Icon contract
Use the bundled Katapult monochrome icon set for mapped apps and system shortcuts. Note and To Do intentionally retain their PromptPad Material icons. Managed-profile mapped apps use the same bundled artwork with Android's work badge. Drawer icons use one fixed outer size and remain hidden until search text is entered.

## Gestures
Swipe up = profile-aware drawer (personal + managed work apps, badged icons, auto-focused bottom search and Settings button). Swipe right = Notifier when enabled (default on); left on its blank area returns Home, while swiping a notification dismisses it. Notifier's Settings toggle also gates drawer and pinned-shortcut entry points. Swipe left on Home = Settings. Long-press blank Home area = shortcut edit mode. Double-tap blank Home area = sleep when enabled. Hidden status bar is transiently revealed by a top-edge swipe. Physical key long-press on Home = mapped app launch. First install shows these instructions once. Back from To Do, including a focused input with the software keyboard, goes directly Home.

## Removed surfaces
Activity tracking and Focus/Monk restriction modes are intentionally purged, including their permissions, preferences, settings, service, resources, and tests.

## Deliberate simplifications (ponytail)
- Settings only expose behavior wired into the app; delete unused flags and callbacks instead of preserving placeholders.
- Use immutable list updates for row changes; persist mutable note field edits directly.
- Weather uses Open-Meteo only for manual city lookup and MET Norway for forecasts. It fetches only while Home is visible, enabled and cache-expired; no GPS, worker, account or backend.
- Notifier preserves internal `HubListener`, `Screen.Hub` and persisted `promptpad:hub` identifiers. It excludes group summaries and opens activity PendingIntents with Android 14+ sender background-launch opt-in; missing, cancelled and non-activity intents fall back to the app. RemoteInput replies retain original and echoed message text in the live listener state; dismissal removes both and cancels the source notification. No notification archive or cross-process reply persistence was added.
- New blank notes focus the title; existing notes open with the body focused and cursor at position zero. The `<>`, `B` and `H` toolbar commands share one cursor-aware insert (`insertAtCursor`): `<>` drops a backtick pair with the cursor between them, `B` drops `****` with the cursor between the two pairs, and `H` inserts a newline+`# ` with the cursor after the hash. No literal `b` placeholder is inserted. View/edit transitions preserve a source-line scroll anchor across markdown rendering; Back closes the note before leaving Notes. Notes and tasks use explicit up/down reorder controls, persisted through the existing store; Notes keeps reorder at the right of New Note, while To Do keeps it at the left of the lowercase `clear` action. Orange scrollbar thumbs appear for overflowing lists and note bodies. No drag-and-drop dependency.
- Notes/To-Do persist as JSON in SharedPreferences; completed To-Dos stay struck through until cleared. Swap for Room only if lists get large.
- Home agenda includes only remaining/ongoing events for the current local date, including all-day events encoded at UTC midnight. The full Agenda screen retains its seven-day query. Local midnight uses `java.time`, not a fixed 24-hour local-day interval.

## Verified release: notifier / notes polish (2026-09-10)
- Branch `feat/notifier-notes-polish`, created from fetched `origin/main` at `e2e8b1b`.
- Principal ponytail audit completed: native APIs/existing Compose only, no added dependencies or schema migration. Removed obsolete note-field styling; fixed notification trampolines, all-day UTC/local-date boundaries and software-keyboard obstruction of task reorder controls during validation. Existing signing identity retained.
- `:app:testDebugUnitTest`: 21 tests, zero failures/errors/skips. Covers reorder bounds, source-line scroll mapping, initial cursor, 90% selection, local midnight/DST and all-day filtering. `:app:lintRelease`: zero errors; existing warnings remain. `git diff --check` clean.
- API 35 headless `promptpad43` instrumentation passes with software IME enabled: actual notification intent and both fallback launches, inline reply retention, row swipe/system removal, note focus/title/scroll/reorder, To Do Back/reorder, Notifier toggle/gestures, 90% preference, status inset comparison and weather app selection/opening. Status-visible and edit-mode screenshots checked for clearance/orange borders.
- Runner: build `:app:assembleDebug :app:assembleDebugAndroidTest`; install both debug APKs; grant the test package `POST_NOTIFICATIONS`; enable `com.hermes.promptpad/.HubListener` using `cmd notification allow_listener`; run `adb shell am instrument -w com.hermes.promptpad.test/com.hermes.promptpad.RegressionRunner`. Require explicit `PASS`, not merely adb exit zero. Fixtures overwrite emulator app notes/tasks: use a disposable AVD, never a user's device.
- Signed/minified release separately installed and smoke-tested through onboarding, empty-note focus, saved-note cursor/typing, To Do Back with IME, and Notifier round trip; no AndroidRuntime crashes. `apksigner verify` passes and `apkanalyzer manifest debuggable` is false. Artifact: `app/build/outputs/apk/release/prompt-pad-release.apk`. WhatsApp itself was not installed; notification behavior was exercised with a real Android notification fixture, not a mocked listener.

## Verified release: notes / to-do controls (2026-09-11)
- Branch `feat/notes-todo-controls`, created from fetched `origin/main` at `bb539c1`. Full ponytail audit retained native Compose only and narrowed the heading inset to the status-bar edge; no dependency, schema or abstraction was added.
- `:app:testDebugUnitTest`: 22 tests, zero failures/errors/skips. `:app:lintDebug`, debug/test APK builds and `git diff --check` pass.
- API 35 `promptpad43` instrumentation passes with software IME: status-visible Notes/To Do headings, both requested reorder-row placements, new-note title focus, backtick insertion, note/task reorder and the existing regression suite.
- Signed/minified release installs and cold-launches without an AndroidRuntime crash. APK Signature Scheme v2 verification passes and the manifest is non-debuggable. Artifact: `app/build/outputs/apk/release/prompt-pad-release.apk`.

## Verified release: ui polish refactor (2026-09-17)
- Branch `feat/ui-polish-refactor`, created from `main`. Full ponytail audit: native Compose only, no dependency/schema/abstraction added. The three toolbar inserts were unified onto one `insertAtCursor` and the now-dead `append` helper deleted (deletion over addition).
- Changes: To Do action relabelled `Clear`→`clear`; Home date shortened `EEEE, MMMM d`→`EEE, MMM d` across all three Peak variants; Notifier notification body capped at `maxLines = 5` with ellipsis; Notes `B`/`H` toolbar commands now match `<>` cursor-aware insertion (`****` cursor-between, `\n# ` cursor-after-hash, no literal `b`).
- `:app:testDebugUnitTest`: 24 tests, zero failures (added bold/heading cursor cases). `:app:lintRelease` zero errors. Signed release build succeeds; `apksigner verify` passes (v2), manifest non-debuggable. Artifact: `app/build/outputs/apk/release/prompt-pad-release.apk`.
