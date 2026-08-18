package me.pompel.elauncher;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.view.KeyEvent;
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
    public void rejectsIncompleteNaturalLanguageCommands() {
        Context context = ApplicationProvider.getApplicationContext();
        Assume.assumeTrue(MediaPipeLlmInterpreter.isModelPresent(context));

        MediaPipeLlmInterpreter interpreter = new MediaPipeLlmInterpreter(context);
        try {
            for (String input : new String[] {"wake", "remind", "buy", "sh", "ala"}) {
                String raw = interpreter.interpret(input);
                assertNull(input + " was mapped to a command: " + raw,
                        LlmOutputMapper.toCommandString(raw));
            }
        } finally {
            interpreter.close();
        }
    }

    @Test
    public void naturalLanguageRequiresEnterBeforeExecuting() {
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

            SystemClock.sleep(1500);
            final String[] pausedQuery = {""};
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> pausedQuery[0] =
                    ((EditText) currentActivity.findViewById(R.id.search)).getText().toString());
            assertEquals("buy milk", pausedQuery[0]);

            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
                EditText search = currentActivity.findViewById(R.id.search);
                assertTrue(search.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,
                        KeyEvent.KEYCODE_ENTER)));
                assertTrue(search.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,
                        KeyEvent.KEYCODE_ENTER)));
            });

            String query = pausedQuery[0];
            for (int attempt = 0; attempt < 20; attempt++) {
                final String[] currentQuery = {""};
                InstrumentationRegistry.getInstrumentation().runOnMainSync(() ->
                        currentQuery[0] = ((EditText) currentActivity.findViewById(R.id.search))
                                .getText().toString());
                query = currentQuery[0];
                if (query.isEmpty()) {
                    break;
                }
                SystemClock.sleep(500);
            }
            assertEquals("", query);
            assertGrocerySaved(context, "milk");
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

    private static void assertGrocerySaved(Context context, String item) {
        GroceryRepository repository = new GroceryRepository(new SharedPreferencesKeyValueStore(
                context.getSharedPreferences("command_data", Context.MODE_PRIVATE)));
        for (GroceryItem grocery : repository.list()) {
            if (item.equals(grocery.getItem())) {
                repository.delete(grocery.getId());
                return;
            }
        }
        fail("Expected grocery was not saved: " + item);
    }
}
