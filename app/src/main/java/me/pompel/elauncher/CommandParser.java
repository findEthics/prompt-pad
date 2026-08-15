package me.pompel.elauncher;

import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses executable command input without depending on Android framework classes. */
public final class CommandParser {
    public static final int DEFAULT_EVENT_DURATION_MINUTES = 30;

    private static final Pattern DURATION_PATTERN = Pattern.compile(
            "(?:(\\d+)h)?(?:(\\d+)m)?", Pattern.CASE_INSENSITIVE);
    private static final Pattern ISO_DATE_PATTERN = Pattern.compile("(\\d{4})-(\\d{2})-(\\d{2})");
    private static final Pattern TIME_PATTERN = Pattern.compile("(?:[01]\\d|2[0-3]):[0-5]\\d");

    private final Clock clock;
    private final ContactResolver contactResolver;

    public CommandParser() {
        this(new SystemClock(), null);
    }

    public CommandParser(Clock clock) {
        this(clock, null);
    }

    public CommandParser(ContactResolver contactResolver) {
        this(new SystemClock(), contactResolver);
    }

    public CommandParser(Clock clock, ContactResolver contactResolver) {
        if (clock == null) {
            throw new IllegalArgumentException("clock must not be null");
        }
        this.clock = clock;
        this.contactResolver = contactResolver;
    }

    public ParseResult parse(String input) {
        if (input == null || input.length() == 0) {
            return error(ErrorCode.EMPTY_INPUT, "Enter a command beginning with !.", "!help");
        }
        if (input.charAt(0) != '!') {
            return error(ErrorCode.NOT_A_COMMAND, "Commands must begin with !.", "!help");
        }

        String body = input.substring(1).trim();
        if (body.length() == 0) {
            return error(ErrorCode.MISSING_ARGUMENT, "Specify a command after !.", "!help");
        }

        Parts parts = splitFirstWord(body);
        String name = parts.first.toLowerCase(Locale.ROOT);
        if ("call".equals(name)) {
            return parseCall(parts.rest);
        }
        if ("text".equals(name)) {
            return parseText(parts.rest);
        }
        if ("timer".equals(name)) {
            return parseTimer(parts.rest);
        }
        if ("note".equals(name)) {
            return parseNote(parts.rest);
        }
        if ("todo".equals(name)) {
            return parseTodo(parts.rest);
        }
        if ("todos".equals(name)) {
            return parseNoArguments(parts.rest, Type.TODOS, "!todos");
        }
        if ("notes".equals(name)) {
            return parseNoArguments(parts.rest, Type.NOTES, "!notes");
        }
        if ("event".equals(name)) {
            return parseEvent(parts.rest);
        }
        if ("torch".equals(name)) {
            return parseNoArguments(parts.rest, Type.TORCH, "!torch");
        }
        if ("camera".equals(name)) {
            return parseNoArguments(parts.rest, Type.CAMERA, "!camera");
        }
        if ("help".equals(name)) {
            return parseNoArguments(parts.rest, Type.HELP, "!help");
        }
        return error(ErrorCode.UNKNOWN_COMMAND, "Unknown command: " + parts.first + ".", "!help");
    }

    /**
     * Returns a canonical 7-to-15-digit number, retaining one leading {@code +}, or {@code null}
     * when the input is not a valid raw phone number.
     */
    public static String normalizePhoneNumber(String value) {
        PhoneNumberParse parse = parsePhoneNumber(value);
        return parse.state == PhoneNumberState.VALID ? parse.number : null;
    }

    private ParseResult parseCall(String arguments) {
        if (arguments.length() == 0) {
            return missing("!call <contact-or-number>");
        }

        PhoneNumberParse phone = parsePhoneNumber(arguments);
        if (phone.state == PhoneNumberState.VALID) {
            return ParseResult.command(new CallCommand(Recipient.phoneNumber(phone.number)));
        }
        if (phone.state == PhoneNumberState.INVALID) {
            return error(ErrorCode.INVALID_RECIPIENT,
                    "Phone numbers must contain 7 to 15 digits and an optional leading +.",
                    "!call <contact-or-number>");
        }

        ContactMatch match = resolveContact(arguments);
        if (match == null) {
            // A caller can resolve this prefix later without making parser behavior depend on Contacts.
            return ParseResult.command(new CallCommand(Recipient.contactPrefix(arguments)));
        }
        ParseError matchError = validateContactMatch(match, arguments);
        if (matchError != null) {
            return ParseResult.error(matchError);
        }
        if (arguments.substring(match.getPrefixLength()).trim().length() != 0) {
            return error(ErrorCode.INVALID_RECIPIENT,
                    "A call recipient cannot include text after the contact name.",
                    "!call <contact-or-number>");
        }
        return ParseResult.command(new CallCommand(
                Recipient.contactPrefix(arguments.substring(0, match.getPrefixLength()).trim())));
    }

    private ParseResult parseText(String arguments) {
        if (arguments.length() == 0) {
            return missing("!text <contact-or-number> <message>");
        }

        PhoneNumberParse phone = parsePhonePrefix(arguments);
        if (phone.state == PhoneNumberState.VALID) {
            String message = arguments.substring(phone.consumedCharacters).trim();
            if (message.length() == 0) {
                return missing("!text <contact-or-number> <message>");
            }
            return ParseResult.command(new TextCommand(Recipient.phoneNumber(phone.number), message));
        }
        if (phone.state == PhoneNumberState.INVALID) {
            return error(ErrorCode.INVALID_RECIPIENT,
                    "Phone numbers must contain 7 to 15 digits and an optional leading +.",
                    "!text <contact-or-number> <message>");
        }

        if (contactResolver == null) {
            return error(ErrorCode.CONTACT_RESOLUTION_REQUIRED,
                    "A contact resolver is required for a named text recipient.",
                    "!text <contact-or-number> <message>");
        }
        ContactMatch match = resolveContact(arguments);
        if (match == null) {
            return error(ErrorCode.INVALID_RECIPIENT, "No contact matches the text recipient.",
                    "!text <contact-or-number> <message>");
        }
        ParseError matchError = validateContactMatch(match, arguments);
        if (matchError != null) {
            return ParseResult.error(matchError);
        }
        String message = arguments.substring(match.getPrefixLength()).trim();
        if (message.length() == 0) {
            return missing("!text <contact-or-number> <message>");
        }
        return ParseResult.command(new TextCommand(
                Recipient.contactPrefix(arguments.substring(0, match.getPrefixLength()).trim()), message));
    }

    private ParseResult parseTimer(String arguments) {
        if (arguments.length() == 0) {
            return missing("!timer <duration> [label]");
        }

        Parts parts = splitFirstWord(arguments);
        Matcher matcher = DURATION_PATTERN.matcher(parts.first);
        if (!matcher.matches() || (matcher.group(1) == null && matcher.group(2) == null)) {
            return error(ErrorCode.INVALID_DURATION, "Use durations such as 10m, 1h, or 1h30m.",
                    "!timer <duration> [label]");
        }

        long hours = parseLong(matcher.group(1));
        long minutes = parseLong(matcher.group(2));
        if (hours < 0 || minutes < 0 || hours > (Integer.MAX_VALUE - minutes) / 60) {
            return error(ErrorCode.INVALID_DURATION, "Timer duration is too large.",
                    "!timer <duration> [label]");
        }
        int totalMinutes = (int) (hours * 60 + minutes);
        if (totalMinutes <= 0) {
            return error(ErrorCode.INVALID_DURATION, "Timer duration must be greater than zero.",
                    "!timer <duration> [label]");
        }
        if (totalMinutes > Integer.MAX_VALUE / 60) {
            return error(ErrorCode.INVALID_DURATION, "Timer duration is too large.",
                    "!timer <duration> [label]");
        }
        return ParseResult.command(new TimerCommand(totalMinutes, parts.rest));
    }

    private ParseResult parseNote(String arguments) {
        return arguments.length() == 0 ? missing("!note <text>")
                : ParseResult.command(new NoteCommand(arguments));
    }

    private ParseResult parseTodo(String arguments) {
        return arguments.length() == 0 ? missing("!todo <text>")
                : ParseResult.command(new TodoCommand(arguments));
    }

    private ParseResult parseEvent(String arguments) {
        if (arguments.length() == 0) {
            return missing("!event <date> <time> <title>");
        }
        Parts datePart = splitFirstWord(arguments);
        if (datePart.rest.length() == 0) {
            return missing("!event <date> <time> <title>");
        }
        Parts timePart = splitFirstWord(datePart.rest);
        if (timePart.rest.length() == 0) {
            return missing("!event <date> <time> <title>");
        }

        TimeZone timeZone = clock.timeZone();
        if (timeZone == null) {
            throw new IllegalStateException("clock returned a null time zone");
        }
        timeZone = (TimeZone) timeZone.clone();
        long now = clock.currentTimeMillis();
        DateParts date = parseEventDate(datePart.first, now, timeZone);
        if (date == null) {
            return error(ErrorCode.INVALID_DATE,
                    "Use today, tomorrow, or a valid YYYY-MM-DD date.",
                    "!event <date> <time> <title>");
        }
        if (!TIME_PATTERN.matcher(timePart.first).matches()) {
            return error(ErrorCode.INVALID_TIME, "Use a 24-hour HH:mm time.",
                    "!event <date> <time> <title>");
        }

        int hour = Integer.parseInt(timePart.first.substring(0, 2));
        int minute = Integer.parseInt(timePart.first.substring(3, 5));
        Calendar start = newCalendar(timeZone);
        start.clear();
        start.set(date.year, date.month - 1, date.day, hour, minute, 0);
        long startMillis = start.getTimeInMillis();
        if (startMillis <= now) {
            return error(ErrorCode.PAST_EVENT, "Event start time must be in the future.",
                    "!event <date> <time> <title>");
        }
        return ParseResult.command(new EventCommand(date.toIsoDate(), timePart.first, timePart.rest,
                startMillis, DEFAULT_EVENT_DURATION_MINUTES, timeZone.getID()));
    }

    private ParseResult parseNoArguments(String arguments, Type type, String syntax) {
        if (arguments.length() != 0) {
            return error(ErrorCode.UNEXPECTED_ARGUMENT, syntax + " does not take arguments.", syntax);
        }
        if (type == Type.HELP) {
            return ParseResult.command(new HelpCommand());
        }
        if (type == Type.TORCH) {
            return ParseResult.command(new TorchCommand());
        }
        if (type == Type.NOTES) {
            return ParseResult.command(new NotesCommand());
        }
        if (type == Type.TODOS) {
            return ParseResult.command(new TodosCommand());
        }
        return ParseResult.command(new CameraCommand());
    }

    private ContactMatch resolveContact(String input) {
        return contactResolver == null ? null : contactResolver.resolveLongestPrefix(input);
    }

    private static ParseError validateContactMatch(ContactMatch match, String input) {
        if (match.getPrefixLength() > input.length()) {
            return new ParseError(ErrorCode.INVALID_RECIPIENT,
                    "Contact resolver returned a prefix beyond the recipient.", "!call <contact-or-number>");
        }
        if (match.getPrefixLength() < input.length()
                && !Character.isWhitespace(input.charAt(match.getPrefixLength()))) {
            return new ParseError(ErrorCode.INVALID_RECIPIENT,
                    "Contact resolver must match a complete recipient prefix.", "!call <contact-or-number>");
        }
        return null;
    }

    private static ParseResult missing(String syntax) {
        return error(ErrorCode.MISSING_ARGUMENT, "Use " + syntax + ".", syntax);
    }

    private static ParseResult error(ErrorCode code, String message, String syntax) {
        return ParseResult.error(new ParseError(code, message, syntax));
    }

    private static Parts splitFirstWord(String value) {
        int index = 0;
        while (index < value.length() && !Character.isWhitespace(value.charAt(index))) {
            index++;
        }
        String first = value.substring(0, index);
        return new Parts(first, value.substring(index).trim());
    }

    private static PhoneNumberParse parsePhonePrefix(String value) {
        int end = 0;
        while (end < value.length() && isPhoneCharacter(value.charAt(end))) {
            end++;
        }
        if (end == 0) {
            return PhoneNumberParse.notNumber();
        }
        PhoneNumberParse parse = parsePhoneNumber(value.substring(0, end).trim());
        return new PhoneNumberParse(parse.state, parse.number, end);
    }

    private static PhoneNumberParse parsePhoneNumber(String value) {
        if (value == null) {
            return PhoneNumberParse.notNumber();
        }
        String input = value.trim();
        if (input.length() == 0) {
            return PhoneNumberParse.notNumber();
        }

        StringBuilder digits = new StringBuilder();
        boolean plus = false;
        for (int index = 0; index < input.length(); index++) {
            char character = input.charAt(index);
            if (Character.isDigit(character)) {
                digits.append(character);
            } else if (character == '+') {
                if (index != 0 || plus) {
                    return PhoneNumberParse.invalid();
                }
                plus = true;
            } else if (!isPhoneCharacter(character)) {
                return PhoneNumberParse.notNumber();
            }
        }
        if (digits.length() < 7 || digits.length() > 15) {
            return PhoneNumberParse.invalid();
        }
        return PhoneNumberParse.valid((plus ? "+" : "") + digits.toString());
    }

    private static boolean isPhoneCharacter(char character) {
        return Character.isDigit(character) || character == '+' || character == '('
                || character == ')' || character == '-' || Character.isWhitespace(character);
    }

    private static long parseLong(String value) {
        if (value == null) {
            return 0;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            return -1;
        }
    }

    private static DateParts parseEventDate(String value, long now, TimeZone timeZone) {
        Calendar current = newCalendar(timeZone);
        current.setTimeInMillis(now);
        if ("today".equalsIgnoreCase(value) || "tomorrow".equalsIgnoreCase(value)) {
            if ("tomorrow".equalsIgnoreCase(value)) {
                current.add(Calendar.DAY_OF_YEAR, 1);
            }
            return new DateParts(current.get(Calendar.YEAR), current.get(Calendar.MONTH) + 1,
                    current.get(Calendar.DAY_OF_MONTH));
        }

        Matcher matcher = ISO_DATE_PATTERN.matcher(value);
        if (!matcher.matches()) {
            return null;
        }
        int year = Integer.parseInt(matcher.group(1));
        int month = Integer.parseInt(matcher.group(2));
        int day = Integer.parseInt(matcher.group(3));
        Calendar date = newCalendar(timeZone);
        date.clear();
        date.setLenient(false);
        date.set(year, month - 1, day, 0, 0, 0);
        try {
            date.getTimeInMillis();
            return new DateParts(year, month, day);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static Calendar newCalendar(TimeZone timeZone) {
        return Calendar.getInstance(timeZone, Locale.ROOT);
    }

    /** Injectable source of local wall-clock time used for event validation. */
    public interface Clock {
        long currentTimeMillis();

        TimeZone timeZone();
    }

    /** Resolves the longest contact-name prefix from a recipient-and-message input. */
    public interface ContactResolver {
        ContactMatch resolveLongestPrefix(String input);
    }

    public enum Type {
        CALL,
        TEXT,
        TIMER,
        NOTE,
        TODO,
        TODOS,
        NOTES,
        EVENT,
        TORCH,
        CAMERA,
        HELP
    }

    public enum ErrorCode {
        EMPTY_INPUT,
        NOT_A_COMMAND,
        UNKNOWN_COMMAND,
        MISSING_ARGUMENT,
        UNEXPECTED_ARGUMENT,
        INVALID_RECIPIENT,
        CONTACT_RESOLUTION_REQUIRED,
        INVALID_DURATION,
        INVALID_DATE,
        INVALID_TIME,
        PAST_EVENT
    }

    public interface Command {
        Type getType();
    }

    public static final class ParseResult {
        private final Command command;
        private final ParseError error;

        private ParseResult(Command command, ParseError error) {
            this.command = command;
            this.error = error;
        }

        public static ParseResult command(Command command) {
            if (command == null) {
                throw new IllegalArgumentException("command must not be null");
            }
            return new ParseResult(command, null);
        }

        public static ParseResult error(ParseError error) {
            if (error == null) {
                throw new IllegalArgumentException("error must not be null");
            }
            return new ParseResult(null, error);
        }

        public boolean isSuccess() {
            return command != null;
        }

        public Command getCommand() {
            return command;
        }

        public ParseError getError() {
            return error;
        }
    }

    public static final class ParseError {
        private final ErrorCode code;
        private final String message;
        private final String syntax;

        private ParseError(ErrorCode code, String message, String syntax) {
            this.code = code;
            this.message = message;
            this.syntax = syntax;
        }

        public ErrorCode getCode() {
            return code;
        }

        public String getMessage() {
            return message;
        }

        public String getSyntax() {
            return syntax;
        }
    }

    public static final class Recipient {
        public enum Kind {
            PHONE_NUMBER,
            CONTACT,
            CONTACT_PREFIX
        }

        private final Kind kind;
        private final String value;

        private Recipient(Kind kind, String value) {
            this.kind = kind;
            this.value = value;
        }

        public static Recipient phoneNumber(String value) {
            return new Recipient(Kind.PHONE_NUMBER, value);
        }

        public static Recipient contact(String value) {
            return new Recipient(Kind.CONTACT, value);
        }

        public static Recipient contactPrefix(String value) {
            return new Recipient(Kind.CONTACT_PREFIX, value);
        }

        public Kind getKind() {
            return kind;
        }

        public String getValue() {
            return value;
        }
    }

    public static final class ContactMatch {
        private final String displayName;
        private final int prefixLength;

        public ContactMatch(String displayName, int prefixLength) {
            if (displayName == null || displayName.trim().length() == 0) {
                throw new IllegalArgumentException("displayName must not be blank");
            }
            if (prefixLength <= 0) {
                throw new IllegalArgumentException("prefixLength must be greater than zero");
            }
            this.displayName = displayName;
            this.prefixLength = prefixLength;
        }

        public String getDisplayName() {
            return displayName;
        }

        public int getPrefixLength() {
            return prefixLength;
        }
    }

    public static final class CallCommand implements Command {
        private final Recipient recipient;

        private CallCommand(Recipient recipient) {
            this.recipient = recipient;
        }

        @Override
        public Type getType() {
            return Type.CALL;
        }

        public Recipient getRecipient() {
            return recipient;
        }
    }

    public static final class TextCommand implements Command {
        private final Recipient recipient;
        private final String message;

        private TextCommand(Recipient recipient, String message) {
            this.recipient = recipient;
            this.message = message;
        }

        @Override
        public Type getType() {
            return Type.TEXT;
        }

        public Recipient getRecipient() {
            return recipient;
        }

        public String getMessage() {
            return message;
        }
    }

    public static final class TimerCommand implements Command {
        private final int durationMinutes;
        private final String label;

        private TimerCommand(int durationMinutes, String label) {
            this.durationMinutes = durationMinutes;
            this.label = label;
        }

        @Override
        public Type getType() {
            return Type.TIMER;
        }

        public int getDurationMinutes() {
            return durationMinutes;
        }

        public String getLabel() {
            return label;
        }
    }

    public static final class NoteCommand implements Command {
        private final String text;

        private NoteCommand(String text) {
            this.text = text;
        }

        @Override
        public Type getType() {
            return Type.NOTE;
        }

        public String getText() {
            return text;
        }
    }

    public static final class TodoCommand implements Command {
        private final String text;

        private TodoCommand(String text) {
            this.text = text;
        }

        @Override
        public Type getType() {
            return Type.TODO;
        }

        public String getText() {
            return text;
        }
    }

    public static final class TodosCommand implements Command {
        private TodosCommand() {
        }

        @Override
        public Type getType() {
            return Type.TODOS;
        }
    }

    public static final class NotesCommand implements Command {
        private NotesCommand() {
        }

        @Override
        public Type getType() {
            return Type.NOTES;
        }
    }

    public static final class EventCommand implements Command {
        private final String date;
        private final String time;
        private final String title;
        private final long startTimeMillis;
        private final int durationMinutes;
        private final String timeZoneId;

        private EventCommand(String date, String time, String title, long startTimeMillis,
                             int durationMinutes, String timeZoneId) {
            this.date = date;
            this.time = time;
            this.title = title;
            this.startTimeMillis = startTimeMillis;
            this.durationMinutes = durationMinutes;
            this.timeZoneId = timeZoneId;
        }

        @Override
        public Type getType() {
            return Type.EVENT;
        }

        public String getDate() {
            return date;
        }

        public String getTime() {
            return time;
        }

        public String getTitle() {
            return title;
        }

        public long getStartTimeMillis() {
            return startTimeMillis;
        }

        public int getDurationMinutes() {
            return durationMinutes;
        }

        public String getTimeZoneId() {
            return timeZoneId;
        }
    }

    public static final class TorchCommand implements Command {
        private TorchCommand() {
        }

        @Override
        public Type getType() {
            return Type.TORCH;
        }
    }

    public static final class CameraCommand implements Command {
        private CameraCommand() {
        }

        @Override
        public Type getType() {
            return Type.CAMERA;
        }
    }

    public static final class HelpCommand implements Command {
        private HelpCommand() {
        }

        @Override
        public Type getType() {
            return Type.HELP;
        }
    }

    private static final class Parts {
        private final String first;
        private final String rest;

        private Parts(String first, String rest) {
            this.first = first;
            this.rest = rest;
        }
    }

    private enum PhoneNumberState {
        VALID,
        INVALID,
        NOT_A_NUMBER
    }

    private static final class PhoneNumberParse {
        private final PhoneNumberState state;
        private final String number;
        private final int consumedCharacters;

        private PhoneNumberParse(PhoneNumberState state, String number, int consumedCharacters) {
            this.state = state;
            this.number = number;
            this.consumedCharacters = consumedCharacters;
        }

        private static PhoneNumberParse valid(String number) {
            return new PhoneNumberParse(PhoneNumberState.VALID, number, 0);
        }

        private static PhoneNumberParse invalid() {
            return new PhoneNumberParse(PhoneNumberState.INVALID, null, 0);
        }

        private static PhoneNumberParse notNumber() {
            return new PhoneNumberParse(PhoneNumberState.NOT_A_NUMBER, null, 0);
        }
    }

    private static final class DateParts {
        private final int year;
        private final int month;
        private final int day;

        private DateParts(int year, int month, int day) {
            this.year = year;
            this.month = month;
            this.day = day;
        }

        private String toIsoDate() {
            return String.format(Locale.ROOT, "%04d-%02d-%02d", year, month, day);
        }
    }

    private static final class SystemClock implements Clock {
        @Override
        public long currentTimeMillis() {
            return System.currentTimeMillis();
        }

        @Override
        public TimeZone timeZone() {
            return TimeZone.getDefault();
        }
    }
}
