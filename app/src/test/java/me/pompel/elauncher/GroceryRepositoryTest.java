package me.pompel.elauncher;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.TimeZone;

public class GroceryRepositoryTest {
    @Test
    public void listReturnsIncompleteGroceriesFirstThenNewestFirst() {
        InMemoryKeyValueStore store = new InMemoryKeyValueStore();
        MutableClock clock = new MutableClock(100L);
        GroceryRepository repository = new GroceryRepository(store, clock);

        GroceryItem older = repository.add("milk");
        clock.setCurrentTimeMillis(200L);
        GroceryItem completed = repository.add("eggs");
        clock.setCurrentTimeMillis(300L);
        GroceryItem newer = repository.add("bread");
        assertTrue(repository.toggleCompletion(completed.getId()));

        assertEquals(Arrays.asList(newer, older, completed.withCompleted(true)), repository.list());
    }

    @Test
    public void itemsPersistAcrossRepositoryRecreation() {
        InMemoryKeyValueStore store = new InMemoryKeyValueStore();
        GroceryRepository first = new GroceryRepository(store, new MutableClock(100L));
        GroceryItem item = first.add("coffee");

        GroceryRepository recreated = new GroceryRepository(store, new MutableClock(200L));
        assertTrue(recreated.toggleCompletion(item.getId()));
        assertEquals(Arrays.asList(item.withCompleted(true)), recreated.list());
        assertTrue(recreated.delete(item.getId()));
        assertFalse(recreated.delete(item.getId()));
        assertTrue(new GroceryRepository(store).list().isEmpty());
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
