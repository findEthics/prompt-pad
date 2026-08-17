package me.pompel.elauncher;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;

import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.widget.EditText;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.preference.PreferenceManager;

import org.junit.Test;
import org.junit.Assume;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class MediaPipeLlmInterpreterInstrumentedTest {
    @Test
    public void generatesParserAcceptableCommand() {
        Context context = ApplicationProvider.getApplicationContext();
        Assume.assumeTrue("Push the .task model before running this smoke test",
                MediaPipeLlmInterpreter.isModelPresent(context));

        MediaPipeLlmInterpreter interpreter = new MediaPipeLlmInterpreter(context);
        try {
            String raw = interpreter.interpret("buy milk");
            assertNotNull(raw);
            String command = LlmOutputMapper.toCommandString(raw);
            assertNotNull(raw + " did not map to a launcher command", command);
            assertEquals("!grocery milk", command);
            assertTrue(new CommandParser().parse(command).isSuccess());
        } finally {
            interpreter.close();
        }
    }

    @Test
    public void naturalLanguageSuggestsWithoutSubmitting() {
        Context context = ApplicationProvider.getApplicationContext();
        Assume.assumeTrue(MediaPipeLlmInterpreter.isModelPresent(context));
        android.content.SharedPreferences preferences =
                PreferenceManager.getDefaultSharedPreferences(context);
        boolean hadPreference = preferences.contains(MainActivity.NATURAL_LANGUAGE_PREFERENCE);
        boolean previousValue = preferences.getBoolean(MainActivity.NATURAL_LANGUAGE_PREFERENCE, false);
        MainActivity activity = null;
        try {
            preferences.edit().putBoolean(MainActivity.NATURAL_LANGUAGE_PREFERENCE, true).commit();
            activity = (MainActivity) InstrumentationRegistry.getInstrumentation().startActivitySync(
                    new Intent(context, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            final MainActivity currentActivity = activity;
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
                currentActivity.findViewById(R.id.AppDrawer).setVisibility(android.view.View.VISIBLE);
                ((EditText) currentActivity.findViewById(R.id.search)).setText("buy milk");
            });

            String query = "";
            for (int attempt = 0; attempt < 20; attempt++) {
                final String[] currentQuery = {""};
                InstrumentationRegistry.getInstrumentation().runOnMainSync(() ->
                        currentQuery[0] = ((EditText) currentActivity.findViewById(R.id.search))
                                .getText().toString());
                query = currentQuery[0];
                if ("!grocery milk".equals(query)) {
                    break;
                }
                SystemClock.sleep(500);
            }
            assertEquals("!grocery milk", query);
        } finally {
            if (activity != null) {
                activity.finish();
            }
            android.content.SharedPreferences.Editor editor = preferences.edit();
            if (hadPreference) {
                editor.putBoolean(MainActivity.NATURAL_LANGUAGE_PREFERENCE, previousValue);
            } else {
                editor.remove(MainActivity.NATURAL_LANGUAGE_PREFERENCE);
            }
            editor.commit();
        }
    }
}
