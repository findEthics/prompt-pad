package me.pompel.elauncher;

import android.content.Context;
import android.graphics.Typeface;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.content.res.ResourcesCompat;

/** Enforces Prompt-Pad's single dark theme and Katapult typography. */
final class ThemePreference {
    private ThemePreference() { }

    static void apply(AppCompatActivity activity) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        activity.setTheme(R.style.AppTheme);
    }

    static boolean isDarkMode(Context context) { return true; }

    static void applyTypography(View root, Context context) {
        applyTypeface(root, ResourcesCompat.getFont(context, R.font.lato));
    }

    private static void applyTypeface(View view, Typeface face) {
        if (view instanceof TextView) {
            TextView text = (TextView) view;
            text.setTypeface(face, text.getTypeface() == null
                    ? Typeface.NORMAL : text.getTypeface().getStyle());
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) applyTypeface(group.getChildAt(i), face);
        }
    }
}
