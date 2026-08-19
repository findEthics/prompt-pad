# Promptpad

Promptpad is a lightweight, minimal Android launcher with a sparse homescreen, fast app access, and a text-first command-search baseline.

The launcher is based on NoLauncher and inspired by [OLauncher Light](https://github.com/tanujnotes/Ultra/) and OLauncher.

## Features

- Sparse homescreen and app drawer
- Fuzzy app search
- Bottom search bar in app drawer
- Swipe up or type on the physical keyboard from the homescreen to open the app drawer
- Long press a homescreen app field to assign or rename an app
- Automatically launch the single matching app result
- Swipe down for notifications
- Double tap to open the original launcher
- Hold empty homescreen space to change the number of app fields

## Promptpad command-search baseline

Promptpad reserves an explicit command mode in the app-drawer search field. This section is the behavioral baseline for runtime implementation; machine-readable acceptance cases live in [`app/src/test/resources/me/pompel/elauncher/command-cases.tsv`](app/src/test/resources/me/pompel/elauncher/command-cases.tsv).

### Routing and interaction

- Command mode is selected only when the first input character is `!`: `query.isNotEmpty() && query.charAt(0) == '!'`.
- Empty input and every non-`!` first character, including whitespace before `!`, use the existing fuzzy app search.
- `!` shows command help and suggestions. Unknown commands show an error and help; they never fall back to app search.
- Removing the leading `!` restores app-search rows without stale command rows.
- Typing only updates help, suggestions, previews, or validation messages. Enter or tapping a valid command row explicitly submits it.
- Command mode never uses automatic single-app launch behavior.

### Commands

| Command | Syntax | Safe outcome after explicit submission |
| --- | --- | --- |
| Call | `!call <contact-or-number>` | Open a prefilled dialer; never place a call |
| Text | `!text <contact-or-number> <message>` | Open a prefilled SMS composer; never send a message |
| Telegram | `!hermes <message>` | Open the Telegram chat for the saved bot username with the message prefilled; never send a message |
| Timer | `!timer <duration> [label]` | Open the system timer form |
| Alarm | `!alarm H:MM` | Set a system alarm silently and show confirmation |
| To-do | `!todo <text>` | Save a local incomplete to-do |
| To-dos | `!todos` | Open the local to-do Activity |
| Grocery | `!grocery <item>` | Save a local incomplete grocery item |
| Groceries | `!groceries` | Open the local grocery Activity |
| Note | `!note <text>` | Save a timestamped local note |
| Notes | `!notes` | Open the local notes Activity |
| Event | `!event <date> <time> <title>` | Open a prefilled Calendar event; never insert it directly |
| Torch | `!t` | Toggle the rear torch only when available and permitted |
| Camera | `!camera` | Open the installed system camera |

### Parsing and result states

- Command names are case-insensitive. The leading `!` selects command mode and is not passed to handlers.
- Timer durations are non-zero contiguous combinations of hours, minutes, and seconds in that order, such as `30s`, `1m15s`, `3m20s`, `10m`, `1h`, and `1h30m`.
- Alarm times use 24-hour `H:mm` or `HH:mm` format and set the alarm silently.
- Event dates are `today`, `tomorrow`, or `YYYY-MM-DD`; times use 24-hour `HH:mm`. Events use local time, a 30-minute default duration, and reject past start times.
- Direct phone numbers contain 7 to 15 digits with an optional leading `+`; spaces, hyphens, and parentheses are ignored for recognition. Contact matching is case-insensitive and uses the longest contact-name prefix.
- Save a concise Telegram bot username in Settings. `!hermes <message>` opens that bot in Telegram with the draft text prefilled and does not send it.
- Multiple matching contacts or multiple numbers for a contact require a visible choice. A contact-name miss is a validation error.
- When Contacts permission is denied, raw phone numbers still work. Name lookups report that contacts are unavailable and offer retry or Settings.
- Incomplete or malformed input shows syntax and an example instead of a best guess.
- Command rows use these states: command help, suggestion, preview, validation error, unknown command, contact choice, permission denied, unavailable, confirmation, and success.
- Unavailable Dialer, SMS, Clock, Calendar, or Camera handlers report an unavailable state without crashing. Torch reports on, off, unavailable, or denied.

### Local list screens

- `!notes`, `!todos`, and `!groceries` open local View-based list Activities and Back returns to Promptpad.
- Notes are shown newest first. To-dos show incomplete items first and support completion and deletion.
- Groceries show incomplete items first and support completion and deletion.
- Notes and to-dos remain local to the device. V1 does not add sync, network access, aliases, macros, plugins, shell execution, or arbitrary intents.

## Download

You can download the apk file directly from the releases tab and install it manually.

## Contributing

Contributions that improve reliability, efficiency, or the focused launcher and command-search experience are welcome. Keep the scope minimal; forks are a better fit for extensive customization, widgets, or unrelated features.
