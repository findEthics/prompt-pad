package me.pompel.elauncher;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;

public class NotesRepositoryTest {
    @Test
    public void listReturnsNotesNewestFirst() {
        InMemoryKeyValueStore keyValueStore = new InMemoryKeyValueStore();
        MutableTimeSource timeSource = new MutableTimeSource(100L);
        NotesRepository repository = new NotesRepository(keyValueStore, timeSource);

        Note older = repository.add("Older note");
        timeSource.setCurrentTimeMillis(200L);
        Note newer = repository.add("Newer note");

        assertEquals(Arrays.asList(newer, older), repository.list());
    }

    @Test
    public void notesPersistAcrossRepositoryRecreationAndCanBeDeleted() {
        InMemoryKeyValueStore keyValueStore = new InMemoryKeyValueStore();
        NotesRepository firstRepository = new NotesRepository(keyValueStore, new MutableTimeSource(100L));
        Note note = firstRepository.add("Remember this");

        NotesRepository recreatedRepository = new NotesRepository(keyValueStore, new MutableTimeSource(200L));
        assertEquals(Arrays.asList(note), recreatedRepository.list());
        assertTrue(recreatedRepository.delete(note.getId()));
        assertFalse(recreatedRepository.delete(note.getId()));

        assertTrue(new NotesRepository(keyValueStore).list().isEmpty());
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
