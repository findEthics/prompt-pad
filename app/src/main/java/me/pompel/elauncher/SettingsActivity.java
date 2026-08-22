package me.pompel.elauncher;

import android.content.Intent;
import android.os.Bundle;
import android.view.GestureDetector;
import android.view.MotionEvent;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.EditTextPreference;
import android.widget.Toast;

public class SettingsActivity extends AppCompatActivity {

    private GestureDetector gestureDetector;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ThemePreference.apply(this);

        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.settings_activity);
        android.view.View settingsRoot = findViewById(R.id.settings_root);
        ViewCompat.setOnApplyWindowInsetsListener(settingsRoot, (view, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout() | WindowInsetsCompat.Type.ime());
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
        ViewCompat.requestApplyInsets(settingsRoot);
        if (savedInstanceState == null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.settings, new SettingsFragment())
                    .commit();
        }
        gestureDetector = new GestureDetector(this, new GestureListener());
    }

    private class GestureListener extends GestureDetector.SimpleOnGestureListener {
        private static final int SWIPE_THRESHOLD = 100;
        private static final int SWIPE_VELOCITY_THRESHOLD = 100;

        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
            assert e1 != null;
            float diffY = e2.getY() - e1.getY();
            float diffX = e2.getX() - e1.getX();
            if (Math.abs(diffY) > Math.abs(diffX)) {
                if (diffY < 0 && Math.abs(diffY) > SWIPE_THRESHOLD && Math.abs(velocityY) > SWIPE_VELOCITY_THRESHOLD) {
                    restartApplication();
                    return true;
                }
            }
            return false;
        }

        @Override
        public void onLongPress(@NonNull MotionEvent e) {
            restartApplication();
        }
    }

    private void restartApplication() {
        Intent intent = new Intent(SettingsActivity.this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finishAffinity(); // Close all activities
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        gestureDetector.onTouchEvent(ev);
        return super.dispatchTouchEvent(ev);
    }

    public static class SettingsFragment extends PreferenceFragmentCompat {
        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.root_preferences, rootKey);
            
            // Initialize dark mode switch to show current system state if not explicitly set
            androidx.preference.SwitchPreferenceCompat darkModePreference = 
                findPreference("dark_mode_preference");
            if (darkModePreference != null) {
                android.content.SharedPreferences prefs = 
                    androidx.preference.PreferenceManager.getDefaultSharedPreferences(requireContext());
                
                // Only set the visual state if the preference hasn't been explicitly set
                if (!prefs.contains("dark_mode_preference")) {
                    darkModePreference.setChecked(ThemePreference.isDarkMode(requireContext()));
                }
            }

            androidx.preference.SwitchPreferenceCompat keyboardPreference =
                    findPreference(MainActivity.HAS_KEYBOARD_PREFERENCE);
            if (keyboardPreference != null) {
                android.content.SharedPreferences prefs =
                        androidx.preference.PreferenceManager.getDefaultSharedPreferences(requireContext());
                if (!prefs.contains(MainActivity.HAS_KEYBOARD_PREFERENCE)) {
                    keyboardPreference.setChecked(MainActivity.hasHardwareKeyboard(requireContext()));
                    prefs.edit().remove(MainActivity.HAS_KEYBOARD_PREFERENCE).apply();
                }
            }

            EditTextPreference hermesUsername = findPreference("hermes_username_preference");
            if (hermesUsername != null) {
                hermesUsername.setOnPreferenceChangeListener((preference, newValue) -> {
                    String raw = newValue == null ? "" : newValue.toString();
                    String input = raw.trim();
                    if (input.isEmpty()) {
                        if (!raw.isEmpty()) {
                            hermesUsername.setText("");
                            return false;
                        }
                        return true;
                    }
                    String normalized = CommandParser.normalizeTelegramUsername(input);
                    if (normalized == null) {
                        Toast.makeText(requireContext(), "Use a valid Telegram username.",
                                Toast.LENGTH_SHORT).show();
                        return false;
                    }
                    if (!normalized.equals(raw)) {
                        hermesUsername.setText(normalized);
                        return false;
                    }
                    return true;
                });
            }
        }

    }
}
