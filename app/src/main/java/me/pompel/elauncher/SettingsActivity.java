package me.pompel.elauncher;

import android.content.Intent;
import android.os.Bundle;
import android.view.GestureDetector;
import android.view.MotionEvent;

import androidx.annotation.NonNull;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.EditTextPreference;
import androidx.preference.SwitchPreferenceCompat;
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
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                restartApplication();
            }
        });
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
        private ModelDownloader modelDownloader;

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

            SwitchPreferenceCompat naturalLanguagePreference =
                    findPreference(MainActivity.NATURAL_LANGUAGE_PREFERENCE);
            if (naturalLanguagePreference != null
                    && !MediaPipeLlmInterpreter.isModelPresent(requireContext())) {
                android.content.SharedPreferences prefs =
                        androidx.preference.PreferenceManager.getDefaultSharedPreferences(requireContext());
                naturalLanguagePreference.setChecked(false);
                prefs.edit().putBoolean(MainActivity.NATURAL_LANGUAGE_PREFERENCE, false).apply();
                naturalLanguagePreference.setOnPreferenceChangeListener((preference, newValue) -> {
                    if (!Boolean.TRUE.equals(newValue)) {
                        return true;
                    }
                    startModelDownload(naturalLanguagePreference);
                    return false;
                });
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

        private void startModelDownload(SwitchPreferenceCompat preference) {
            preference.setEnabled(false);
            preference.setSummary("Downloading model...");
            modelDownloader = new ModelDownloader(requireContext(), new ModelDownloader.Listener() {
                @Override
                public void onProgress(int percent) {
                    if (percent >= 0) {
                        preference.setSummary(percent + "% downloading...");
                    } else {
                        preference.setSummary("Downloading model...");
                    }
                }

                @Override
                public void onSuccess() {
                    preference.setChecked(true);
                    preference.setEnabled(true);
                    preference.setSummary(R.string.natural_language_commands_summary);
                    androidx.preference.PreferenceManager.getDefaultSharedPreferences(requireContext())
                            .edit()
                            .putBoolean(MainActivity.NATURAL_LANGUAGE_PREFERENCE, true)
                            .apply();
                    modelDownloader = null;
                    Toast.makeText(requireContext(), "Natural-language model downloaded.",
                            Toast.LENGTH_SHORT).show();
                }

                @Override
                public void onError(String message) {
                    preference.setChecked(false);
                    preference.setEnabled(true);
                    preference.setSummary(R.string.natural_language_commands_summary);
                    androidx.preference.PreferenceManager.getDefaultSharedPreferences(requireContext())
                            .edit()
                            .putBoolean(MainActivity.NATURAL_LANGUAGE_PREFERENCE, false)
                            .apply();
                    modelDownloader = null;
                    Toast.makeText(requireContext(), "Model download failed: " + message,
                            Toast.LENGTH_LONG).show();
                }

                @Override
                public void onCancelled() {
                    preference.setChecked(false);
                    preference.setEnabled(true);
                    preference.setSummary(R.string.natural_language_commands_summary);
                    modelDownloader = null;
                    Toast.makeText(requireContext(), "Model download cancelled.",
                            Toast.LENGTH_SHORT).show();
                }
            });
            modelDownloader.start();
        }

        @Override
        public void onDestroy() {
            if (modelDownloader != null) {
                modelDownloader.close();
                modelDownloader = null;
            }
            super.onDestroy();
        }
    }
}
