package me.pompel.elauncher;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Maps model JSON into the existing canonical command syntax. */
public final class LlmOutputMapper {
    private LlmOutputMapper() {
    }

    public static String toCommandString(String raw) {
        Map<String, String> fields = parseObject(extractObject(raw));
        if (fields == null) {
            return null;
        }
        String command = value(fields, "command");
        if (command == null) {
            return null;
        }
        command = command.toLowerCase(Locale.ROOT);
        switch (command) {
            case "call":
                return withSlot("call", fields, "contact");
            case "text":
                return withSlots("text", fields, "contact", "body");
            case "hermes":
                return withSlot("hermes", fields, "body");
            case "timer":
                return withSlot("timer", fields, "duration");
            case "alarm":
                return withSlot("alarm", fields, "time");
            case "todo":
                return withSlot("todo", fields, "text");
            case "todos":
                return "!todos";
            case "note":
                return withSlot("note", fields, "text");
            case "notes":
                return "!notes";
            case "grocery":
                return withSlot("grocery", fields, "item");
            case "groceries":
                return "!groceries";
            case "event":
                return withSlots("event", fields, "date", "time", "title");
            case "torch":
                return "!t";
            case "camera":
                return "!camera";
            default:
                return null;
        }
    }

    private static String withSlot(String command, Map<String, String> fields, String slot) {
        String value = value(fields, slot);
        return value == null ? null : "!" + command + " " + value;
    }

    private static String withSlots(String command, Map<String, String> fields, String... slots) {
        StringBuilder result = new StringBuilder("!").append(command);
        for (String slot : slots) {
            String value = value(fields, slot);
            if (value == null) {
                return null;
            }
            result.append(' ').append(value);
        }
        return result.toString();
    }

    private static String value(Map<String, String> fields, String key) {
        String value = fields.get(key);
        if (value == null || value.trim().length() == 0) {
            return null;
        }
        return value.trim();
    }

    private static String extractObject(String raw) {
        if (raw == null) {
            return null;
        }
        int start = raw.indexOf('{');
        if (start < 0) {
            return null;
        }
        char quote = 0;
        boolean escaped = false;
        int depth = 0;
        for (int index = start; index < raw.length(); index++) {
            char current = raw.charAt(index);
            if (quote != 0) {
                if (escaped) {
                    escaped = false;
                } else if (current == '\\') {
                    escaped = true;
                } else if (current == quote && !(quote == '\'' && index + 1 < raw.length()
                        && Character.isLetter(raw.charAt(index + 1)))) {
                    quote = 0;
                }
                continue;
            }
            if (current == '\'' || current == '"') {
                quote = current;
            } else if (current == '{') {
                depth++;
            } else if (current == '}' && --depth == 0) {
                return raw.substring(start, index + 1);
            }
        }
        return null;
    }

    private static Map<String, String> parseObject(String object) {
        if (object == null) {
            return null;
        }
        Map<String, String> fields = new HashMap<>();
        int[] cursor = {1};
        while (true) {
            skipWhitespace(object, cursor);
            if (cursor[0] >= object.length()) {
                return null;
            }
            if (object.charAt(cursor[0]) == '}') {
                return fields;
            }
            String key = readQuoted(object, cursor);
            if (key == null) {
                return null;
            }
            skipWhitespace(object, cursor);
            if (cursor[0] >= object.length() || object.charAt(cursor[0]++) != ':') {
                return null;
            }
            skipWhitespace(object, cursor);
            String parsedValue = cursor[0] < object.length()
                    && (object.charAt(cursor[0]) == '\'' || object.charAt(cursor[0]) == '"')
                    ? readQuoted(object, cursor) : readBare(object, cursor);
            if (parsedValue == null) {
                return null;
            }
            fields.put(key, "null".equals(parsedValue) ? null : parsedValue);
            skipWhitespace(object, cursor);
            if (cursor[0] >= object.length()) {
                return null;
            }
            char separator = object.charAt(cursor[0]++);
            if (separator == '}') {
                return fields;
            }
            if (separator != ',') {
                return null;
            }
        }
    }

    private static String readQuoted(String text, int[] cursor) {
        if (cursor[0] >= text.length()) {
            return null;
        }
        char quote = text.charAt(cursor[0]++);
        if (quote != '\'' && quote != '"') {
            return null;
        }
        StringBuilder value = new StringBuilder();
        while (cursor[0] < text.length()) {
            char current = text.charAt(cursor[0]++);
            if (current == quote) {
                if (quote == '\'' && cursor[0] < text.length()
                        && Character.isLetter(text.charAt(cursor[0]))) {
                    value.append(current);
                    continue;
                }
                return value.toString();
            }
            if (current == '\\') {
                if (cursor[0] >= text.length()) {
                    return null;
                }
                char escaped = text.charAt(cursor[0]++);
                switch (escaped) {
                    case 'n': value.append('\n'); break;
                    case 'r': value.append('\r'); break;
                    case 't': value.append('\t'); break;
                    case 'u':
                        if (cursor[0] + 4 > text.length()) {
                            return null;
                        }
                        try {
                            value.append((char) Integer.parseInt(text.substring(cursor[0], cursor[0] + 4), 16));
                        } catch (NumberFormatException exception) {
                            return null;
                        }
                        cursor[0] += 4;
                        break;
                    default: value.append(escaped);
                }
            } else {
                value.append(current);
            }
        }
        return null;
    }

    private static String readBare(String text, int[] cursor) {
        int start = cursor[0];
        while (cursor[0] < text.length()
                && text.charAt(cursor[0]) != ',' && text.charAt(cursor[0]) != '}') {
            cursor[0]++;
        }
        String value = text.substring(start, cursor[0]).trim();
        return value.length() == 0 ? null : value;
    }

    private static void skipWhitespace(String text, int[] cursor) {
        while (cursor[0] < text.length() && Character.isWhitespace(text.charAt(cursor[0]))) {
            cursor[0]++;
        }
    }
}
