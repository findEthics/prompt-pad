package me.pompel.elauncher;

/** Immutable locally stored to-do. */
public final class Todo {
    private final String id;
    private final String text;
    private final long createdAtMillis;
    private final boolean completed;

    public Todo(String id, String text, long createdAtMillis, boolean completed) {
        if (id == null || text == null) {
            throw new IllegalArgumentException("id and text must not be null");
        }
        this.id = id;
        this.text = text;
        this.createdAtMillis = createdAtMillis;
        this.completed = completed;
    }

    public String getId() {
        return id;
    }

    public String getText() {
        return text;
    }

    public long getCreatedAtMillis() {
        return createdAtMillis;
    }

    public boolean isCompleted() {
        return completed;
    }

    public Todo withCompleted(boolean completed) {
        return new Todo(id, text, createdAtMillis, completed);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Todo)) {
            return false;
        }
        Todo todo = (Todo) other;
        return createdAtMillis == todo.createdAtMillis
                && completed == todo.completed
                && id.equals(todo.id)
                && text.equals(todo.text);
    }

    @Override
    public int hashCode() {
        int result = id.hashCode();
        result = 31 * result + text.hashCode();
        result = 31 * result + (int) (createdAtMillis ^ (createdAtMillis >>> 32));
        result = 31 * result + (completed ? 1 : 0);
        return result;
    }
}
