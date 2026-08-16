package me.pompel.elauncher;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/** Local persistent to-do store. */
public final class TodosRepository {
    private static final String TODOS_KEY = "me.pompel.elauncher.todos.v1";

    private final KeyValueStore keyValueStore;
    private final CommandParser.Clock clock;

    public TodosRepository(KeyValueStore keyValueStore) {
        this(keyValueStore, CommandParser.Clock.system());
    }

    public TodosRepository(KeyValueStore keyValueStore, CommandParser.Clock clock) {
        if (keyValueStore == null || clock == null) {
            throw new IllegalArgumentException("keyValueStore and clock must not be null");
        }
        this.keyValueStore = keyValueStore;
        this.clock = clock;
    }

    public Todo add(String text) {
        List<Todo> todos = load();
        Todo todo = new Todo(UUID.randomUUID().toString(), RepositorySupport.normalizeText(text),
                nextCreatedAt(todos), false);
        todos.add(todo);
        save(todos);
        return todo;
    }

    public List<Todo> list() {
        List<Todo> todos = load();
        Collections.sort(todos, new Comparator<Todo>() {
            @Override
            public int compare(Todo first, Todo second) {
                if (first.isCompleted() != second.isCompleted()) {
                    return first.isCompleted() ? 1 : -1;
                }
                if (first.getCreatedAtMillis() != second.getCreatedAtMillis()) {
                    return first.getCreatedAtMillis() > second.getCreatedAtMillis() ? -1 : 1;
                }
                return first.getId().compareTo(second.getId());
            }
        });
        return Collections.unmodifiableList(todos);
    }

    public boolean toggleCompletion(String id) {
        if (id == null) {
            return false;
        }
        List<Todo> todos = load();
        for (int index = 0; index < todos.size(); index++) {
            Todo todo = todos.get(index);
            if (id.equals(todo.getId())) {
                todos.set(index, todo.withCompleted(!todo.isCompleted()));
                save(todos);
                return true;
            }
        }
        return false;
    }

    public boolean delete(String id) {
        if (id == null) {
            return false;
        }
        List<Todo> todos = load();
        for (Iterator<Todo> iterator = todos.iterator(); iterator.hasNext(); ) {
            if (id.equals(iterator.next().getId())) {
                iterator.remove();
                save(todos);
                return true;
            }
        }
        return false;
    }

    private List<Todo> load() {
        List<String[]> rows = PersistentValueCodec.decode(keyValueStore.getString(TODOS_KEY), 4);
        List<Todo> todos = new ArrayList<>(rows.size());
        try {
            for (String[] row : rows) {
                if (!"true".equals(row[3]) && !"false".equals(row[3])) {
                    return new ArrayList<>();
                }
                todos.add(new Todo(row[0], row[1], Long.parseLong(row[2]),
                        Boolean.parseBoolean(row[3])));
            }
            return todos;
        } catch (IllegalArgumentException exception) {
            return new ArrayList<>();
        }
    }

    private void save(List<Todo> todos) {
        List<String[]> rows = new ArrayList<>(todos.size());
        for (Todo todo : todos) {
            rows.add(new String[]{todo.getId(), todo.getText(),
                    Long.toString(todo.getCreatedAtMillis()), Boolean.toString(todo.isCompleted())});
        }
        keyValueStore.putString(TODOS_KEY, PersistentValueCodec.encode(rows));
    }

    private long nextCreatedAt(List<Todo> todos) {
        long latest = Long.MIN_VALUE;
        for (Todo todo : todos) {
            latest = Math.max(latest, todo.getCreatedAtMillis());
        }
        long now = clock.currentTimeMillis();
        return now <= latest && latest < Long.MAX_VALUE ? latest + 1 : now;
    }

}
