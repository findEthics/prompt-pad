package me.pompel.elauncher;

import java.util.HashMap;
import java.util.Map;

final class InMemoryKeyValueStore implements KeyValueStore {
    private final Map<String, String> values = new HashMap<>();

    @Override
    public String getString(String key) {
        return values.get(key);
    }

    @Override
    public void putString(String key, String value) {
        values.put(key, value);
    }
}
