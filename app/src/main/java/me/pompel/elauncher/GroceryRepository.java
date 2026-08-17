package me.pompel.elauncher;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/** Local persistent grocery-list store. */
public final class GroceryRepository {
    private static final String GROCERIES_KEY = "me.pompel.elauncher.groceries.v1";

    private final KeyValueStore keyValueStore;
    private final CommandParser.Clock clock;

    public GroceryRepository(KeyValueStore keyValueStore) {
        this(keyValueStore, CommandParser.Clock.system());
    }

    public GroceryRepository(KeyValueStore keyValueStore, CommandParser.Clock clock) {
        if (keyValueStore == null || clock == null) {
            throw new IllegalArgumentException("keyValueStore and clock must not be null");
        }
        this.keyValueStore = keyValueStore;
        this.clock = clock;
    }

    public GroceryItem add(String item) {
        List<GroceryItem> groceries = load();
        GroceryItem grocery = new GroceryItem(UUID.randomUUID().toString(),
                RepositorySupport.normalizeText(item), nextCreatedAt(groceries), false);
        groceries.add(grocery);
        save(groceries);
        return grocery;
    }

    public List<GroceryItem> list() {
        List<GroceryItem> groceries = load();
        Collections.sort(groceries, new Comparator<GroceryItem>() {
            @Override
            public int compare(GroceryItem first, GroceryItem second) {
                if (first.isCompleted() != second.isCompleted()) {
                    return first.isCompleted() ? 1 : -1;
                }
                if (first.getCreatedAtMillis() != second.getCreatedAtMillis()) {
                    return first.getCreatedAtMillis() > second.getCreatedAtMillis() ? -1 : 1;
                }
                return first.getId().compareTo(second.getId());
            }
        });
        return Collections.unmodifiableList(groceries);
    }

    public boolean toggleCompletion(String id) {
        if (id == null) {
            return false;
        }
        List<GroceryItem> groceries = load();
        for (int index = 0; index < groceries.size(); index++) {
            GroceryItem grocery = groceries.get(index);
            if (id.equals(grocery.getId())) {
                groceries.set(index, grocery.withCompleted(!grocery.isCompleted()));
                save(groceries);
                return true;
            }
        }
        return false;
    }

    public boolean delete(String id) {
        if (id == null) {
            return false;
        }
        List<GroceryItem> groceries = load();
        for (Iterator<GroceryItem> iterator = groceries.iterator(); iterator.hasNext(); ) {
            if (id.equals(iterator.next().getId())) {
                iterator.remove();
                save(groceries);
                return true;
            }
        }
        return false;
    }

    private List<GroceryItem> load() {
        List<String[]> rows = PersistentValueCodec.decode(keyValueStore.getString(GROCERIES_KEY), 4);
        List<GroceryItem> groceries = new ArrayList<>(rows.size());
        try {
            for (String[] row : rows) {
                if (!"true".equals(row[3]) && !"false".equals(row[3])) {
                    return new ArrayList<>();
                }
                groceries.add(new GroceryItem(row[0], row[1], Long.parseLong(row[2]),
                        Boolean.parseBoolean(row[3])));
            }
            return groceries;
        } catch (IllegalArgumentException exception) {
            return new ArrayList<>();
        }
    }

    private void save(List<GroceryItem> groceries) {
        List<String[]> rows = new ArrayList<>(groceries.size());
        for (GroceryItem grocery : groceries) {
            rows.add(new String[]{grocery.getId(), grocery.getItem(),
                    Long.toString(grocery.getCreatedAtMillis()),
                    Boolean.toString(grocery.isCompleted())});
        }
        keyValueStore.putString(GROCERIES_KEY, PersistentValueCodec.encode(rows));
    }

    private long nextCreatedAt(List<GroceryItem> groceries) {
        long latest = Long.MIN_VALUE;
        for (GroceryItem grocery : groceries) {
            latest = Math.max(latest, grocery.getCreatedAtMillis());
        }
        long now = clock.currentTimeMillis();
        return now <= latest && latest < Long.MAX_VALUE ? latest + 1 : now;
    }
}
