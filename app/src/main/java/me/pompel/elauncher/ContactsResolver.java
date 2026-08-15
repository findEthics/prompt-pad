package me.pompel.elauncher;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.provider.ContactsContract;

import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Reads phone contacts only after an explicitly submitted command. */
public final class ContactsResolver implements CommandParser.ContactResolver {
    private static final String[] PROJECTION = {
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NORMALIZED_NUMBER,
            ContactsContract.CommonDataKinds.Phone.NUMBER
    };

    private final Context context;

    public ContactsResolver(Context context) {
        this.context = context.getApplicationContext();
    }

    public boolean hasPermission() {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS)
                == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public CommandParser.ContactMatch resolveLongestPrefix(String input) {
        if (!hasPermission() || input == null) {
            return null;
        }

        int bestLength = -1;
        for (int end = input.length(); end > 0; end--) {
            if (end != input.length() && !Character.isWhitespace(input.charAt(end))) {
                continue;
            }
            String prefix = input.substring(0, end).trim();
            if (prefix.isEmpty()) {
                continue;
            }
            List<Contact> matches = matchingContacts(prefix);
            if (!matches.isEmpty()) {
                bestLength = prefix.length();
                break;
            }
        }
        return bestLength == -1 ? null
                : new CommandParser.ContactMatch(bestLength);
    }

    /** Returns every phone row for an exact contact name, or every matching name for a prefix. */
    public List<Contact> contactsFor(String query) {
        if (!hasPermission() || query == null || query.trim().isEmpty()) {
            return Collections.emptyList();
        }
        return matchingContacts(query.trim());
    }

    private List<Contact> matchingContacts(String query) {
        String normalizedQuery = query.toLowerCase(Locale.ROOT);
        List<Contact> prefixMatches = new ArrayList<>();
        List<Contact> exactMatches = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        try (Cursor cursor = context.getContentResolver().query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI, PROJECTION,
                null, null, null)) {
            if (cursor == null) {
                return Collections.emptyList();
            }
            while (cursor.moveToNext()) {
                String id = cursor.getString(0);
                String displayName = cursor.getString(1);
                String number = cursor.getString(2);
                if (number == null || number.trim().isEmpty()) {
                    number = cursor.getString(3);
                }
                String normalizedNumber = CommandParser.normalizePhoneNumber(number);
                if (id == null || displayName == null || normalizedNumber == null) {
                    continue;
                }
                String normalizedName = displayName.toLowerCase(Locale.ROOT);
                if (!normalizedName.startsWith(normalizedQuery)) {
                    continue;
                }
                Contact contact = new Contact(id, displayName, normalizedNumber);
                if (!seen.add(contact.getId() + "\u0000" + contact.getNumber())) {
                    continue;
                }
                prefixMatches.add(contact);
                if (normalizedName.equals(normalizedQuery)) {
                    exactMatches.add(contact);
                }
            }
        } catch (SecurityException ignored) {
            return Collections.emptyList();
        }
        List<Contact> result = exactMatches.isEmpty() ? prefixMatches : exactMatches;
        Collections.sort(result, new Comparator<Contact>() {
            @Override
            public int compare(Contact first, Contact second) {
                int names = first.displayName.compareToIgnoreCase(second.displayName);
                return names != 0 ? names : first.number.compareTo(second.number);
            }
        });
        return result;
    }

    public static final class Contact {
        private final String id;
        private final String displayName;
        private final String number;

        private Contact(String id, String displayName, String number) {
            this.id = id;
            this.displayName = displayName;
            this.number = number;
        }

        public String getId() {
            return id;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getNumber() {
            return number;
        }

        public String getLabel() {
            return displayName + "  " + number;
        }
    }
}
