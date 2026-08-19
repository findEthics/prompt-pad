package me.pompel.elauncher;

/** Displays locally stored notes in newest-first order. */
public final class NotesActivity extends LocalListActivity {
    public NotesActivity() {
        super(LocalListKind.NOTES);
    }
}
