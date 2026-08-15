package me.pompel.elauncher;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class CommandContractFixtureTest {
    private static final String RESOURCE = "me/pompel/elauncher/command-cases.tsv";
    private static final String[] HEADER = {
            "id", "input", "trigger", "context", "mode", "state", "command",
            "normalized_arguments", "expected_effect", "expected_message"
    };
    private static final Set<String> TRIGGERS = values(
            "INPUT_CHANGED", "SUBMIT", "LIST_OPENED", "LIST_ACTION", "BACK"
    );
    private static final Set<String> CONTEXTS = values(
            "DEFAULT", "CONTACTS_GRANTED_SINGLE", "CONTACTS_GRANTED_AMBIGUOUS",
            "CONTACTS_GRANTED_MULTIPLE_NUMBERS", "CONTACTS_GRANTED_NONE", "CONTACTS_DENIED",
            "DIALER_UNAVAILABLE", "SMS_UNAVAILABLE", "CLOCK_UNAVAILABLE", "PAST_EVENT",
            "FUTURE_EVENT", "CALENDAR_UNAVAILABLE", "TORCH_AVAILABLE", "TORCH_UNAVAILABLE",
            "TORCH_ON", "TORCH_OFF", "CAMERA_DENIED", "CAMERA_UNAVAILABLE", "TODOS_EXIST",
            "TODO_ITEM_EXISTS", "TODOS_OPEN", "NOTES_EXIST", "NOTE_ITEM_EXISTS", "NOTES_OPEN"
    );
    private static final Set<String> MODES = values("APP_SEARCH", "COMMAND_SEARCH");
    private static final Set<String> STATES = values(
            "APP_RESULTS", "COMMAND_HELP", "SUGGESTION", "PREVIEW", "VALIDATION_ERROR",
            "UNKNOWN_COMMAND", "CONTACT_CHOICE", "PERMISSION_DENIED", "UNAVAILABLE",
            "CONFIRMATION", "SUCCESS"
    );
    private static final Set<String> COMMANDS = values(
            "-", "HELP", "CALL", "TEXT", "TIMER", "TODO", "TODOS", "NOTE", "NOTES",
            "EVENT", "TORCH", "CAMERA", "UNKNOWN"
    );
    private static final Set<String> EFFECTS = values(
            "NONE", "FILTER_APPS", "SHOW_HELP", "SHOW_SYNTAX", "SHOW_CONTACT_CHOICES",
            "OFFER_SETTINGS", "SHOW_UNAVAILABLE", "OPEN_DIALER", "OPEN_SMS_COMPOSER",
            "OPEN_TIMER", "SAVE_TODO", "OPEN_TODOS", "SAVE_NOTE", "OPEN_NOTES",
            "OPEN_CALENDAR", "TOGGLE_TORCH", "OPEN_CAMERA", "COMPLETE_TODO", "DELETE_TODO",
            "DELETE_NOTE", "RETURN_TO_LAUNCHER"
    );
    private static final Set<String> DISPLAY_EFFECTS = values(
            "NONE", "SHOW_HELP", "SHOW_SYNTAX", "SHOW_CONTACT_CHOICES", "OFFER_SETTINGS",
            "SHOW_UNAVAILABLE"
    );

    @Test
    public void commandContractFixtureHasAStableSchemaAndRoutingRules() throws IOException {
        InputStream inputStream = getClass().getClassLoader().getResourceAsStream(RESOURCE);
        assertNotNull("Missing command contract fixture", inputStream);

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String header = reader.readLine();
            assertNotNull("Fixture must include a header", header);
            assertArrayEquals(HEADER, header.split("\\t", -1));

            Set<String> caseIds = new HashSet<>();
            Set<String> rawRecipientFallbacks = new HashSet<>();
            Set<String> listBehaviors = new HashSet<>();
            boolean hasTorchTypingCase = false;
            boolean hasTorchOnCase = false;
            boolean hasTorchOffCase = false;
            boolean hasOneHourTimerCase = false;
            boolean hasIsoDateCase = false;
            boolean hasMinimumLengthNumberCase = false;
            boolean hasMaximumLengthNumberCase = false;
            boolean hasCaseInsensitiveContactCase = false;
            String line;
            int lineNumber = 1;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (line.trim().isEmpty()) {
                    continue;
                }

                String[] fields = line.split("\\t", -1);
                assertEquals("Unexpected column count at line " + lineNumber, HEADER.length, fields.length);
                for (int index = 0; index < fields.length; index++) {
                    assertFalse(
                            "Blank " + HEADER[index] + " at line " + lineNumber,
                            fields[index].trim().isEmpty()
                    );
                }

                assertTrue("Duplicate case id at line " + lineNumber, caseIds.add(fields[0]));
                assertTrue("Unknown trigger at line " + lineNumber, TRIGGERS.contains(fields[2]));
                assertTrue("Unknown context at line " + lineNumber, CONTEXTS.contains(fields[3]));
                assertTrue("Unknown mode at line " + lineNumber, MODES.contains(fields[4]));
                assertTrue("Unknown state at line " + lineNumber, STATES.contains(fields[5]));
                assertTrue("Unknown command at line " + lineNumber, COMMANDS.contains(fields[6]));
                assertTrue("Unknown effect at line " + lineNumber, EFFECTS.contains(fields[8]));

                String input = "<empty>".equals(fields[1]) ? "" : fields[1];
                String expectedMode = input.isEmpty() || input.charAt(0) != '!'
                        ? "APP_SEARCH" : "COMMAND_SEARCH";
                assertEquals("Incorrect first-character routing at line " + lineNumber, expectedMode, fields[4]);

                if ("APP_SEARCH".equals(fields[4])) {
                    assertEquals("App search must render app rows at line " + lineNumber,
                            "APP_RESULTS", fields[5]);
                    assertEquals("App search must use the app filter at line " + lineNumber,
                            "FILTER_APPS", fields[8]);
                } else {
                    assertFalse("Command mode must never use the app filter at line " + lineNumber,
                            "FILTER_APPS".equals(fields[8]));
                }

                if ("COMMAND_SEARCH".equals(fields[4]) && "INPUT_CHANGED".equals(fields[2])) {
                    assertTrue("Typing a command must not execute it at line " + lineNumber,
                            DISPLAY_EFFECTS.contains(fields[8]));
                }

                if ("EVENT".equals(fields[6])) {
                    assertTrue("Events must use local time at line " + lineNumber,
                            fields[7].contains("timezone=local"));
                    if ("FUTURE_EVENT".equals(fields[3]) && fields[7].contains("date=2026-08-16")) {
                        hasIsoDateCase = true;
                    }
                }

                if ("TIMER".equals(fields[6]) && fields[7].startsWith("duration=60m")) {
                    hasOneHourTimerCase = true;
                }

                if ("CALL".equals(fields[6])
                        && "CONTACTS_GRANTED_SINGLE".equals(fields[3])
                        && "!call ADA lovelace".equals(fields[1])) {
                    assertEquals("Contact matching must be case-insensitive at line " + lineNumber,
                            "recipient=Ada Lovelace", fields[7]);
                    hasCaseInsensitiveContactCase = true;
                }

                if (isRawRecipient(fields[7])) {
                    int digitCount = rawRecipientDigitCount(fields[7]);
                    hasMinimumLengthNumberCase |= digitCount == 7;
                    hasMaximumLengthNumberCase |= digitCount == 15;
                }

                if ("CONTACTS_DENIED".equals(fields[3]) && isRawRecipient(fields[7])) {
                    if ("INPUT_CHANGED".equals(fields[2])) {
                        assertEquals("Raw numbers must preview without Contacts permission at line " + lineNumber,
                                "PREVIEW", fields[5]);
                    } else {
                        assertEquals("Raw numbers must submit without Contacts permission at line " + lineNumber,
                                "CONFIRMATION", fields[5]);
                    }
                    rawRecipientFallbacks.add(
                            fields[6] + "_" + rawRecipientVariant(fields[7]) + "_" + fields[2]
                    );
                }

                if ("TORCH".equals(fields[6]) && "INPUT_CHANGED".equals(fields[2])) {
                    assertEquals("Typing !t must not toggle it at line " + lineNumber,
                            "PREVIEW", fields[5]);
                    assertEquals("Typing !t must not have a side effect at line " + lineNumber,
                            "NONE", fields[8]);
                    hasTorchTypingCase = true;
                }

                if ("TORCH".equals(fields[6]) && "TOGGLE_TORCH".equals(fields[8])) {
                    hasTorchOnCase |= "Report torch on".equals(fields[9]);
                    hasTorchOffCase |= "Report torch off".equals(fields[9]);
                }

                if ("NOTES".equals(fields[6])) {
                    if ("order=newest_first".equals(fields[7])) {
                        listBehaviors.add("NOTES_ORDER");
                    }
                    if ("DELETE_NOTE".equals(fields[8])) {
                        listBehaviors.add("NOTES_DELETE");
                    }
                    if ("RETURN_TO_LAUNCHER".equals(fields[8])) {
                        listBehaviors.add("NOTES_BACK");
                    }
                }

                if ("TODOS".equals(fields[6])) {
                    if ("order=incomplete_first".equals(fields[7])) {
                        listBehaviors.add("TODOS_ORDER");
                    }
                    if ("COMPLETE_TODO".equals(fields[8])) {
                        listBehaviors.add("TODOS_COMPLETE");
                    }
                    if ("DELETE_TODO".equals(fields[8])) {
                        listBehaviors.add("TODOS_DELETE");
                    }
                    if ("RETURN_TO_LAUNCHER".equals(fields[8])) {
                        listBehaviors.add("TODOS_BACK");
                    }
                }
            }

            assertFalse("Fixture must define at least one contract case", caseIds.isEmpty());
            assertEquals("Raw-number fallback must cover both number forms and commands", values(
                    "CALL_BARE_INPUT_CHANGED", "CALL_BARE_SUBMIT", "CALL_PLUS_INPUT_CHANGED",
                    "CALL_PLUS_SUBMIT", "TEXT_BARE_INPUT_CHANGED", "TEXT_BARE_SUBMIT",
                    "TEXT_PLUS_INPUT_CHANGED", "TEXT_PLUS_SUBMIT"
            ), rawRecipientFallbacks);
            assertTrue("Fixture must cover typing !t before submission", hasTorchTypingCase);
            assertTrue("Fixture must distinguish torch on", hasTorchOnCase);
            assertTrue("Fixture must distinguish torch off", hasTorchOffCase);
            assertTrue("Fixture must cover a one-hour timer", hasOneHourTimerCase);
            assertTrue("Fixture must cover a valid ISO event date", hasIsoDateCase);
            assertTrue("Fixture must cover a seven-digit number", hasMinimumLengthNumberCase);
            assertTrue("Fixture must cover a fifteen-digit number", hasMaximumLengthNumberCase);
            assertTrue("Fixture must cover case-insensitive contact matching", hasCaseInsensitiveContactCase);
            assertEquals("Fixture must cover notes and to-do list behavior", values(
                    "NOTES_ORDER", "NOTES_DELETE", "NOTES_BACK", "TODOS_ORDER", "TODOS_COMPLETE",
                    "TODOS_DELETE", "TODOS_BACK"
            ), listBehaviors);
        }
    }

    private static boolean isRawRecipient(String normalizedArguments) {
        return normalizedArguments.matches("^recipient=\\+?\\d{7,15}(;.*)?$");
    }

    private static String rawRecipientVariant(String normalizedArguments) {
        return normalizedArguments.startsWith("recipient=+") ? "PLUS" : "BARE";
    }

    private static int rawRecipientDigitCount(String normalizedArguments) {
        int separator = normalizedArguments.indexOf(';');
        String recipient = separator == -1 ? normalizedArguments : normalizedArguments.substring(0, separator);
        return recipient.replaceAll("\\D", "").length();
    }

    private static Set<String> values(String... values) {
        return new HashSet<>(Arrays.asList(values));
    }
}
