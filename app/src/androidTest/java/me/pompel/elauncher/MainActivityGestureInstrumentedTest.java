package me.pompel.elauncher;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.view.KeyEvent;
import android.view.View;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.preference.PreferenceManager;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class MainActivityGestureInstrumentedTest {
    @Test
    public void backFromDrawerReturnsToHome() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Intent intent = new Intent(context, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        MainActivity activity = (MainActivity) InstrumentationRegistry.getInstrumentation()
                .startActivitySync(intent);

        try {
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
                activity.findViewById(R.id.HomeScreen).setVisibility(View.GONE);
                activity.findViewById(R.id.AppDrawer).setVisibility(View.VISIBLE);
                activity.getOnBackPressedDispatcher().onBackPressed();
            });

            assertEquals(View.VISIBLE, activity.findViewById(R.id.HomeScreen).getVisibility());
            assertEquals(View.GONE, activity.findViewById(R.id.AppDrawer).getVisibility());
        } finally {
            activity.finish();
        }
    }

    @Test
    public void typingOnHomeOpensDrawerWithText() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        boolean hadPreference = preferences.contains(MainActivity.HAS_KEYBOARD_PREFERENCE);
        boolean previousValue = preferences.getBoolean(MainActivity.HAS_KEYBOARD_PREFERENCE, false);
        try {
            preferences.edit().putBoolean(MainActivity.HAS_KEYBOARD_PREFERENCE, true).commit();
            MainActivity activity = (MainActivity) InstrumentationRegistry.getInstrumentation()
                    .startActivitySync(new Intent(context, MainActivity.class)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            try {
                InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
                    assertTrue(activity.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,
                            KeyEvent.KEYCODE_A)));
                    assertEquals(View.GONE, activity.findViewById(R.id.HomeScreen).getVisibility());
                    assertEquals(View.VISIBLE, activity.findViewById(R.id.AppDrawer).getVisibility());
                    assertEquals("a", ((android.widget.EditText) activity.findViewById(R.id.search))
                            .getText().toString());
                });
            } finally {
                activity.finish();
            }
        } finally {
            SharedPreferences.Editor editor = preferences.edit();
            if (hadPreference) editor.putBoolean(MainActivity.HAS_KEYBOARD_PREFERENCE, previousValue);
            else editor.remove(MainActivity.HAS_KEYBOARD_PREFERENCE);
            editor.commit();
        }
    }

    @Test
    public void typingBangOnHomeEntersCommandModeWithoutSubmitting() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        boolean hadPreference = preferences.contains(MainActivity.HAS_KEYBOARD_PREFERENCE);
        boolean previousValue = preferences.getBoolean(MainActivity.HAS_KEYBOARD_PREFERENCE, false);
        try {
            preferences.edit().putBoolean(MainActivity.HAS_KEYBOARD_PREFERENCE, true).commit();
            MainActivity activity = (MainActivity) InstrumentationRegistry.getInstrumentation()
                    .startActivitySync(new Intent(context, MainActivity.class)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            try {
                InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
                    assertTrue(activity.dispatchKeyEvent(new KeyEvent(0, 0, KeyEvent.ACTION_DOWN,
                            KeyEvent.KEYCODE_1, 0, KeyEvent.META_SHIFT_ON)));
                    assertEquals(View.GONE, activity.findViewById(R.id.HomeScreen).getVisibility());
                    assertEquals(View.VISIBLE, activity.findViewById(R.id.AppDrawer).getVisibility());
                    assertEquals("!", ((android.widget.EditText) activity.findViewById(R.id.search))
                            .getText().toString());
                    assertTrue(((androidx.recyclerview.widget.RecyclerView)
                            activity.findViewById(R.id.recycler_view)).getAdapter() instanceof CommandAdapter);
                });
            } finally {
                activity.finish();
            }
        } finally {
            SharedPreferences.Editor editor = preferences.edit();
            if (hadPreference) editor.putBoolean(MainActivity.HAS_KEYBOARD_PREFERENCE, previousValue);
            else editor.remove(MainActivity.HAS_KEYBOARD_PREFERENCE);
            editor.commit();
        }
    }
}
