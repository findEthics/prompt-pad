# eLauncher

eLauncher is an extremely lightweight and minimal launcher for Android, based on NoLauncher and inspired by [OLauncher Light](https://github.com/tanujnotes/Ultra/), and OLauncher in general. It is even more barebones than OLauncher Light, and aims to provide only the most basic features.

eLauncher favours easy readibility on eInk/ePaper devices, such as the Onyx Boox Note series, and the Bigme HiBreak.

## Features

- Extremely lightweight: only 1010KB
- eInk friendly: uses a light theme by default, fix text size and weight
- Fuzzy Search: search for apps by typing their name
- Bottom search bar in app drawer

- Homescreen and app drawer: swipe up on homescreen to enter the app drawer
- Long press an app field on the homescreen to assign an app, app can be renamed
- Type to search in app drawer, if only one result is left, it is automatically launched (like OLauncher)
- Gestures: swipe down for notification center, left/right swipe to launch any app (configurable in Settings), double tap to open the original launcher
- Customizable swipe gestures: pick any app for left and right swipe gestures via Settings
- Hold on empty space to change the number of apps on homescreen

## Titan command-search V1 contract

The `feat/command-search-v1` branch adds an explicit command mode to the existing app-drawer search field. This section defines the contract before runtime implementation. Machine-readable acceptance cases live in [`app/src/test/resources/me/pompel/elauncher/command-cases.tsv`](app/src/test/resources/me/pompel/elauncher/command-cases.tsv).

### Routing and interaction

- Command mode is selected only when the first input character is `!`: `query.isNotEmpty() && query.charAt(0) == '!'`.
- Empty input and every non-`!` first character, including whitespace before `!`, use the existing fuzzy app search.
- `!` shows command help and suggestions. Unknown commands show an error and help; they never fall back to app search.
- Removing the leading `!` restores app-search rows without stale command rows.
- Typing only updates help, suggestions, previews, or validation messages. Enter or tapping a valid command row explicitly submits it.
- Command mode never uses eLauncher's automatic single-app launch behavior.

### Commands

| Command | Syntax | Safe outcome after explicit submission |
| --- | --- | --- |
| Help | `!help` | Show commands, examples, and syntax |
| Call | `!call <contact-or-number>` | Open a prefilled dialer; never place a call |
| Text | `!text <contact-or-number> <message>` | Open a prefilled SMS composer; never send a message |
| Timer | `!timer <duration> [label]` | Open the system timer form |
| To-do | `!todo <text>` | Save a local incomplete to-do |
| To-dos | `!todos` | Open the local to-do Activity |
| Note | `!note <text>` | Save a timestamped local note |
| Notes | `!notes` | Open the local notes Activity |
| Event | `!event <date> <time> <title>` | Open a prefilled Calendar event; never insert it directly |
| Torch | `!torch` | Toggle the rear torch only when available and permitted |
| Camera | `!camera` | Open the installed system camera |

### Parsing and result states

- Command names are case-insensitive. The leading `!` selects command mode and is not passed to handlers.
- Timer durations are non-zero combinations of hours and minutes such as `10m`, `1h`, and `1h30m`.
- Event dates are `today`, `tomorrow`, or `YYYY-MM-DD`; times use 24-hour `HH:mm`. Events use local time, a 30-minute default duration, and reject past start times.
- Direct phone numbers contain 7 to 15 digits with an optional leading `+`; spaces, hyphens, and parentheses are ignored for recognition. Contact matching is case-insensitive and uses the longest contact-name prefix.
- Multiple matching contacts or multiple numbers for a contact require a visible choice. A contact-name miss is a validation error.
- When Contacts permission is denied, raw phone numbers still work. Name lookups report that contacts are unavailable and offer retry or Settings.
- Incomplete or malformed input shows syntax and an example instead of a best guess.
- Command rows use these states: command help, suggestion, preview, validation error, unknown command, contact choice, permission denied, unavailable, confirmation, and success.
- Unavailable Dialer, SMS, Clock, Calendar, or Camera handlers report an unavailable state without crashing. Torch reports on, off, unavailable, or denied.

### Local list screens

- `!notes` and `!todos` open separate View-based Activities and Back returns to the launcher.
- Notes are shown newest first. To-dos show incomplete items first and support completion and deletion.
- Notes and to-dos remain local to the device. V1 does not add sync, network access, aliases, macros, plugins, shell execution, or arbitrary intents.

## apk size differences with OLauncher Light

This might have been done on purpose, but OLauncher Light uses long deprecated APIs, like ListView to achieve its impressive 23 KB apk size. eLauncher uses RecyclerView, which is much better for performance and memory usage, and also uses many other newer APIs. Thus, the APK size is much larger than with OLauncher Light, but still really small — ~1 MB.

## Download

You can download the apk file directly from the releases tab and install it manually.

## Contributing

Feel free to contribute if you found a bug or have a way to make the code more efficient or minimal, but please don't add massive new features. If you feel like adding a lot of customization options, widgets, etc. please start your own fork, as the scope of this project is to be as (reasonably) barebones of a launcher as possible.
