package me.pompel.elauncher;

/** Displays local to-dos; tap to complete and long press to delete. */
public final class TodosActivity extends LocalListActivity {
    public TodosActivity() {
        super(LocalListKind.TODOS);
    }
}
