package me.pompel.elauncher;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Classifies launcher search input for display. It deliberately performs no command execution
 * or argument validation, so callers can safely use every result while the user is typing.
 */
public final class CommandQueryClassifier {
    private static final List<CommandParser.Type> COMMANDS = Collections.unmodifiableList(
            Arrays.asList(CommandParser.Type.values()));

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
        CommandParser.Type command = CommandParser.Type.fromName(commandName);
        if (command != null) {
            return classifyCommand(command, arguments);
        }

        List<CommandParser.Type> matches = matchingCommands(commandName);
        if (!matches.isEmpty()) {
            return Result.suggestions(commandName, matches);
        }
        return Result.unknown(commandName);
    }

    private static Result classifyCommand(CommandParser.Type command, String arguments) {
        if (arguments.isEmpty() && (command == CommandParser.Type.CALL || command == CommandParser.Type.TEXT
                || command == CommandParser.Type.HERMES
                || command == CommandParser.Type.TIMER || command == CommandParser.Type.ALARM
                || command == CommandParser.Type.TODO || command == CommandParser.Type.NOTE
                || command == CommandParser.Type.EVENT)) {
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

    private static List<CommandParser.Type> matchingCommands(String prefix) {
        String normalizedPrefix = prefix.toLowerCase(Locale.ROOT);
        List<CommandParser.Type> matches = new ArrayList<>();
        for (CommandParser.Type command : COMMANDS) {
            if (command.getName().startsWith(normalizedPrefix)) {
                matches.add(command);
            }
        }
        return Collections.unmodifiableList(matches);
    }

    public enum DisplayState {
        APP_RESULTS,
        COMMAND_HELP,
        SUGGESTION,
        PREVIEW,
        VALIDATION_ERROR,
        UNKNOWN_COMMAND
    }

    /** Immutable display model intended for direct conversion to adapter rows. */
    public static final class Result {
        private final DisplayState displayState;
        private final String appQuery;
        private final CommandParser.Type command;
        private final String arguments;
        private final List<CommandParser.Type> commands;
        private final String message;
        private final String syntaxHint;

        private Result(DisplayState displayState, String appQuery, CommandParser.Type command,
                       String arguments, List<CommandParser.Type> commands, String message,
                       String syntaxHint) {
            this.displayState = displayState;
            this.appQuery = appQuery;
            this.command = command;
            this.arguments = arguments;
            this.commands = commands;
            this.message = message;
            this.syntaxHint = syntaxHint;
        }

        private static Result appSearch(String query) {
            return new Result(DisplayState.APP_RESULTS, query, null, "",
                    Collections.<CommandParser.Type>emptyList(), "", "");
        }

        private static Result help() {
            return new Result(DisplayState.COMMAND_HELP, "", null, "",
                    COMMANDS, "Available commands", "");
        }

        private static Result suggestions(String prefix, List<CommandParser.Type> commands) {
            return new Result(DisplayState.SUGGESTION, "", null, "", commands,
                    "Commands matching " + prefix, "");
        }

        private static Result suggestion(CommandParser.Type command) {
            return new Result(DisplayState.SUGGESTION, "", command, "",
                    Collections.singletonList(command), "Use " + command.getSyntaxHint(),
                    command.getSyntaxHint());
        }

        private static Result preview(CommandParser.Type command, String arguments) {
            return new Result(DisplayState.PREVIEW, "", command, arguments,
                    Collections.singletonList(command), "Preview only", command.getSyntaxHint());
        }

        private static Result validation(CommandParser.Type command, String message) {
            return new Result(DisplayState.VALIDATION_ERROR, "", command, "",
                    Collections.singletonList(command), message, command.getSyntaxHint());
        }

        private static Result unknown(String name) {
            return new Result(DisplayState.UNKNOWN_COMMAND, "", null, "", COMMANDS,
                    "Unknown command: " + name, "");
        }

        public DisplayState getDisplayState() {
            return displayState;
        }

        public String getAppQuery() {
            return appQuery;
        }

        public CommandParser.Type getCommand() {
            return command;
        }

        public String getArguments() {
            return arguments;
        }

        public List<CommandParser.Type> getCommands() {
            return commands;
        }

        public String getMessage() {
            return message;
        }

        public String getSyntaxHint() {
            return syntaxHint;
        }

    }
}
