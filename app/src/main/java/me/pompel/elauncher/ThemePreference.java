package me.pompel.elauncher;

import android.content.Context;
import android.graphics.Typeface;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.preference.PreferenceManager;

/** Enforces the launcher's dark-only visual profile and selected local font. */
final class ThemePreference {
    private ThemePreference() { }

    static void apply(AppCompatActivity activity) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        activity.setTheme(R.style.AppTheme);
    }

    static boolean isDarkMode(Context context) { return true; }

    static void applyTypography(View root, Context context) {
        String profile = PreferenceManager.getDefaultSharedPreferences(context)
                .getString("font_profile", "minimal");
        Typeface face;
        if ("readable".equals(profile)) face = Typeface.create("sans-serif", Typeface.NORMAL);
        else if ("mono".equals(profile)) face = Typeface.MONOSPACE;
        else face = Typeface.create("poppins", Typeface.NORMAL);
        applyTypeface(root, face);
    }

    private static void applyTypeface(View view, Typeface face) {
        if (view instanceof TextView) ((TextView) view).setTypeface(face,
                ((TextView) view).getTypeface() == null ? Typeface.NORMAL : ((TextView) view).getTypeface().getStyle());
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) applyTypeface(group.getChildAt(i), face);
        }
    }
}
