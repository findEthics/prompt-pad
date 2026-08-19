package me.pompel.elauncher;

/** Displays local groceries; tap to complete and long press to delete. */
public final class GroceryActivity extends LocalListActivity {
    public GroceryActivity() {
        super(LocalListKind.GROCERIES);
    }
}
