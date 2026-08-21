package me.pompel.elauncher;

/** Immutable locally stored list item. */
public final class LocalListItem {
    private final String id;
    private final String text;
    private final long createdAtMillis;
    private final boolean completed;

    public LocalListItem(String id, String text, long createdAtMillis, boolean completed) {
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

    public LocalListItem withCompleted(boolean completed) {
        return new LocalListItem(id, text, createdAtMillis, completed);
    }

    public LocalListItem withText(String text) {
        return new LocalListItem(id, text, createdAtMillis, completed);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof LocalListItem)) {
            return false;
        }
        LocalListItem item = (LocalListItem) other;
        return createdAtMillis == item.createdAtMillis
                && completed == item.completed
                && id.equals(item.id)
                && text.equals(item.text);
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
