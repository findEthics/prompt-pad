package me.pompel.elauncher;

/** Configuration shared by the local notes, to-do, and grocery lists. */
enum LocalListKind {
    NOTES("me.pompel.elauncher.notes.v1", 3, "Notes", "No notes yet",
            "Delete this note?", "Note deleted", false),
    TODOS("me.pompel.elauncher.todos.v1", 4, "To-dos", "No to-dos yet",
            "Delete this to-do?", "To-do deleted", true),
    GROCERIES("me.pompel.elauncher.groceries.v1", 4, "Groceries", "No groceries yet",
            "Delete this grocery item?", "Grocery item deleted", true);

    final String key;
    final int fieldsPerRow;
    final String title;
    final String emptyText;
    final String deleteMessage;
    final String deleteToast;
    final boolean checklist;

    LocalListKind(String key, int fieldsPerRow, String title, String emptyText,
            String deleteMessage, String deleteToast, boolean checklist) {
        this.key = key;
        this.fieldsPerRow = fieldsPerRow;
        this.title = title;
        this.emptyText = emptyText;
        this.deleteMessage = deleteMessage;
        this.deleteToast = deleteToast;
        this.checklist = checklist;
    }
}
