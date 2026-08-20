package me.pompel.elauncher;

/** Displays local groceries; tap to edit and use the checkbox to complete. */
public final class GroceryActivity extends LocalListActivity {
    public GroceryActivity() {
        super(LocalListKind.GROCERIES);
    }
}
