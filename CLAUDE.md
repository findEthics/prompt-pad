# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

Promptpad is a lightweight Android launcher, forked from thypon/eLauncher, being extended with an explicit `!`-prefixed command-search mode (calling, texting, timers, notes, todos, calendar events, torch, camera) for a Unihertz Titan 2 physical-keyboard device. Package: `me.pompel.elauncher`. Single Gradle module (`app`), plain Java (no Kotlin), View-based UI (no Compose).

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

**Persistence**: `LocalListRepository` wraps `KeyValueStore` (`SharedPreferencesKeyValueStore` + `PersistentValueCodec`) for notes, to-dos, and groceries — all local-only, no network/sync by design (V1 scope). `LocalListItem` is the shared immutable record. `LocalListActivity` owns the shared screen shell; the three named list Activities select their list kind for Android component and Recents compatibility.

**Testing pattern**: `CommandContractFixtureTest` is data-driven off `app/src/test/resources/me/pompel/elauncher/command-cases.tsv` — this TSV is the source of truth for routing/parsing contract cases and should be updated alongside any change to command syntax or validation behavior, in lockstep with the README command-contract table and `PLAN.md`. `TimeSource` is an injected time abstraction used to keep timer/note/todo timestamp logic testable. `InMemoryKeyValueStore` is the test double for persistence tests.

## Constraints (from PLAN.md — do not violate without explicit user direction)

- No new command types, aliases, macros, or arbitrary-intent execution beyond the fixed V1 command list.
- No network calls, analytics, telemetry, or cloud sync.
- HOME-scoped printable-key capture is allowed while Promptpad owns HOME; no system-wide key interception or accessibility-service capture.
- Keep release APK size minimal — avoid adding new libraries; this is a hard project constraint, not a style preference.
- eLauncher upstream is GPL-3.0 — this fork must stay GPL-compatible if ever distributed beyond personal use.
