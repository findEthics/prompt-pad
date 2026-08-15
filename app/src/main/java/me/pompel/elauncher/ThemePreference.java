package me.pompel.elauncher;

import android.content.Context;
import android.content.res.Configuration;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.preference.PreferenceManager;

/** Applies the saved launcher theme before an Activity creates its UI. */
final class ThemePreference {
    private static final String DARK_MODE = "dark_mode_preference";

    private ThemePreference() {
    }

    static void apply(AppCompatActivity activity) {
        boolean darkMode = isDarkMode(activity);
        AppCompatDelegate.setDefaultNightMode(darkMode
                ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO);
        activity.setTheme(darkMode ? R.style.AppTheme_InvertedDark : R.style.AppTheme);
    }

    static boolean isDarkMode(Context context) {
        boolean systemDarkMode = (context.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        return PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean(DARK_MODE, systemDarkMode);
    }
}
