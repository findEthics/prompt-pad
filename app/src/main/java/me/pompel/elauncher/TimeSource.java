package me.pompel.elauncher;

/** Supplies creation times without coupling repositories to Android clocks. */
public interface TimeSource {
    long currentTimeMillis();
}
