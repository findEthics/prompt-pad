package me.pompel.elauncher;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;

public class TodosRepositoryTest {
    @Test
    public void listReturnsIncompleteTodosFirstThenNewestFirst() {
        InMemoryKeyValueStore keyValueStore = new InMemoryKeyValueStore();
        MutableTimeSource timeSource = new MutableTimeSource(100L);
        TodosRepository repository = new TodosRepository(keyValueStore, timeSource);

        Todo olderIncomplete = repository.add("Older incomplete");
        timeSource.setCurrentTimeMillis(200L);
        Todo completed = repository.add("Completed");
        timeSource.setCurrentTimeMillis(300L);
        Todo newerIncomplete = repository.add("Newer incomplete");
        assertTrue(repository.toggleCompletion(completed.getId()));

        assertEquals(Arrays.asList(newerIncomplete, olderIncomplete,
                completed.withCompleted(true)), repository.list());
    }

    @Test
    public void completionAndDeletionPersistAcrossRepositoryRecreation() {
        InMemoryKeyValueStore keyValueStore = new InMemoryKeyValueStore();
        TodosRepository firstRepository = new TodosRepository(keyValueStore, new MutableTimeSource(100L));
        Todo todo = firstRepository.add("Persist me");

        TodosRepository recreatedRepository = new TodosRepository(keyValueStore, new MutableTimeSource(200L));
        assertTrue(recreatedRepository.toggleCompletion(todo.getId()));

        TodosRepository afterToggle = new TodosRepository(keyValueStore, new MutableTimeSource(300L));
        assertEquals(Arrays.asList(todo.withCompleted(true)), afterToggle.list());
        assertTrue(afterToggle.delete(todo.getId()));
        assertFalse(afterToggle.delete(todo.getId()));

        assertTrue(new TodosRepository(keyValueStore).list().isEmpty());
    }

    private static final class MutableTimeSource implements TimeSource {
        private long currentTimeMillis;

        MutableTimeSource(long currentTimeMillis) {
            this.currentTimeMillis = currentTimeMillis;
        }

        void setCurrentTimeMillis(long currentTimeMillis) {
            this.currentTimeMillis = currentTimeMillis;
        }

        @Override
        public long currentTimeMillis() {
            return currentTimeMillis;
        }
    }
}
