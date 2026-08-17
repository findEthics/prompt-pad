package me.pompel.elauncher;

/** Immutable locally stored grocery item. */
public final class GroceryItem {
    private final String id;
    private final String item;
    private final long createdAtMillis;
    private final boolean completed;

    public GroceryItem(String id, String item, long createdAtMillis, boolean completed) {
        if (id == null || item == null) {
            throw new IllegalArgumentException("id and item must not be null");
        }
        this.id = id;
        this.item = item;
        this.createdAtMillis = createdAtMillis;
        this.completed = completed;
    }

    public String getId() {
        return id;
    }

    public String getItem() {
        return item;
    }

    public String getText() {
        return item;
    }

    public long getCreatedAtMillis() {
        return createdAtMillis;
    }

    public boolean isCompleted() {
        return completed;
    }

    public GroceryItem withCompleted(boolean completed) {
        return new GroceryItem(id, item, createdAtMillis, completed);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof GroceryItem)) {
            return false;
        }
        GroceryItem groceryItem = (GroceryItem) other;
        return createdAtMillis == groceryItem.createdAtMillis
                && completed == groceryItem.completed
                && id.equals(groceryItem.id)
                && item.equals(groceryItem.item);
    }

    @Override
    public int hashCode() {
        int result = id.hashCode();
        result = 31 * result + item.hashCode();
        result = 31 * result + (int) (createdAtMillis ^ (createdAtMillis >>> 32));
        result = 31 * result + (completed ? 1 : 0);
        return result;
    }
}
