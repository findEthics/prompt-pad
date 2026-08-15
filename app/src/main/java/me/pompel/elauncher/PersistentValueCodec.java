package me.pompel.elauncher;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Length-prefixed string codec that does not need a JSON or Android dependency. */
final class PersistentValueCodec {
    private PersistentValueCodec() {
    }

    static String encode(List<String[]> rows) {
        StringBuilder encoded = new StringBuilder();
        encoded.append(rows.size()).append('|');
        for (String[] row : rows) {
            for (String value : row) {
                encoded.append(value.length()).append(':').append(value);
            }
        }
        return encoded.toString();
    }

    static List<String[]> decode(String encoded, int fieldsPerRow) {
        if (encoded == null || encoded.isEmpty() || fieldsPerRow <= 0) {
            return Collections.emptyList();
        }

        try {
            int separator = encoded.indexOf('|');
            if (separator < 0) {
                return Collections.emptyList();
            }
            int rowCount = parseNonNegativeInt(encoded, 0, separator);
            int index = separator + 1;
            if (rowCount > (encoded.length() - index) / (2 * fieldsPerRow)) {
                return Collections.emptyList();
            }

            List<String[]> rows = new ArrayList<>(rowCount);
            for (int rowIndex = 0; rowIndex < rowCount; rowIndex++) {
                String[] row = new String[fieldsPerRow];
                for (int fieldIndex = 0; fieldIndex < fieldsPerRow; fieldIndex++) {
                    int colon = encoded.indexOf(':', index);
                    if (colon < index) {
                        return Collections.emptyList();
                    }
                    int length = parseNonNegativeInt(encoded, index, colon);
                    int valueStart = colon + 1;
                    int valueEnd = valueStart + length;
                    if (valueEnd < valueStart || valueEnd > encoded.length()) {
                        return Collections.emptyList();
                    }
                    row[fieldIndex] = encoded.substring(valueStart, valueEnd);
                    index = valueEnd;
                }
                rows.add(row);
            }
            return index == encoded.length() ? rows : Collections.<String[]>emptyList();
        } catch (NumberFormatException exception) {
            return Collections.emptyList();
        }
    }

    private static int parseNonNegativeInt(String value, int start, int end) {
        if (start >= end) {
            throw new NumberFormatException("empty number");
        }
        int parsed = Integer.parseInt(value.substring(start, end));
        if (parsed < 0) {
            throw new NumberFormatException("negative number");
        }
        return parsed;
    }
}
