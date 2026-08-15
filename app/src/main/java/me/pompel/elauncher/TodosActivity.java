package me.pompel.elauncher;

import android.os.Bundle;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.util.List;

/** Displays local to-dos; tap to complete and long press to delete. */
public final class TodosActivity extends AppCompatActivity {
    private TodosRepository repository;
    private ListView listView;
    private List<Todo> todos;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ThemePreference.apply(this);
        setTitle("To-dos");
        repository = new TodosRepository(new SharedPreferencesKeyValueStore(
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
                repository.toggleCompletion(todos.get(position).getId());
                refresh();
            }
        });
        listView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                confirmDelete(todos.get(position));
                return true;
            }
        });
        setContentView(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private View emptyView() {
        TextView empty = new TextView(this);
        empty.setText("No to-dos yet");
        empty.setTextSize(20);
        empty.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        return empty;
    }

    private void refresh() {
        todos = repository.list();
        listView.setAdapter(new ArrayAdapter<Todo>(this, android.R.layout.simple_list_item_1, todos) {
            @Override
            public View getView(int position, View convertView, android.view.ViewGroup parent) {
                TextView row = (TextView) super.getView(position, convertView, parent);
                Todo todo = getItem(position);
                row.setText((todo.isCompleted() ? "[x] " : "[ ] ") + todo.getText());
                row.setPadding(32, 24, 32, 24);
                return row;
            }
        });
    }

    private void confirmDelete(final Todo todo) {
        new AlertDialog.Builder(this)
                .setMessage("Delete this to-do?")
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton("Delete", (dialog, which) -> {
                    repository.delete(todo.getId());
                    Toast.makeText(this, "To-do deleted", Toast.LENGTH_SHORT).show();
                    refresh();
                })
                .show();
    }
}
