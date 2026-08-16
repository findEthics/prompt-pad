package me.pompel.elauncher;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.TimeZone;

public class NotesRepositoryTest {
    @Test
    public void listReturnsNotesNewestFirst() {
        InMemoryKeyValueStore keyValueStore = new InMemoryKeyValueStore();
        MutableClock clock = new MutableClock(100L);
        NotesRepository repository = new NotesRepository(keyValueStore, clock);

        Note older = repository.add("Older note");
        clock.setCurrentTimeMillis(200L);
        Note newer = repository.add("Newer note");

        assertEquals(Arrays.asList(newer, older), repository.list());
    }

    @Test
    public void notesPersistAcrossRepositoryRecreationAndCanBeDeleted() {
        InMemoryKeyValueStore keyValueStore = new InMemoryKeyValueStore();
        NotesRepository firstRepository = new NotesRepository(keyValueStore, new MutableClock(100L));
        Note note = firstRepository.add("Remember this");

        NotesRepository recreatedRepository = new NotesRepository(keyValueStore, new MutableClock(200L));
        assertEquals(Arrays.asList(note), recreatedRepository.list());
        assertTrue(recreatedRepository.delete(note.getId()));
        assertFalse(recreatedRepository.delete(note.getId()));

        assertTrue(new NotesRepository(keyValueStore).list().isEmpty());
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
