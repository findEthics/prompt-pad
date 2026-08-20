package me.pompel.elauncher;

/** Displays local to-dos; tap to edit and use the checkbox to complete. */
public final class TodosActivity extends LocalListActivity {
    public TodosActivity() {
        super(LocalListKind.TODOS);
    }
}
