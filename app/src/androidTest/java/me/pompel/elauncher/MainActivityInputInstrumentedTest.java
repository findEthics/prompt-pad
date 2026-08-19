package me.pompel.elauncher;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.app.Activity;
import android.graphics.Typeface;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.TextView;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.RecyclerView;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;

@RunWith(AndroidJUnit4.class)
public class MainActivityInputInstrumentedTest {
    @Test
    public void enterPathsSubmitCommandsAndClearTheQuery() {
        assertCommandSubmitted("!todo __instrument_physical_enter", KeyEvent.KEYCODE_ENTER);
        assertCommandSubmitted("!todo __instrument_numpad_enter", KeyEvent.KEYCODE_NUMPAD_ENTER);

        MainActivity activity = startActivity();
        try {
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
                activity.findViewById(R.id.AppDrawer).setVisibility(View.VISIBLE);
                EditText search = activity.findViewById(R.id.search);
                search.setText("!todo __instrument_ime_done");
                search.onEditorAction(EditorInfo.IME_ACTION_DONE);
                assertEquals("", search.getText().toString());
            });
            assertTodoSaved("__instrument_ime_done");
        } finally {
            activity.finish();
        }
    }

    @Test
    public void commandTokensUseTheAccentColorAndBoldStyleInInputAndResults() {
        MainActivity activity = startActivity();
        try {
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
                activity.findViewById(R.id.AppDrawer).setVisibility(View.VISIBLE);
                EditText search = activity.findViewById(R.id.search);
                search.setText("!timer 1m");
                assertCommandTokenStyle(search.getText(), 6, activity);
            });
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
                RecyclerView results = activity.findViewById(R.id.recycler_view);
                RecyclerView.ViewHolder holder = results.findViewHolderForAdapterPosition(0);
                assertTrue(holder != null);
                TextView title = holder.itemView.findViewById(R.id.command_title);
                assertCommandTokenStyle(title.getText(), 6, activity);
            });
        } finally {
            activity.finish();
        }
    }

    @Test
    public void allActivitiesUseTheSavedDarkTheme() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        boolean hadPreference = preferences.contains("dark_mode_preference");
        boolean previousValue = preferences.getBoolean("dark_mode_preference", false);
        try {
            preferences.edit().putBoolean("dark_mode_preference", true).commit();
            assertDarkTheme(MainActivity.class);
            assertDarkTheme(NotesActivity.class);
            assertDarkTheme(TodosActivity.class);
            assertDarkTheme(GroceryActivity.class);
            assertDarkTheme(SettingsActivity.class);
        } finally {
            SharedPreferences.Editor editor = preferences.edit();
            if (hadPreference) editor.putBoolean("dark_mode_preference", previousValue);
            else editor.remove("dark_mode_preference");
            editor.commit();
        }
    }

    @Test
    public void darkThemeIsTheDefaultButSavedChoiceWins() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        boolean hadPreference = preferences.contains("dark_mode_preference");
        boolean previousValue = preferences.getBoolean("dark_mode_preference", false);
        try {
            preferences.edit().remove("dark_mode_preference").commit();
            assertTrue(ThemePreference.isDarkMode(context));
            preferences.edit().putBoolean("dark_mode_preference", false).commit();
            assertTrue(!ThemePreference.isDarkMode(context));
        } finally {
            SharedPreferences.Editor editor = preferences.edit();
            if (hadPreference) editor.putBoolean("dark_mode_preference", previousValue);
            else editor.remove("dark_mode_preference");
            editor.commit();
        }
    }

    @Test
    public void appSearchRequiresContiguousCaseInsensitiveMatch() {
        ArrayList<App> apps = new ArrayList<>();
        apps.add(new App("Calendar", "com.google.android.calendar"));
        final boolean[] clicked = {false};
        recyclerAdapter adapter = new recyclerAdapter(apps, new recyclerAdapter.RecyclerViewClickListener() {
            @Override
            public void onClick(App app) {
                clicked[0] = true;
            }

            @Override
            public void onLongClick(App app) {
            }
        });

        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> adapter.filter("alar"));
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        assertEquals(0, adapter.getItemCount());
        assertTrue(!clicked[0]);

        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> adapter.filter("ALE"));
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        assertEquals(1, adapter.getItemCount());
        assertTrue(!clicked[0]);
    }

    private static void assertCommandSubmitted(String command, int keyCode) {
        MainActivity activity = startActivity();
        try {
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
                EditText search = activity.findViewById(R.id.search);
                search.setText(command);
                assertTrue(search.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, keyCode)));
                assertTrue(search.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, keyCode)));
                assertEquals("", search.getText().toString());
            });
            assertTodoSaved(command.substring("!todo ".length()));
        } finally {
            activity.finish();
        }
    }

    private static MainActivity startActivity() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        return (MainActivity) InstrumentationRegistry.getInstrumentation().startActivitySync(
                new Intent(context, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }

    private static void assertTodoSaved(String text) {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        LocalListRepository repository = new LocalListRepository(new SharedPreferencesKeyValueStore(
                context.getSharedPreferences("command_data", Context.MODE_PRIVATE)), LocalListKind.TODOS);
        for (LocalListItem todo : repository.list()) {
            if (text.equals(todo.getText())) {
                repository.delete(todo.getId());
                return;
            }
        }
        fail("Expected test to-do was not saved: " + text);
    }

    private static void assertDarkTheme(Class<? extends Activity> activityClass) {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Activity activity = InstrumentationRegistry.getInstrumentation().startActivitySync(
                new Intent(context, activityClass).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        try {
            TypedValue background = new TypedValue();
            assertTrue(activity.getTheme().resolveAttribute(android.R.attr.colorBackground,
                    background, true));
            int color = background.resourceId == 0 ? background.data
                    : ContextCompat.getColor(activity, background.resourceId);
            assertEquals(ContextCompat.getColor(activity, R.color.surface_dark), color);
        } finally {
            activity.finish();
        }
    }

    private static void assertCommandTokenStyle(CharSequence text, int end, Context context) {
        assertTrue(text instanceof Spanned);
        Spanned styled = (Spanned) text;
        int expected = ContextCompat.getColor(context, R.color.command_accent);
        boolean accented = false;
        for (ForegroundColorSpan span : styled.getSpans(0, end, ForegroundColorSpan.class)) {
            if (styled.getSpanStart(span) == 0 && styled.getSpanEnd(span) == end) {
                assertEquals(expected, span.getForegroundColor());
                accented = true;
            }
        }
        assertTrue("Expected an accent span for the command token", accented);
        for (StyleSpan span : styled.getSpans(0, end, StyleSpan.class)) {
            if (styled.getSpanStart(span) == 0 && styled.getSpanEnd(span) == end
                    && span.getStyle() == Typeface.BOLD) {
                return;
            }
        }
        fail("Expected a bold span for the command token");
    }
}
