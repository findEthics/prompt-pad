package me.pompel.elauncher;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;

public class CommandQueryClassifierTest {
    @Test
    public void emptyInputUsesNormalAppSearch() {
        CommandQueryClassifier.Result result = CommandQueryClassifier.classify("");

        assertEquals(CommandQueryClassifier.Mode.APP_SEARCH, result.getMode());
        assertEquals(CommandQueryClassifier.DisplayState.APP_RESULTS, result.getDisplayState());
        assertEquals("", result.getAppQuery());
        assertFalse(result.isDisplayOnly());
    }

    @Test
    public void normalInputAndWhitespaceBeforeBangUseAppSearch() {
        assertAppSearch("calendar");
        assertAppSearch(" !call Ada");
    }

    @Test
    public void bareAndWhitespaceOnlyCommandsShowHelp() {
        assertHelp("!");
        assertHelp("! \t ");
    }

    @Test
    public void partialCommandReturnsEveryMatchingSuggestion() {
        CommandQueryClassifier.Result result = CommandQueryClassifier.classify("!to");

        assertEquals(CommandQueryClassifier.Mode.COMMAND_SEARCH, result.getMode());
        assertEquals(CommandQueryClassifier.DisplayState.SUGGESTION, result.getDisplayState());
        assertEquals(Arrays.asList(CommandQueryClassifier.Command.TODO,
                CommandQueryClassifier.Command.TODOS),
                result.getCommands());
        assertTrue(result.isDisplayOnly());
    }

    @Test
    public void completeCommandsAreRecognizedIncludingHelp() {
        assertSuggestion("!call", CommandQueryClassifier.Command.CALL);
        assertSuggestion("!text", CommandQueryClassifier.Command.TEXT);
        assertSuggestion("!timer", CommandQueryClassifier.Command.TIMER);
        assertSuggestion("!alarm", CommandQueryClassifier.Command.ALARM);
        assertSuggestion("!todo", CommandQueryClassifier.Command.TODO);
        assertPreview("!todos", CommandQueryClassifier.Command.TODOS);
        assertSuggestion("!note", CommandQueryClassifier.Command.NOTE);
        assertPreview("!notes", CommandQueryClassifier.Command.NOTES);
        assertSuggestion("!event", CommandQueryClassifier.Command.EVENT);
        assertPreview("!t", CommandQueryClassifier.Command.TORCH);
        assertPreview("!camera", CommandQueryClassifier.Command.CAMERA);
        assertPreview("!HeLp", CommandQueryClassifier.Command.HELP);
    }

    @Test
    public void commandPayloadProducesDisplayOnlyPreviewWithSyntaxHint() {
        CommandQueryClassifier.Result result = CommandQueryClassifier.classify("!text +15551234567 hello there");

        assertEquals(CommandQueryClassifier.DisplayState.PREVIEW, result.getDisplayState());
        assertEquals(CommandQueryClassifier.Command.TEXT, result.getCommand());
        assertEquals("+15551234567 hello there", result.getArguments());
        assertEquals("!text <contact-or-number> <message>", result.getSyntaxHint());
        assertTrue(result.isDisplayOnly());
    }

    @Test
    public void unknownCommandReturnsErrorAndHelpCommands() {
        CommandQueryClassifier.Result result = CommandQueryClassifier.classify("!zoom now");

        assertEquals(CommandQueryClassifier.Mode.COMMAND_SEARCH, result.getMode());
        assertEquals(CommandQueryClassifier.DisplayState.UNKNOWN_COMMAND, result.getDisplayState());
        assertNull(result.getCommand());
        assertTrue(result.getMessage().startsWith("Unknown command: zoom"));
        assertEquals(12, result.getCommands().size());
        assertTrue(result.getCommands().contains(CommandQueryClassifier.Command.HELP));

        assertEquals(CommandQueryClassifier.DisplayState.UNKNOWN_COMMAND,
                CommandQueryClassifier.classify("!torch").getDisplayState());
    }

    @Test
    public void incompleteAndMalformedCommandsShowGuidanceInsteadOfPreviews() {
        assertSuggestion("!call", CommandQueryClassifier.Command.CALL);
        assertSuggestion("!text +15551234567", CommandQueryClassifier.Command.TEXT);
        assertSuggestion("!timer", CommandQueryClassifier.Command.TIMER);
        assertSuggestion("!alarm", CommandQueryClassifier.Command.ALARM);
        assertSuggestion("!todo", CommandQueryClassifier.Command.TODO);
        assertSuggestion("!note", CommandQueryClassifier.Command.NOTE);
        assertSuggestion("!event today 14:30", CommandQueryClassifier.Command.EVENT);

        CommandQueryClassifier.Result result = CommandQueryClassifier.classify("!timer 0m");
        assertEquals(CommandQueryClassifier.DisplayState.VALIDATION_ERROR, result.getDisplayState());
        assertEquals(CommandQueryClassifier.Command.TIMER, result.getCommand());
        assertEquals("!timer <duration> [label]", result.getSyntaxHint());

        result = CommandQueryClassifier.classify("!alarm 7:05");
        assertEquals(CommandQueryClassifier.DisplayState.VALIDATION_ERROR, result.getDisplayState());
        assertEquals(CommandQueryClassifier.Command.ALARM, result.getCommand());
        assertEquals("!alarm HH:MM", result.getSyntaxHint());
    }

    @Test
    public void invalidEventDateAndTimeShowSyntaxGuidance() {
        assertEventValidation("!event 2026-02-30 14:30 Project review");
        assertEventValidation("!event today 25:00 Project review");
        assertPreview("!event 2026-08-16 14:30 Project review", CommandQueryClassifier.Command.EVENT);
    }

    private static void assertAppSearch(String input) {
        CommandQueryClassifier.Result result = CommandQueryClassifier.classify(input);
        assertEquals(CommandQueryClassifier.Mode.APP_SEARCH, result.getMode());
        assertEquals(CommandQueryClassifier.DisplayState.APP_RESULTS, result.getDisplayState());
        assertEquals(input, result.getAppQuery());
    }

    private static void assertHelp(String input) {
        CommandQueryClassifier.Result result = CommandQueryClassifier.classify(input);
        assertEquals(CommandQueryClassifier.Mode.COMMAND_SEARCH, result.getMode());
        assertEquals(CommandQueryClassifier.DisplayState.COMMAND_HELP, result.getDisplayState());
        assertEquals(12, result.getCommands().size());
        assertTrue(result.isDisplayOnly());
    }

    private static void assertPreview(String input, CommandQueryClassifier.Command command) {
        CommandQueryClassifier.Result result = CommandQueryClassifier.classify(input);
        assertEquals(CommandQueryClassifier.DisplayState.PREVIEW, result.getDisplayState());
        assertEquals(command, result.getCommand());
        assertEquals(command.getSyntaxHint(), result.getSyntaxHint());
        assertTrue(result.isDisplayOnly());
    }

    private static void assertSuggestion(String input, CommandQueryClassifier.Command command) {
        CommandQueryClassifier.Result result = CommandQueryClassifier.classify(input);
        assertEquals(CommandQueryClassifier.DisplayState.SUGGESTION, result.getDisplayState());
        assertEquals(command, result.getCommand());
        assertEquals(command.getSyntaxHint(), result.getSyntaxHint());
        assertTrue(result.isDisplayOnly());
    }

    private static void assertEventValidation(String input) {
        CommandQueryClassifier.Result result = CommandQueryClassifier.classify(input);
        assertEquals(CommandQueryClassifier.DisplayState.VALIDATION_ERROR, result.getDisplayState());
        assertEquals(CommandQueryClassifier.Command.EVENT, result.getCommand());
        assertEquals("!event <date> <time> <title>", result.getSyntaxHint());
    }
}
