package me.pompel.elauncher;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/** Local persistent store shared by notes, to-dos, and groceries. */
public final class LocalListRepository {
    private final KeyValueStore keyValueStore;
    private final LocalListKind kind;
    private final CommandParser.Clock clock;

    public LocalListRepository(KeyValueStore keyValueStore, LocalListKind kind) {
        this(keyValueStore, kind, CommandParser.Clock.system());
    }

    public LocalListRepository(KeyValueStore keyValueStore, LocalListKind kind,
            CommandParser.Clock clock) {
        if (keyValueStore == null || kind == null || clock == null) {
            throw new IllegalArgumentException("keyValueStore, kind, and clock must not be null");
        }
        this.keyValueStore = keyValueStore;
        this.kind = kind;
        this.clock = clock;
    }

    public LocalListItem add(String text) {
        List<LocalListItem> items = load();
        LocalListItem item = new LocalListItem(UUID.randomUUID().toString(),
                RepositorySupport.normalizeText(text), nextCreatedAt(items), false);
        items.add(item);
        save(items);
        return item;
    }

    public List<LocalListItem> list() {
        List<LocalListItem> items = load();
        Collections.sort(items, new Comparator<LocalListItem>() {
            @Override
            public int compare(LocalListItem first, LocalListItem second) {
                if (kind.checklist && first.isCompleted() != second.isCompleted()) {
                    return first.isCompleted() ? 1 : -1;
                }
                if (first.getCreatedAtMillis() != second.getCreatedAtMillis()) {
                    return first.getCreatedAtMillis() > second.getCreatedAtMillis() ? -1 : 1;
                }
                return first.getId().compareTo(second.getId());
            }
        });
        return Collections.unmodifiableList(items);
    }

    public boolean toggleCompletion(String id) {
        if (!kind.checklist || id == null) {
            return false;
        }
        List<LocalListItem> items = load();
        for (int index = 0; index < items.size(); index++) {
            LocalListItem item = items.get(index);
            if (id.equals(item.getId())) {
                items.set(index, item.withCompleted(!item.isCompleted()));
                save(items);
                return true;
            }
        }
        return false;
    }

    public boolean delete(String id) {
        if (id == null) {
            return false;
        }
        List<LocalListItem> items = load();
        for (Iterator<LocalListItem> iterator = items.iterator(); iterator.hasNext(); ) {
            if (id.equals(iterator.next().getId())) {
                iterator.remove();
                save(items);
                return true;
            }
        }
        return false;
    }

    private List<LocalListItem> load() {
        List<String[]> rows = PersistentValueCodec.decode(
                keyValueStore.getString(kind.key), kind.fieldsPerRow);
        List<LocalListItem> items = new ArrayList<>(rows.size());
        try {
            for (String[] row : rows) {
                boolean completed = false;
                if (kind.checklist) {
                    if (!"true".equals(row[3]) && !"false".equals(row[3])) {
                        return new ArrayList<>();
                    }
                    completed = Boolean.parseBoolean(row[3]);
                }
                items.add(new LocalListItem(row[0], row[1], Long.parseLong(row[2]), completed));
            }
            return items;
        } catch (IllegalArgumentException exception) {
            return new ArrayList<>();
        }
    }

    private void save(List<LocalListItem> items) {
        List<String[]> rows = new ArrayList<>(items.size());
        for (LocalListItem item : items) {
            if (kind.checklist) {
                rows.add(new String[]{item.getId(), item.getText(),
                        Long.toString(item.getCreatedAtMillis()),
                        Boolean.toString(item.isCompleted())});
            } else {
                rows.add(new String[]{item.getId(), item.getText(),
                        Long.toString(item.getCreatedAtMillis())});
            }
        }
        keyValueStore.putString(kind.key, PersistentValueCodec.encode(rows));
    }

    private long nextCreatedAt(List<LocalListItem> items) {
        long latest = Long.MIN_VALUE;
        for (LocalListItem item : items) {
            latest = Math.max(latest, item.getCreatedAtMillis());
        }
        long now = clock.currentTimeMillis();
        return now <= latest && latest < Long.MAX_VALUE ? latest + 1 : now;
    }
}
