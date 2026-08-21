package me.pompel.elauncher;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class LocalListActivityInstrumentedTest {
    @Test
    public void deleteButtonDeletesItemImmediately() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        LocalListRepository repository = new LocalListRepository(new SharedPreferencesKeyValueStore(
                context.getSharedPreferences("command_data", Context.MODE_PRIVATE)), LocalListKind.TODOS);
        LocalListItem item = repository.add("__instrument_delete_button__");
        TodosActivity activity = (TodosActivity) InstrumentationRegistry.getInstrumentation()
                .startActivitySync(new Intent(context, TodosActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        try {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            ImageButton delete = findDeleteButton(activity.getWindow().getDecorView(),
                    "Delete " + item.getText());
            assertNotNull(delete);
            InstrumentationRegistry.getInstrumentation().runOnMainSync(delete::performClick);
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            assertFalse(contains(repository.list(), item.getId()));
        } finally {
            repository.delete(item.getId());
            activity.finish();
        }
    }

    private static ImageButton findDeleteButton(View view, String description) {
        if (view instanceof ImageButton && description.contentEquals(view.getContentDescription())) {
            return (ImageButton) view;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int index = 0; index < group.getChildCount(); index++) {
                ImageButton button = findDeleteButton(group.getChildAt(index), description);
                if (button != null) {
                    return button;
                }
            }
        }
        return null;
    }

    private static boolean contains(java.util.List<LocalListItem> items, String id) {
        for (LocalListItem item : items) {
            if (id.equals(item.getId())) {
                return true;
            }
        }
        return false;
    }
}
