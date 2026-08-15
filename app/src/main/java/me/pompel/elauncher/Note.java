package me.pompel.elauncher;

/** Immutable locally stored note. */
public final class Note {
    private final String id;
    private final String text;
    private final long createdAtMillis;

    public Note(String id, String text, long createdAtMillis) {
        if (id == null || text == null) {
            throw new IllegalArgumentException("id and text must not be null");
        }
        this.id = id;
        this.text = text;
        this.createdAtMillis = createdAtMillis;
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

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Note)) {
            return false;
        }
        Note note = (Note) other;
        return createdAtMillis == note.createdAtMillis
                && id.equals(note.id)
                && text.equals(note.text);
    }

    @Override
    public int hashCode() {
        int result = id.hashCode();
        result = 31 * result + text.hashCode();
        result = 31 * result + (int) (createdAtMillis ^ (createdAtMillis >>> 32));
        return result;
    }
}
