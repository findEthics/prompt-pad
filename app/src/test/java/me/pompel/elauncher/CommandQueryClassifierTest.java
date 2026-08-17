package me.pompel.elauncher;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;

public class CommandQueryClassifierTest {
    @Test
    public void emptyInputUsesNormalAppSearch() {
        CommandQueryClassifier.Result result = CommandQueryClassifier.classify("");

        assertEquals(CommandQueryClassifier.DisplayState.APP_RESULTS, result.getDisplayState());
        assertEquals("", result.getAppQuery());
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

        assertEquals(CommandQueryClassifier.DisplayState.SUGGESTION, result.getDisplayState());
        assertEquals(Arrays.asList(CommandParser.Type.TODO, CommandParser.Type.TODOS),
                result.getCommands());
    }

    @Test
    public void completeCommandsAreRecognized() {
        assertSuggestion("!call", CommandParser.Type.CALL);
        assertSuggestion("!text", CommandParser.Type.TEXT);
        assertSuggestion("!hermes", CommandParser.Type.HERMES);
        assertSuggestion("!timer", CommandParser.Type.TIMER);
        assertSuggestion("!alarm", CommandParser.Type.ALARM);
        assertSuggestion("!todo", CommandParser.Type.TODO);
        assertPreview("!todos", CommandParser.Type.TODOS);
        assertSuggestion("!grocery", CommandParser.Type.GROCERY);
        assertPreview("!groceries", CommandParser.Type.GROCERIES);
        assertSuggestion("!note", CommandParser.Type.NOTE);
        assertPreview("!notes", CommandParser.Type.NOTES);
        assertSuggestion("!event", CommandParser.Type.EVENT);
        assertPreview("!t", CommandParser.Type.TORCH);
        assertPreview("!camera", CommandParser.Type.CAMERA);
        assertEquals(CommandQueryClassifier.DisplayState.UNKNOWN_COMMAND,
                CommandQueryClassifier.classify("!HeLp").getDisplayState());
    }

    @Test
    public void commandPayloadProducesDisplayOnlyPreviewWithSyntaxHint() {
        CommandQueryClassifier.Result result = CommandQueryClassifier.classify("!text +15551234567 hello there");

        assertEquals(CommandQueryClassifier.DisplayState.PREVIEW, result.getDisplayState());
        assertEquals(CommandParser.Type.TEXT, result.getCommand());
        assertEquals("+15551234567 hello there", result.getArguments());
        assertEquals("!text <contact-or-number> <message>", result.getSyntaxHint());
    }

    @Test
    public void unknownCommandReturnsErrorAndHelpCommands() {
        CommandQueryClassifier.Result result = CommandQueryClassifier.classify("!zoom now");

        assertEquals(CommandQueryClassifier.DisplayState.UNKNOWN_COMMAND, result.getDisplayState());
        assertNull(result.getCommand());
        assertTrue(result.getMessage().startsWith("Unknown command: zoom"));
        assertEquals(14, result.getCommands().size());

        assertEquals(CommandQueryClassifier.DisplayState.UNKNOWN_COMMAND,
                CommandQueryClassifier.classify("!torch").getDisplayState());
    }

    @Test
    public void incompleteAndMalformedCommandsShowGuidanceInsteadOfPreviews() {
        assertSuggestion("!call", CommandParser.Type.CALL);
        assertSuggestion("!text +15551234567", CommandParser.Type.TEXT);
        assertSuggestion("!hermes", CommandParser.Type.HERMES);
        assertSuggestion("!timer", CommandParser.Type.TIMER);
        assertSuggestion("!alarm", CommandParser.Type.ALARM);
        assertSuggestion("!todo", CommandParser.Type.TODO);
        assertSuggestion("!note", CommandParser.Type.NOTE);
        assertSuggestion("!event today 14:30", CommandParser.Type.EVENT);

        CommandQueryClassifier.Result result = CommandQueryClassifier.classify("!timer 0m");
        assertEquals(CommandQueryClassifier.DisplayState.VALIDATION_ERROR, result.getDisplayState());
        assertEquals(CommandParser.Type.TIMER, result.getCommand());
        assertEquals("!timer <duration> [label]", result.getSyntaxHint());

        result = CommandQueryClassifier.classify("!alarm 7:05");
        assertPreview("!alarm 7:05", CommandParser.Type.ALARM);

        assertPreview("!hermes hello there", CommandParser.Type.HERMES);
    }

    @Test
    public void invalidEventDateAndTimeShowSyntaxGuidance() {
        assertEventValidation("!event 2026-02-30 14:30 Project review");
        assertEventValidation("!event today 25:00 Project review");
        assertPreview("!event tomorrow 14:30 Project review", CommandParser.Type.EVENT);
    }

    private static void assertAppSearch(String input) {
        CommandQueryClassifier.Result result = CommandQueryClassifier.classify(input);
        assertEquals(CommandQueryClassifier.DisplayState.APP_RESULTS, result.getDisplayState());
        assertEquals(input, result.getAppQuery());
    }

    private static void assertHelp(String input) {
        CommandQueryClassifier.Result result = CommandQueryClassifier.classify(input);
        assertEquals(CommandQueryClassifier.DisplayState.COMMAND_HELP, result.getDisplayState());
        assertEquals(14, result.getCommands().size());
    }

    private static void assertPreview(String input, CommandParser.Type command) {
        CommandQueryClassifier.Result result = CommandQueryClassifier.classify(input);
        assertEquals(CommandQueryClassifier.DisplayState.PREVIEW, result.getDisplayState());
        assertEquals(command, result.getCommand());
        assertEquals(command.getSyntaxHint(), result.getSyntaxHint());
    }

    private static void assertSuggestion(String input, CommandParser.Type command) {
        CommandQueryClassifier.Result result = CommandQueryClassifier.classify(input);
        assertEquals(CommandQueryClassifier.DisplayState.SUGGESTION, result.getDisplayState());
        assertEquals(command, result.getCommand());
        assertEquals(command.getSyntaxHint(), result.getSyntaxHint());
    }

    private static void assertEventValidation(String input) {
        CommandQueryClassifier.Result result = CommandQueryClassifier.classify(input);
        assertEquals(CommandQueryClassifier.DisplayState.VALIDATION_ERROR, result.getDisplayState());
        assertEquals(CommandParser.Type.EVENT, result.getCommand());
        assertEquals("!event <date> <time> <title>", result.getSyntaxHint());
    }
}
