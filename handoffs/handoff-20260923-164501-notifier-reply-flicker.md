## Context
- Prompt-Pad Notifier previously retained stale replies after WhatsApp reposts; the latest-exchange fix now shows only the newest incoming message and local reply. A new report concerns flickering on the second reply to the same notification.

## Current state
- The latest-exchange changes are uncommitted on `fix/notifier-reply-sender` (HEAD `e7f1b4b`); unrelated `.DS_Store` files are untracked. No work on the new flicker report has begun.
- 32 unit tests and debug lint passed; a three-round Android 16 synthetic notification/UI scenario passed. The broader instrumentation run timed out at the unrelated `90% saved` check; repeated runs on a reused AVD sometimes time out setting up the fixture. Real WhatsApp has not been revalidated with this build.
- Signed, R8-minified, non-debuggable release v1.16/code 17 was verified and cold-launched. APK: `app/build/outputs/apk/release/prompt-pad-release.apk`; SHA-256 `f9990149c39a79678103a7300fc9605081e63f40444ea690446da0d8d7ea5117`.

## Pending tasks
- a flickering happens when a reply is sent the second time on the same notification in notifier; investigate and fix
- Validate the latest-exchange behavior and any flicker fix on the target WhatsApp/Android 16 device; repeat relevant tests and produce a new signed release if code changes.
- Resolve or document the unrelated `90% saved` full-suite timeout and reused-AVD fixture setup flakiness.
- Commit/push the current uncommitted fix and open an upstream PR if desired. Upstream push was denied; the active Enterprise Managed User cannot create a PR, though the earlier branch was pushed to the `findEthics` fork.

## Key files / paths
- `app/src/main/java/com/hermes/promptpad/HubListener.kt`, `Hub.kt`; `app/src/test/java/com/hermes/promptpad/LogicTest.kt`.
- `app/src/androidTest/java/com/hermes/promptpad/NotificationTarget.java`, `RegressionRunner.kt`; `app/build.gradle.kts`; `AGENTS.md`.
- Existing upstream PR compare URL: `https://github.com/hermes-ss/prompt-pad/compare/main...findEthics:fix/notifier-reply-sender?expand=1`.
