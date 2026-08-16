package me.pompel.elauncher;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.provider.AlarmClock;
import android.provider.CalendarContract;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.TimeZone;

@RunWith(AndroidJUnit4.class)
public class CommandIntentFactoryInstrumentedTest {
    @Test
    public void safeSystemIntentsOnlyPrefillForms() {
        Intent dial = CommandIntentFactory.dial("+15551234567");
        Intent text = CommandIntentFactory.composeText("5551234567", "hello");
        Intent telegram = CommandIntentFactory.openTelegram("alice", "hello there");
        Intent timer = CommandIntentFactory.setTimer(95, "Tea");
        Intent alarm = CommandIntentFactory.setAlarm(7, 5);

        CommandParser.EventCommand event = (CommandParser.EventCommand) new CommandParser(
                new FixedClock()).parse("!event tomorrow 14:30 Project review").getCommand();
        Intent calendar = CommandIntentFactory.insertEvent(event);

        assertEquals(Intent.ACTION_DIAL, dial.getAction());
        assertEquals("+15551234567", dial.getData().getSchemeSpecificPart());
        assertEquals(Intent.ACTION_SENDTO, text.getAction());
        assertEquals("smsto:5551234567", text.getDataString());
        assertEquals("hello", text.getStringExtra("sms_body"));
        assertEquals(Intent.ACTION_VIEW, telegram.getAction());
        assertEquals("tg", telegram.getData().getScheme());
        assertEquals("resolve", telegram.getData().getAuthority());
        assertEquals("alice", telegram.getData().getQueryParameter("domain"));
        assertEquals("hello there", telegram.getData().getQueryParameter("text"));
        assertEquals(95, timer.getIntExtra(AlarmClock.EXTRA_LENGTH, 0));
        assertEquals("Tea", timer.getStringExtra(AlarmClock.EXTRA_MESSAGE));
        assertEquals(AlarmClock.ACTION_SET_ALARM, alarm.getAction());
        assertEquals(7, alarm.getIntExtra(AlarmClock.EXTRA_HOUR, -1));
        assertEquals(5, alarm.getIntExtra(AlarmClock.EXTRA_MINUTES, -1));
        assertTrue(alarm.getBooleanExtra(AlarmClock.EXTRA_SKIP_UI, false));
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        assertTrue(alarm.resolveActivity(context.getPackageManager()) != null);
        assertEquals(Intent.ACTION_INSERT, calendar.getAction());
        assertEquals("Project review", calendar.getStringExtra(CalendarContract.Events.TITLE));
        assertEquals(event.getStartTimeMillis(), calendar.getLongExtra(
                CalendarContract.EXTRA_EVENT_BEGIN_TIME, 0));
    }

    @Test
    public void notesAndTodosSurviveRepositoryRecreation() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        SharedPreferences preferences = context.getSharedPreferences("phase4-instrumentation",
                Context.MODE_PRIVATE);
        preferences.edit().clear().commit();
        SharedPreferencesKeyValueStore store = new SharedPreferencesKeyValueStore(preferences);

        NotesRepository notes = new NotesRepository(store);
        TodosRepository todos = new TodosRepository(store);
        Note first = notes.add("First note");
        Note second = notes.add("Second note");
        Todo todo = todos.add("Buy batteries");

        List<Note> restoredNotes = new NotesRepository(store).list();
        List<Todo> restoredTodos = new TodosRepository(store).list();
        assertEquals(second, restoredNotes.get(0));
        assertEquals(first, restoredNotes.get(1));
        assertFalse(restoredTodos.get(0).isCompleted());

        new TodosRepository(store).toggleCompletion(todo.getId());
        assertTrue(new TodosRepository(store).list().get(0).isCompleted());
    }

    private static final class FixedClock implements CommandParser.Clock {
        @Override
        public long currentTimeMillis() {
            return 1786802400000L; // 2026-08-15 10:00:00 UTC
        }

        @Override
        public TimeZone timeZone() {
            return TimeZone.getTimeZone("UTC");
        }
    }
}
