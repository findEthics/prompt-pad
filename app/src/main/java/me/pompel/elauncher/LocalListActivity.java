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

import java.text.DateFormat;
import java.util.List;

/** Shared UI for the local notes, to-do, and grocery lists. */
public class LocalListActivity extends AppCompatActivity {
    private final LocalListKind kind;
    private LocalListRepository repository;
    private ListView listView;
    private List<LocalListItem> items;

    protected LocalListActivity(LocalListKind kind) {
        if (kind == null) {
            throw new IllegalArgumentException("kind must not be null");
        }
        this.kind = kind;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ThemePreference.apply(this);
        setTitle(kind.title);
        repository = new LocalListRepository(new SharedPreferencesKeyValueStore(
                getSharedPreferences("command_data", MODE_PRIVATE)), kind);
        listView = new ListView(this);
        View empty = emptyView();
        FrameLayout root = new FrameLayout(this);
        root.addView(listView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        root.addView(empty, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        listView.setEmptyView(empty);
        if (kind.checklist) {
            listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                    repository.toggleCompletion(items.get(position).getId());
                    refresh();
                }
            });
        }
        listView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                confirmDelete(items.get(position));
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
        empty.setText(kind.emptyText);
        empty.setTextSize(20);
        empty.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        empty.setTypeface(ResourcesCompat.getFont(this, R.font.poppins));
        return empty;
    }

    private void refresh() {
        items = repository.list();
        listView.setAdapter(new ArrayAdapter<LocalListItem>(this,
                android.R.layout.simple_list_item_1, items) {
            @Override
            public View getView(int position, View convertView, android.view.ViewGroup parent) {
                TextView row = (TextView) super.getView(position, convertView, parent);
                LocalListItem item = getItem(position);
                if (kind.checklist) {
                    row.setText((item.isCompleted() ? "[x] " : "[ ] ") + item.getText());
                } else {
                    row.setText(item.getText() + "\n" + DateFormat.getDateTimeInstance().format(
                            item.getCreatedAtMillis()));
                }
                row.setPadding(32, 24, 32, 24);
                row.setTypeface(ResourcesCompat.getFont(LocalListActivity.this, R.font.poppins));
                return row;
            }
        });
    }

    private void confirmDelete(final LocalListItem item) {
        new AlertDialog.Builder(this)
                .setMessage(kind.deleteMessage)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton("Delete", (dialog, which) -> {
                    repository.delete(item.getId());
                    Toast.makeText(this, kind.deleteToast, Toast.LENGTH_SHORT).show();
                    refresh();
                })
                .show();
    }
}
