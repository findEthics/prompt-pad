package me.pompel.elauncher;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.TimeZone;

public class LocalListRepositoryTest {
    @Test
    public void notesReturnNewestFirstAndPersistAcrossRecreation() {
        InMemoryKeyValueStore store = new InMemoryKeyValueStore();
        MutableClock clock = new MutableClock(100L);
        LocalListRepository repository = repository(store, LocalListKind.NOTES, clock);

        LocalListItem older = repository.add("Older note");
        clock.setCurrentTimeMillis(200L);
        LocalListItem newer = repository.add("Newer note");

        assertEquals(Arrays.asList(newer, older), repository.list());
        LocalListRepository recreated = repository(store, LocalListKind.NOTES, new MutableClock(300L));
        assertEquals(Arrays.asList(newer, older), recreated.list());
        assertTrue(recreated.delete(older.getId()));
        assertFalse(recreated.delete(older.getId()));
    }

    @Test
    public void todosReturnIncompleteFirstAndPersistCompletionAndDeletion() {
        InMemoryKeyValueStore store = new InMemoryKeyValueStore();
        MutableClock clock = new MutableClock(100L);
        LocalListRepository repository = repository(store, LocalListKind.TODOS, clock);

        LocalListItem olderIncomplete = repository.add("Older incomplete");
        clock.setCurrentTimeMillis(200L);
        LocalListItem completed = repository.add("Completed");
        clock.setCurrentTimeMillis(300L);
        LocalListItem newerIncomplete = repository.add("Newer incomplete");
        assertTrue(repository.toggleCompletion(completed.getId()));

        assertEquals(Arrays.asList(newerIncomplete, olderIncomplete,
                completed.withCompleted(true)), repository.list());

        LocalListRepository recreated = repository(store, LocalListKind.TODOS, new MutableClock(400L));
        assertEquals(Arrays.asList(newerIncomplete, olderIncomplete,
                completed.withCompleted(true)), recreated.list());
        assertTrue(recreated.delete(completed.getId()));
        assertFalse(recreated.delete(completed.getId()));
    }

    @Test
    public void groceriesShareChecklistBehavior() {
        InMemoryKeyValueStore store = new InMemoryKeyValueStore();
        MutableClock clock = new MutableClock(100L);
        LocalListRepository repository = repository(store, LocalListKind.GROCERIES, clock);

        LocalListItem older = repository.add("milk");
        clock.setCurrentTimeMillis(200L);
        LocalListItem completed = repository.add("eggs");
        clock.setCurrentTimeMillis(300L);
        LocalListItem newer = repository.add("bread");
        assertTrue(repository.toggleCompletion(completed.getId()));

        assertEquals(Arrays.asList(newer, older, completed.withCompleted(true)), repository.list());
        assertTrue(repository.delete(older.getId()));
        assertFalse(repository.delete(older.getId()));
    }

    @Test
    public void updatesTextPreservingStateAndPersistingAcrossRecreation() {
        InMemoryKeyValueStore store = new InMemoryKeyValueStore();
        LocalListRepository repository = repository(store, LocalListKind.TODOS, new MutableClock(100L));
        LocalListItem original = repository.add("old text");
        assertTrue(repository.toggleCompletion(original.getId()));

        assertTrue(repository.updateText(original.getId(), "  new text  "));
        LocalListItem expected = original.withText("new text").withCompleted(true);
        assertEquals(Collections.singletonList(expected), repository.list());

        LocalListRepository recreated = repository(store, LocalListKind.TODOS, new MutableClock(200L));
        assertEquals(Collections.singletonList(expected), recreated.list());
        assertFalse(recreated.updateText(original.getId(), "   "));
        assertFalse(recreated.updateText("missing", "new text"));
        assertFalse(recreated.updateText(null, "new text"));
    }

    @Test
    public void notesCanBeEditedWithoutChangingTimestamp() {
        InMemoryKeyValueStore store = new InMemoryKeyValueStore();
        LocalListRepository repository = repository(store, LocalListKind.NOTES, new MutableClock(100L));
        LocalListItem original = repository.add("old note");

        assertTrue(repository.updateText(original.getId(), "  updated note  "));
        LocalListItem updated = original.withText("updated note");
        assertEquals(Collections.singletonList(updated), repository.list());

        LocalListRepository recreated = repository(store, LocalListKind.NOTES, new MutableClock(200L));
        assertEquals(Collections.singletonList(updated), recreated.list());
    }

    @Test
    public void preservesLegacyKeysAndRowShapes() {
        InMemoryKeyValueStore store = new InMemoryKeyValueStore();
        store.putString(LocalListKind.NOTES.key, PersistentValueCodec.encode(Collections.singletonList(
                new String[]{"note-id", "legacy note", "100"})));
        store.putString(LocalListKind.TODOS.key, PersistentValueCodec.encode(Collections.singletonList(
                new String[]{"todo-id", "legacy todo", "200", "false"})));
        store.putString(LocalListKind.GROCERIES.key, PersistentValueCodec.encode(Collections.singletonList(
                new String[]{"grocery-id", "legacy grocery", "300", "true"})));

        LocalListRepository notes = repository(store, LocalListKind.NOTES, new MutableClock(400L));
        LocalListRepository todos = repository(store, LocalListKind.TODOS, new MutableClock(400L));
        LocalListRepository groceries = repository(store, LocalListKind.GROCERIES, new MutableClock(400L));

        assertEquals("legacy note", notes.list().get(0).getText());
        assertEquals("legacy todo", todos.list().get(0).getText());
        assertEquals("legacy grocery", groceries.list().get(0).getText());
        LocalListItem todo = todos.add("new todo");
        assertTrue(todos.toggleCompletion(todo.getId()));
        assertTrue(todos.delete(todo.getId()));
        LocalListItem grocery = groceries.add("new grocery");
        assertTrue(groceries.toggleCompletion(grocery.getId()));
        assertTrue(groceries.delete(grocery.getId()));
        assertTrue(todos.toggleCompletion("todo-id"));
        assertFalse(groceries.toggleCompletion("missing-id"));

        LocalListItem note = notes.add("new note");
        assertTrue(notes.delete(note.getId()));
        assertEquals(3, fieldsInStoredRow(store, LocalListKind.NOTES));
        assertEquals(4, fieldsInStoredRow(store, LocalListKind.TODOS));
        assertEquals(4, fieldsInStoredRow(store, LocalListKind.GROCERIES));
    }

    private static LocalListRepository repository(InMemoryKeyValueStore store, LocalListKind kind,
            MutableClock clock) {
        return new LocalListRepository(store, kind, clock);
    }

    private static int fieldsInStoredRow(InMemoryKeyValueStore store, LocalListKind kind) {
        List<String[]> rows = PersistentValueCodec.decode(store.getString(kind.key), kind.fieldsPerRow);
        assertEquals(1, rows.size());
        return rows.get(0).length;
    }

    private static final class MutableClock implements CommandParser.Clock {
        private long currentTimeMillis;

        MutableClock(long currentTimeMillis) {
            this.currentTimeMillis = currentTimeMillis;
        }

        void setCurrentTimeMillis(long currentTimeMillis) {
            this.currentTimeMillis = currentTimeMillis;
        }

        @Override
        public long currentTimeMillis() {
            return currentTimeMillis;
        }

        @Override
        public TimeZone timeZone() {
            return TimeZone.getDefault();
        }
    }
}
