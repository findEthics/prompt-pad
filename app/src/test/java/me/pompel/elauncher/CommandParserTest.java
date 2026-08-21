package me.pompel.elauncher;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Calendar;
import java.util.TimeZone;

public class CommandParserTest {
    private static final TimeZone UTC = TimeZone.getTimeZone("UTC");

    @Test
    public void parsesEverySupportedArgumentFreeCommand() {
        CommandParser parser = parserAt("2026-08-15 10:00");

        assertError(parser.parse("!help"), CommandParser.ErrorCode.UNKNOWN_COMMAND);
        assertCommand(parser.parse("!todos"), CommandParser.Type.TODOS,
                CommandParser.ZeroPayloadCommand.class);
        assertCommand(parser.parse("!notes"), CommandParser.Type.NOTES,
                CommandParser.ZeroPayloadCommand.class);
        assertCommand(parser.parse("!groceries"), CommandParser.Type.GROCERIES,
                CommandParser.ZeroPayloadCommand.class);
        assertCommand(parser.parse("!t"), CommandParser.Type.TORCH,
                CommandParser.ZeroPayloadCommand.class);
        assertCommand(parser.parse("!camera"), CommandParser.Type.CAMERA,
                CommandParser.ZeroPayloadCommand.class);
        assertError(parser.parse("!t now"), CommandParser.ErrorCode.UNEXPECTED_ARGUMENT);
        assertError(parser.parse("!torch"), CommandParser.ErrorCode.UNKNOWN_COMMAND);
    }

    @Test
    public void parsesTextualCommandsAndRejectsMissingPayloads() {
        CommandParser parser = parserAt("2026-08-15 10:00");

        CommandParser.NoteCommand note = (CommandParser.NoteCommand) parser.parse("!NoTe Meter reading 42")
                .getCommand();
        CommandParser.TodoCommand todo = (CommandParser.TodoCommand) parser.parse("!todo Buy batteries")
                .getCommand();
        CommandParser.GroceryCommand grocery = (CommandParser.GroceryCommand) parser
                .parse("!grocery Milk").getCommand();
        CommandParser.GroceryCommand buy = (CommandParser.GroceryCommand) parser
                .parse("!buy Milk").getCommand();

        assertEquals("Meter reading 42", note.getText());
        assertEquals("Buy batteries", todo.getText());
        assertEquals("Milk", grocery.getItem());
        assertEquals(CommandParser.Type.GROCERY, grocery.getType());
        assertEquals("Milk", buy.getItem());
        assertEquals(CommandParser.Type.BUY, buy.getType());
        assertError(parser.parse("!note"), CommandParser.ErrorCode.MISSING_ARGUMENT);
        assertError(parser.parse("!todo"), CommandParser.ErrorCode.MISSING_ARGUMENT);
        assertError(parser.parse("!grocery"), CommandParser.ErrorCode.MISSING_ARGUMENT);
        assertError(parser.parse("!buy"), CommandParser.ErrorCode.MISSING_ARGUMENT);
        assertError(parser.parse("!unknown"), CommandParser.ErrorCode.UNKNOWN_COMMAND);
        assertError(parser.parse("calendar"), CommandParser.ErrorCode.NOT_A_COMMAND);
    }

    @Test
    public void normalizesRawNumbersForCallAndText() {
        CommandParser parser = parserAt("2026-08-15 10:00");

        CommandParser.CallCommand call = (CommandParser.CallCommand) parser
                .parse("!call +1 (555) 123-4567").getCommand();
        CommandParser.TextCommand text = (CommandParser.TextCommand) parser
                .parse("!text 555 123 4567 hello there").getCommand();

        assertEquals(CommandParser.Recipient.Kind.PHONE_NUMBER, call.getRecipient().getKind());
        assertEquals("+15551234567", call.getRecipient().getValue());
        assertEquals("5551234567", text.getRecipient().getValue());
        assertEquals("hello there", text.getMessage());
        assertEquals("+15551234567", CommandParser.normalizePhoneNumber("+1 (555) 123-4567"));
        assertEquals("1234567", CommandParser.normalizePhoneNumber("1234567"));
        assertEquals(null, CommandParser.normalizePhoneNumber("123456"));
        assertError(parser.parse("!call 123456"), CommandParser.ErrorCode.INVALID_RECIPIENT);
        assertError(parser.parse("!text +15551234567"), CommandParser.ErrorCode.MISSING_ARGUMENT);
    }

    @Test
    public void parsesHermesMessageOnlyForTheSavedTelegramBot() {
        CommandParser parser = parserAt("2026-08-15 10:00");

        CommandParser.HermesCommand hermes = (CommandParser.HermesCommand) parser
                .parse("!hermes hello there").getCommand();

        assertEquals("hello there", hermes.getMessage());
        assertError(parser.parse("!hermes"), CommandParser.ErrorCode.MISSING_ARGUMENT);
    }

    @Test
    public void normalizesTelegramBotUsernameForSettings() {
        assertEquals("hermes_bot", CommandParser.normalizeTelegramUsername(" @hermes_bot "));
        assertEquals("hermes_bot", CommandParser.normalizeTelegramUsername("hermes_bot"));
        assertNull(CommandParser.normalizeTelegramUsername("bad-name"));
        assertNull(CommandParser.normalizeTelegramUsername("abcd"));
    }

    @Test
    public void acceptsAnUnresolvedCallPrefixAndUsesAResolverForNamedText() {
        CommandParser withoutResolver = parserAt("2026-08-15 10:00");
        CommandParser.CallCommand unresolved = (CommandParser.CallCommand) withoutResolver
                .parse("!call Ada Lovelace").getCommand();

        assertEquals(CommandParser.Recipient.Kind.CONTACT_PREFIX, unresolved.getRecipient().getKind());
        assertEquals("Ada Lovelace", unresolved.getRecipient().getValue());
        assertError(withoutResolver.parse("!text Ada Lovelace hello"),
                CommandParser.ErrorCode.CONTACT_RESOLUTION_REQUIRED);

        CommandParser parser = new CommandParser(new FixedClock("2026-08-15 10:00"),
                new CommandParser.ContactResolver() {
                    @Override
                    public CommandParser.ContactMatch resolveLongestPrefix(String input) {
                        if (input.toLowerCase().startsWith("ada lovelace")) {
                            return new CommandParser.ContactMatch(12);
                        }
                        return null;
                    }
                });
        CommandParser.TextCommand text = (CommandParser.TextCommand) parser
                .parse("!text ADA lovelace hello there").getCommand();

        assertEquals(CommandParser.Recipient.Kind.CONTACT_PREFIX, text.getRecipient().getKind());
        assertEquals("ADA lovelace", text.getRecipient().getValue());
        assertEquals("hello there", text.getMessage());
    }

    @Test
    public void normalizesTimerDurationsToSeconds() {
        CommandParser parser = parserAt("2026-08-15 10:00");

        CommandParser.TimerCommand hour = (CommandParser.TimerCommand) parser.parse("!timer 1h").getCommand();
        CommandParser.TimerCommand combined = (CommandParser.TimerCommand) parser
                .parse("!timer 1H30m Tea").getCommand();
        CommandParser.TimerCommand zeroHours = (CommandParser.TimerCommand) parser
                .parse("!timer 0h30m Stretch").getCommand();
        CommandParser.TimerCommand secondsOnly = (CommandParser.TimerCommand) parser
                .parse("!timer 30S Tea").getCommand();
        CommandParser.TimerCommand seconds = (CommandParser.TimerCommand) parser
                .parse("!timer 3M20S Tea").getCommand();
        CommandParser.TimerCommand maximum = (CommandParser.TimerCommand) parser
                .parse("!timer 2147483647s").getCommand();

        assertEquals(3600, hour.getDurationSeconds());
        assertEquals(5400, combined.getDurationSeconds());
        assertEquals("Tea", combined.getLabel());
        assertEquals(1800, zeroHours.getDurationSeconds());
        assertEquals("Stretch", zeroHours.getLabel());
        assertEquals(30, secondsOnly.getDurationSeconds());
        assertEquals("Tea", secondsOnly.getLabel());
        assertEquals(200, seconds.getDurationSeconds());
        assertEquals("Tea", seconds.getLabel());
        assertEquals(Integer.MAX_VALUE, maximum.getDurationSeconds());
        assertError(parser.parse("!timer 0s"), CommandParser.ErrorCode.INVALID_DURATION);
        assertError(parser.parse("!timer 0m"), CommandParser.ErrorCode.INVALID_DURATION);
        assertError(parser.parse("!timer 10x"), CommandParser.ErrorCode.INVALID_DURATION);
        assertError(parser.parse("!timer 1s1m"), CommandParser.ErrorCode.INVALID_DURATION);
        assertError(parser.parse("!timer 2147483648s"), CommandParser.ErrorCode.INVALID_DURATION);
    }

    @Test
    public void parsesFlexibleTwentyFourHourAlarms() {
        CommandParser parser = parserAt("2026-08-15 10:00");

        CommandParser.AlarmCommand midnight = (CommandParser.AlarmCommand) parser
                .parse("!alarm 00:00").getCommand();
        CommandParser.AlarmCommand singleDigitHour = (CommandParser.AlarmCommand) parser
                .parse("!alarm 7:05").getCommand();
        CommandParser.AlarmCommand lastMinute = (CommandParser.AlarmCommand) parser
                .parse("!alarm 23:59").getCommand();

        assertEquals(0, midnight.getHour());
        assertEquals(0, midnight.getMinute());
        assertEquals(7, singleDigitHour.getHour());
        assertEquals(5, singleDigitHour.getMinute());
        assertEquals(23, lastMinute.getHour());
        assertEquals(59, lastMinute.getMinute());
        assertError(parser.parse("!alarm"), CommandParser.ErrorCode.MISSING_ARGUMENT);
        assertError(parser.parse("!alarm 24:00"), CommandParser.ErrorCode.INVALID_TIME);
        assertError(parser.parse("!alarm 07:05 label"), CommandParser.ErrorCode.UNEXPECTED_ARGUMENT);
    }

    @Test
    public void resolvesTodayTomorrowAndIsoEventDatesWithTheInjectedClock() {
        CommandParser parser = parserAt("2026-08-15 10:00");

        CommandParser.EventCommand today = (CommandParser.EventCommand) parser
                .parse("!event today 10:01 Standup").getCommand();
        CommandParser.EventCommand tomorrow = (CommandParser.EventCommand) parser
                .parse("!event tomorrow 00:00 Planning").getCommand();
        CommandParser.EventCommand iso = (CommandParser.EventCommand) parser
                .parse("!event 2026-08-16 14:30 Project review").getCommand();

        assertEquals("Project review", iso.getTitle());
        assertEquals(CommandParser.DEFAULT_EVENT_DURATION_MINUTES, iso.getDurationMinutes());
        assertTrue(today.getStartTimeMillis() > new FixedClock("2026-08-15 10:00").currentTimeMillis());
        assertTrue(tomorrow.getStartTimeMillis() > new FixedClock("2026-08-15 10:00").currentTimeMillis());
        assertTrue(iso.getStartTimeMillis() > new FixedClock("2026-08-15 10:00").currentTimeMillis());
    }

    @Test
    public void rejectsPastAndMalformedEvents() {
        CommandParser parser = parserAt("2026-08-15 10:00");

        assertError(parser.parse("!event today 10:00 Standup"), CommandParser.ErrorCode.PAST_EVENT);
        assertError(parser.parse("!event today 09:59 Standup"), CommandParser.ErrorCode.PAST_EVENT);
        assertError(parser.parse("!event 2026-02-30 14:30 Review"), CommandParser.ErrorCode.INVALID_DATE);
        assertError(parser.parse("!event today 24:00 Review"), CommandParser.ErrorCode.INVALID_TIME);
        assertError(parser.parse("!event today 9:00 Review"), CommandParser.ErrorCode.INVALID_TIME);
        assertError(parser.parse("!event today 14:30"), CommandParser.ErrorCode.MISSING_ARGUMENT);
    }

    private static CommandParser parserAt(String localTime) {
        return new CommandParser(new FixedClock(localTime));
    }

    private static void assertCommand(CommandParser.ParseResult result, CommandParser.Type type,
                                      Class<?> commandClass) {
        assertTrue(result.isSuccess());
        assertEquals(type, result.getCommand().getType());
        assertTrue(commandClass.isInstance(result.getCommand()));
    }

    private static void assertError(CommandParser.ParseResult result, CommandParser.ErrorCode code) {
        assertFalse(result.isSuccess());
        assertEquals(code, result.getError().getCode());
        assertTrue(result.getError().getMessage().length() > 0);
        assertTrue(result.getError().getSyntax().startsWith("!"));
    }

    private static final class FixedClock implements CommandParser.Clock {
        private final long currentTimeMillis;

        private FixedClock(String localTime) {
            String[] dateAndTime = localTime.split(" ");
            String[] date = dateAndTime[0].split("-");
            String[] time = dateAndTime[1].split(":");
            Calendar calendar = Calendar.getInstance(UTC);
            calendar.clear();
            calendar.set(Integer.parseInt(date[0]), Integer.parseInt(date[1]) - 1,
                    Integer.parseInt(date[2]), Integer.parseInt(time[0]), Integer.parseInt(time[1]), 0);
            currentTimeMillis = calendar.getTimeInMillis();
        }

        @Override
        public long currentTimeMillis() {
            return currentTimeMillis;
        }

        @Override
        public TimeZone timeZone() {
            return UTC;
        }
    }
}
