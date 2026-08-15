package me.pompel.elauncher;

/** Minimal persistence boundary so repositories can run in a plain JVM. */
public interface KeyValueStore {
    String getString(String key);

    void putString(String key, String value);
}
