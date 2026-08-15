package me.pompel.elauncher;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/** Local persistent note store. */
public final class NotesRepository {
    private static final String NOTES_KEY = "me.pompel.elauncher.notes.v1";
    private static final TimeSource SYSTEM_TIME = new TimeSource() {
        @Override
        public long currentTimeMillis() {
            return System.currentTimeMillis();
        }
    };

    private final KeyValueStore keyValueStore;
    private final TimeSource timeSource;

    public NotesRepository(KeyValueStore keyValueStore) {
        this(keyValueStore, SYSTEM_TIME);
    }

    public NotesRepository(KeyValueStore keyValueStore, TimeSource timeSource) {
        if (keyValueStore == null || timeSource == null) {
            throw new IllegalArgumentException("keyValueStore and timeSource must not be null");
        }
        this.keyValueStore = keyValueStore;
        this.timeSource = timeSource;
    }

    public Note add(String text) {
        List<Note> notes = load();
        Note note = new Note(UUID.randomUUID().toString(), normalizeText(text), nextCreatedAt(notes));
        notes.add(note);
        save(notes);
        return note;
    }

    public List<Note> list() {
        List<Note> notes = load();
        Collections.sort(notes, new Comparator<Note>() {
            @Override
            public int compare(Note first, Note second) {
                if (first.getCreatedAtMillis() != second.getCreatedAtMillis()) {
                    return first.getCreatedAtMillis() > second.getCreatedAtMillis() ? -1 : 1;
                }
                return first.getId().compareTo(second.getId());
            }
        });
        return Collections.unmodifiableList(notes);
    }

    public boolean delete(String id) {
        if (id == null) {
            return false;
        }
        List<Note> notes = load();
        for (Iterator<Note> iterator = notes.iterator(); iterator.hasNext(); ) {
            if (id.equals(iterator.next().getId())) {
                iterator.remove();
                save(notes);
                return true;
            }
        }
        return false;
    }

    private List<Note> load() {
        List<String[]> rows = PersistentValueCodec.decode(keyValueStore.getString(NOTES_KEY), 3);
        List<Note> notes = new ArrayList<>(rows.size());
        try {
            for (String[] row : rows) {
                notes.add(new Note(row[0], row[1], Long.parseLong(row[2])));
            }
            return notes;
        } catch (IllegalArgumentException exception) {
            return new ArrayList<>();
        }
    }

    private void save(List<Note> notes) {
        List<String[]> rows = new ArrayList<>(notes.size());
        for (Note note : notes) {
            rows.add(new String[]{note.getId(), note.getText(),
                    Long.toString(note.getCreatedAtMillis())});
        }
        keyValueStore.putString(NOTES_KEY, PersistentValueCodec.encode(rows));
    }

    private long nextCreatedAt(List<Note> notes) {
        long latest = Long.MIN_VALUE;
        for (Note note : notes) {
            latest = Math.max(latest, note.getCreatedAtMillis());
        }
        long now = timeSource.currentTimeMillis();
        return now <= latest && latest < Long.MAX_VALUE ? latest + 1 : now;
    }

    private static String normalizeText(String text) {
        if (text == null || text.trim().isEmpty()) {
            throw new IllegalArgumentException("text must not be blank");
        }
        return text.trim();
    }
}
