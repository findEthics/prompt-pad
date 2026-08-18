# CLauncher — Integration Spec (branch `physical-keyboard-LLM`)

For opencode. Implement on the M4 against the real repo. Two workstreams:
**(A) new `grocery` command**, **(B) the on-device NL interpreter layer**. Preserve the existing
architecture and the "never auto-execute" invariant.

Existing relevant files (package `me.pompel.elauncher`):
`CommandParser.java`, `CommandQueryClassifier.java`, `CommandIntentFactory.java`,
`CommandAdapter.java`, `NotesRepository.java`, `TodosRepository.java`, `KeyValueStore.java`,
`SharedPreferencesKeyValueStore.java`, `MainActivity.java`, plus tests and
`app/src/test/resources/me/pompel/elauncher/command-cases.tsv`.

---

## A. New `grocery` / `groceries` command

Mirror the existing `todo`/`todos` pair exactly — it is the closest analog (content add + list open).

1. **`CommandParser.Type`** — add two enum entries:
   ```java
   GROCERY("grocery", "!grocery <item>"),
   GROCERIES("groceries", "!groceries"),
   ```
2. **Parser method** — add `parseGrocery` alongside `parseTodo`: non-empty argument required,
   `!groceries` takes no argument (copy the `todos` handling). Emit a `GROCERY` command carrying
   the item text, and `GROCERIES` as a no-arg list-open command.
3. **`GroceryRepository.java`** — copy `TodosRepository.java`; back it with the same
   `KeyValueStore` pattern (new key, e.g. `grocery_items`). Same add/list/persist API.
4. **`CommandIntentFactory` / handler** — `groceries` opens the list UI (mirror `TodosActivity`
   route); `grocery` appends an item then shows confirmation. NEVER send anywhere — local only.
   (Optional, confirm with user before doing: also append to `~/life-book/personal/groceries.md`
   is a Hermes-side concern, NOT the app's — do not add file/network writes to the launcher.)
5. **`GroceryActivity` + `GroceriesRepository` tests** — copy `TodosActivity` and
   `TodosRepositoryTest`. Add rows to `command-cases.tsv` for grocery preview/submit/empty-arg,
   matching the existing todo rows' columns.
6. Update help text / command list UI to include the new command.

**Acceptance:** `!grocery milk` adds "milk"; `!groceries` opens the list; all existing tests plus
new grocery tests green.

---

## B. On-device NL interpreter layer

The LLM sits IN FRONT of `CommandParser`. Flow:

```
free text (no leading !)
   -> LlmCommandInterpreter.interpret(text)        # MediaPipe LLM, returns JSON string
   -> LlmOutputMapper.toCommandString(json)        # JSON -> canonical "!..." string
   -> existing CommandParser.parse("!...")         # SAME validation as typed commands
   -> existing CommandQueryClassifier preview       # SAME preview UI
   -> user confirms -> existing CommandIntentFactory # SAME prefilled intent, user submits
```

### B1. When to invoke the model (keep it cheap + safe)
- Only when the drawer input is **non-empty, does NOT start with `!`, and does not match an app
  name** (i.e. after the deterministic app-search + command paths find nothing obvious).
- Behind a settings toggle "Natural language commands (beta)", default OFF until model downloaded.
- Debounce; run inference off the UI thread; show a lightweight "thinking" state.

### B2. `LlmCommandInterpreter` (new)
- Wraps MediaPipe `LlmInference` (GenAI / LiteRT). Loads the `.task` from the app files dir.
- Current development delivery is an adb-pushed file at
  `getExternalFilesDir(null)/clauncher-gemma3-270m.task`; the feature stays disabled when it is
  absent. The production delivery task is deliberately deferred: host one versioned `.task` at a
  stable HTTPS URL, download it to a `.part` file with `HttpURLConnection` and HTTP Range resume,
  verify a pinned SHA-256 before an atomic rename, then load only the verified file. Add
  `android.permission.INTERNET` only when that downloader lands. Do not forget this deferred task.
- **System prompt MUST be byte-identical to the training SYSTEM_PROMPT** in
  `dataset/generate_dataset.py`. Copy it verbatim (see below). If they drift, accuracy drops.
- Generation: greedy/deterministic, temperature 0, with an output budget of about 64 tokens. The
  MediaPipe `maxTokens` option includes the prompt, so the current implementation uses a total
  budget of 256. Prefer CPU backend first
  (GPU q4 `.task` can silently emit 0 tokens — known LiteRT-LM issue).

### B3. `LlmOutputMapper` (new, pure Java, unit-tested — no Android deps)
- Parse the JSON object. Map `command` + slots to a canonical `!...` string:
  | command    | JSON slots            | canonical string                  |
  |------------|-----------------------|-----------------------------------|
  | call       | contact               | `!call <contact>`                 |
  | text       | contact, body         | `!text <contact> <body>`          |
  | hermes     | body                  | `!hermes <body>`                  |
  | timer      | duration              | `!timer <duration>`               |
  | alarm      | time (HH:MM)          | `!alarm <time>`                   |
  | todo       | text                  | `!todo <text>`                    |
  | todos      | —                     | `!todos`                          |
  | note       | text                  | `!note <text>`                    |
  | notes      | —                     | `!notes`                          |
  | grocery    | item                  | `!grocery <item>`                 |
  | groceries  | —                     | `!groceries`                      |
  | event      | date, time, title     | `!event <date> <time> <title>`    |
  | torch      | —                     | `!t`   ⚠️ (not `!torch`)          |
  | camera     | —                     | `!camera`                         |
  | none       | —                     | (no command → fall through to app search / no-op) |
- **Robustness:** tolerate extra prose around the JSON (extract first `{...}`), missing/extra keys,
  single quotes. On any parse failure OR `command:"none"` OR unknown command → return null and let
  the launcher fall back to normal app search. A wrong/absent command must degrade to "no action",
  never to a wrong action.
- After mapping, the result STILL goes through `CommandParser.parse()` — so malformed slots are
  caught by existing validation and shown as the existing error/preview states.

### B4. The system prompt (copy verbatim from the dataset generator)
```
You are the command interpreter for a minimal Android launcher. Convert the user's natural-language request into a single JSON object describing one launcher command. Respond with ONLY the JSON object, no prose. Valid commands: call, text, hermes, timer, alarm, todo, todos, note, notes, grocery, groceries, event, torch, camera. If the request does not match any command, respond {"command":"none"}. Normalize times to 24-hour HH:MM and durations to a compact form like 30s, 5m, 1h30m.
```

### B5. Tests
- `LlmOutputMapperTest` (pure JUnit): for each command, a sample JSON → expected `!...` string;
  plus malformed JSON, `none`, unknown command, extra-prose-wrapped JSON, the `torch`→`!t` case.
- Instrumented smoke test (optional, needs the model): feed 8–10 natural phrases, assert the
  produced canonical string parses to the expected `Type`. Keep it small; gate real accuracy in
  the Python eval, not on-device.
- All existing `CommandParserTest`, `CommandQueryClassifierTest`, `CommandContractFixtureTest`
  must stay green.

---

## Invariants (do not break)
1. Natural-language predictions auto-submit through the existing `CommandParser` and command execution
   flow. External apps are only opened with prefilled data; the launcher never auto-calls, sends, or
   inserts anything.
2. The model never bypasses `CommandParser` validation.
3. Base APK stays small: model is downloaded on demand, not bundled.
4. Feature is opt-in (settings toggle), OFF until the model is present.
5. `none`/parse-failure/unknown → silent fallback to app search, never a wrong action.

## Definition of done
- `grocery`/`groceries` implemented + tested.
- NL layer implemented + `LlmOutputMapperTest` green.
- `.task` model loads on device (CPU backend verified) and produces correct commands for a manual
  smoke set.
- Branch `physical-keyboard-LLM` cut from `physical-keyboard`; PR opened (not merged).
