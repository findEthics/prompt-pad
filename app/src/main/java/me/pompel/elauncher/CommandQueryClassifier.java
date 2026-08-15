package me.pompel.elauncher;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

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
        if (arguments.isEmpty() && (command == Command.CALL || command == Command.TEXT
                || command == Command.TIMER || command == Command.TODO || command == Command.NOTE
                || command == Command.EVENT)) {
            return Result.suggestion(command);
        }
        CommandParser.ParseResult parsed = new CommandParser().parse(
                "!" + command.getName() + (arguments.isEmpty() ? "" : " " + arguments));
        if (parsed.isSuccess() || parsed.getError().getCode()
                == CommandParser.ErrorCode.CONTACT_RESOLUTION_REQUIRED) {
            return Result.preview(command, arguments);
        }
        if (parsed.getError().getCode() == CommandParser.ErrorCode.MISSING_ARGUMENT) {
            return Result.suggestion(command);
        }
        return Result.validation(command, parsed.getError().getMessage());
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
        TORCH("t", "!t"),
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
