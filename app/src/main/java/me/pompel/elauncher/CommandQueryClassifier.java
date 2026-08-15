package me.pompel.elauncher;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.text.ParseException;
import java.text.SimpleDateFormat;

/**
 * Classifies launcher search input for display. It deliberately performs no command execution
 * or argument validation, so callers can safely use every result while the user is typing.
 */
public final class CommandQueryClassifier {
    private static final List<Command> COMMANDS;

    static {
        List<Command> commands = new ArrayList<>();
        Collections.addAll(commands, Command.values());
        COMMANDS = Collections.unmodifiableList(commands);
    }

    private CommandQueryClassifier() {
    }

    public static Result classify(String input) {
        String query = input == null ? "" : input;
        if (query.isEmpty() || query.charAt(0) != '!') {
            return Result.appSearch(query);
        }

        String commandQuery = query.substring(1).trim();
        if (commandQuery.isEmpty()) {
            return Result.help();
        }

        int separator = firstWhitespace(commandQuery);
        String commandName = separator == -1 ? commandQuery : commandQuery.substring(0, separator);
        String arguments = separator == -1 ? "" : commandQuery.substring(separator).trim();
        Command command = Command.fromName(commandName);
        if (command != null) {
            return classifyCommand(command, arguments);
        }

        List<Command> matches = matchingCommands(commandName);
        if (!matches.isEmpty()) {
            return Result.suggestions(commandName, matches);
        }
        return Result.unknown(commandName);
    }

    private static Result classifyCommand(Command command, String arguments) {
        switch (command) {
            case HELP:
            case TODOS:
            case NOTES:
            case TORCH:
            case CAMERA:
                return arguments.isEmpty() ? Result.preview(command, arguments)
                        : Result.validation(command, "This command does not take arguments.");
            case CALL:
            case TODO:
            case NOTE:
                return arguments.isEmpty() ? Result.suggestion(command) : Result.preview(command, arguments);
            case TEXT:
                return hasWords(arguments, 2) ? Result.preview(command, arguments)
                        : Result.suggestion(command);
            case TIMER:
                if (arguments.isEmpty()) return Result.suggestion(command);
                return isDuration(firstWord(arguments)) ? Result.preview(command, arguments)
                        : Result.validation(command, "Use durations such as 10m, 1h, or 1h30m.");
            case EVENT:
                return classifyEvent(command, arguments);
        }
        return Result.unknown(command.getName());
    }

    private static Result classifyEvent(Command command, String arguments) {
        if (!hasWords(arguments, 3)) return Result.suggestion(command);
        String[] parts = arguments.split("\\s+", 3);
        if (!isEventDate(parts[0])) {
            return Result.validation(command, "Use today, tomorrow, or a valid YYYY-MM-DD date.");
        }
        if (!isEventTime(parts[1])) {
            return Result.validation(command, "Use a 24-hour HH:mm time.");
        }
        return Result.preview(command, arguments);
    }

    private static boolean hasWords(String value, int count) {
        if (value.isEmpty()) return false;
        return value.trim().split("\\s+").length >= count;
    }

    private static String firstWord(String value) {
        int separator = firstWhitespace(value);
        return separator == -1 ? value : value.substring(0, separator);
    }

    private static boolean isDuration(String duration) {
        return duration.matches("(?:[1-9]\\d*h(?:\\d+m)?|[1-9]\\d*m)");
    }

    private static boolean isEventDate(String date) {
        if ("today".equalsIgnoreCase(date) || "tomorrow".equalsIgnoreCase(date)) return true;
        if (!date.matches("\\d{4}-\\d{2}-\\d{2}")) return false;

        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT);
        formatter.setLenient(false);
        try {
            return formatter.parse(date) != null;
        } catch (ParseException exception) {
            return false;
        }
    }

    private static boolean isEventTime(String time) {
        return time.matches("(?:[01]\\d|2[0-3]):[0-5]\\d");
    }

    private static int firstWhitespace(String value) {
        for (int index = 0; index < value.length(); index++) {
            if (Character.isWhitespace(value.charAt(index))) {
                return index;
            }
        }
        return -1;
    }

    private static List<Command> matchingCommands(String prefix) {
        String normalizedPrefix = prefix.toLowerCase(Locale.ROOT);
        List<Command> matches = new ArrayList<>();
        for (Command command : COMMANDS) {
            if (command.name.startsWith(normalizedPrefix)) {
                matches.add(command);
            }
        }
        return Collections.unmodifiableList(matches);
    }

    public enum Mode {
        APP_SEARCH,
        COMMAND_SEARCH
    }

    public enum DisplayState {
        APP_RESULTS,
        COMMAND_HELP,
        SUGGESTION,
        PREVIEW,
        VALIDATION_ERROR,
        UNKNOWN_COMMAND
    }

    public enum Command {
        HELP("help", "!help"),
        CALL("call", "!call <contact-or-number>"),
        TEXT("text", "!text <contact-or-number> <message>"),
        TIMER("timer", "!timer <duration> [label]"),
        TODO("todo", "!todo <text>"),
        TODOS("todos", "!todos"),
        NOTE("note", "!note <text>"),
        NOTES("notes", "!notes"),
        EVENT("event", "!event <date> <time> <title>"),
        TORCH("torch", "!torch"),
        CAMERA("camera", "!camera");

        private final String name;
        private final String syntaxHint;

        Command(String name, String syntaxHint) {
            this.name = name;
            this.syntaxHint = syntaxHint;
        }

        public String getName() {
            return name;
        }

        public String getSyntaxHint() {
            return syntaxHint;
        }

        private static Command fromName(String name) {
            for (Command command : COMMANDS) {
                if (command.name.equalsIgnoreCase(name)) {
                    return command;
                }
            }
            return null;
        }
    }

    /** Immutable display model intended for direct conversion to adapter rows. */
    public static final class Result {
        private final Mode mode;
        private final DisplayState displayState;
        private final String appQuery;
        private final Command command;
        private final String arguments;
        private final List<Command> commands;
        private final String message;
        private final String syntaxHint;

        private Result(Mode mode, DisplayState displayState, String appQuery, Command command,
                       String arguments, List<Command> commands, String message, String syntaxHint) {
            this.mode = mode;
            this.displayState = displayState;
            this.appQuery = appQuery;
            this.command = command;
            this.arguments = arguments;
            this.commands = commands;
            this.message = message;
            this.syntaxHint = syntaxHint;
        }

        private static Result appSearch(String query) {
            return new Result(Mode.APP_SEARCH, DisplayState.APP_RESULTS, query, null, "",
                    Collections.<Command>emptyList(), "", "");
        }

        private static Result help() {
            return new Result(Mode.COMMAND_SEARCH, DisplayState.COMMAND_HELP, "", null, "",
                    COMMANDS, "Available commands", "");
        }

        private static Result suggestions(String prefix, List<Command> commands) {
            return new Result(Mode.COMMAND_SEARCH, DisplayState.SUGGESTION, "", null, "", commands,
                    "Commands matching " + prefix, "");
        }

        private static Result suggestion(Command command) {
            return new Result(Mode.COMMAND_SEARCH, DisplayState.SUGGESTION, "", command, "",
                    Collections.singletonList(command), "Use " + command.syntaxHint, command.syntaxHint);
        }

        private static Result preview(Command command, String arguments) {
            return new Result(Mode.COMMAND_SEARCH, DisplayState.PREVIEW, "", command, arguments,
                    Collections.singletonList(command), "Preview only", command.syntaxHint);
        }

        private static Result validation(Command command, String message) {
            return new Result(Mode.COMMAND_SEARCH, DisplayState.VALIDATION_ERROR, "", command, "",
                    Collections.singletonList(command), message, command.syntaxHint);
        }

        private static Result unknown(String name) {
            return new Result(Mode.COMMAND_SEARCH, DisplayState.UNKNOWN_COMMAND, "", null, "", COMMANDS,
                    "Unknown command: " + name, "");
        }

        public Mode getMode() {
            return mode;
        }

        public DisplayState getDisplayState() {
            return displayState;
        }

        public String getAppQuery() {
            return appQuery;
        }

        public Command getCommand() {
            return command;
        }

        public String getArguments() {
            return arguments;
        }

        public List<Command> getCommands() {
            return commands;
        }

        public String getMessage() {
            return message;
        }

        public String getSyntaxHint() {
            return syntaxHint;
        }

        public boolean isDisplayOnly() {
            return mode == Mode.COMMAND_SEARCH;
        }
    }
}
