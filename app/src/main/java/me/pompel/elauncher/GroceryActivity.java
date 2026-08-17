package me.pompel.elauncher;

import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.FrameLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.res.ResourcesCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.List;

/** Displays local groceries; tap to complete and long press to delete. */
public final class GroceryActivity extends AppCompatActivity {
    private GroceryRepository repository;
    private ListView listView;
    private List<GroceryItem> groceries;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ThemePreference.apply(this);
        setTitle("Groceries");
        repository = new GroceryRepository(new SharedPreferencesKeyValueStore(
                getSharedPreferences("command_data", MODE_PRIVATE)));
        listView = new ListView(this);
        View empty = emptyView();
        FrameLayout root = new FrameLayout(this);
        root.addView(listView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        root.addView(empty, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        listView.setEmptyView(empty);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                repository.toggleCompletion(groceries.get(position).getId());
                refresh();
            }
        });
        listView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                confirmDelete(groceries.get(position));
                return true;
            }
        });
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(root);
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout() | WindowInsetsCompat.Type.ime());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
        ViewCompat.requestApplyInsets(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private View emptyView() {
        TextView empty = new TextView(this);
        empty.setText("No groceries yet");
        empty.setTextSize(20);
        empty.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        empty.setTypeface(ResourcesCompat.getFont(this, R.font.poppins));
        return empty;
    }

    private void refresh() {
        groceries = repository.list();
        listView.setAdapter(new ArrayAdapter<GroceryItem>(this,
                android.R.layout.simple_list_item_1, groceries) {
            @Override
            public View getView(int position, View convertView, android.view.ViewGroup parent) {
                TextView row = (TextView) super.getView(position, convertView, parent);
                GroceryItem grocery = getItem(position);
                row.setText((grocery.isCompleted() ? "[x] " : "[ ] ") + grocery.getItem());
                row.setPadding(32, 24, 32, 24);
                row.setTypeface(ResourcesCompat.getFont(GroceryActivity.this, R.font.poppins));
                return row;
            }
        });
    }

    private void confirmDelete(final GroceryItem grocery) {
        new AlertDialog.Builder(this)
                .setMessage("Delete this grocery item?")
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton("Delete", (dialog, which) -> {
                    repository.delete(grocery.getId());
                    Toast.makeText(this, "Grocery item deleted", Toast.LENGTH_SHORT).show();
                    refresh();
                })
                .show();
    }
}
