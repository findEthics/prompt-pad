package me.pompel.elauncher;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.TimeZone;

public class TodosRepositoryTest {
    @Test
    public void listReturnsIncompleteTodosFirstThenNewestFirst() {
        InMemoryKeyValueStore keyValueStore = new InMemoryKeyValueStore();
        MutableClock clock = new MutableClock(100L);
        TodosRepository repository = new TodosRepository(keyValueStore, clock);

        Todo olderIncomplete = repository.add("Older incomplete");
        clock.setCurrentTimeMillis(200L);
        Todo completed = repository.add("Completed");
        clock.setCurrentTimeMillis(300L);
        Todo newerIncomplete = repository.add("Newer incomplete");
        assertTrue(repository.toggleCompletion(completed.getId()));

        assertEquals(Arrays.asList(newerIncomplete, olderIncomplete,
                completed.withCompleted(true)), repository.list());
    }

    @Test
    public void completionAndDeletionPersistAcrossRepositoryRecreation() {
        InMemoryKeyValueStore keyValueStore = new InMemoryKeyValueStore();
        TodosRepository firstRepository = new TodosRepository(keyValueStore, new MutableClock(100L));
        Todo todo = firstRepository.add("Persist me");

        TodosRepository recreatedRepository = new TodosRepository(keyValueStore, new MutableClock(200L));
        assertTrue(recreatedRepository.toggleCompletion(todo.getId()));

        TodosRepository afterToggle = new TodosRepository(keyValueStore, new MutableClock(300L));
        assertEquals(Arrays.asList(todo.withCompleted(true)), afterToggle.list());
        assertTrue(afterToggle.delete(todo.getId()));
        assertFalse(afterToggle.delete(todo.getId()));

        assertTrue(new TodosRepository(keyValueStore).list().isEmpty());
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
