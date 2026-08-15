package me.pompel.elauncher;

import android.content.SharedPreferences;

/** Adapts Android SharedPreferences to the platform-neutral repository boundary. */
public final class SharedPreferencesKeyValueStore implements KeyValueStore {
    private final SharedPreferences preferences;

    public SharedPreferencesKeyValueStore(SharedPreferences preferences) {
        if (preferences == null) {
            throw new IllegalArgumentException("preferences must not be null");
        }
        this.preferences = preferences;
    }

    @Override
    public String getString(String key) {
        return preferences.getString(key, null);
    }

    @Override
    public void putString(String key, String value) {
        preferences.edit().putString(key, value).apply();
    }
}
