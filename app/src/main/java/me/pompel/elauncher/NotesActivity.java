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
import androidx.core.content.res.ResourcesCompat;

import java.text.DateFormat;
import java.util.List;

/** Displays locally stored notes in newest-first order. */
public final class NotesActivity extends AppCompatActivity {
    private NotesRepository repository;
    private ListView listView;
    private List<Note> notes;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ThemePreference.apply(this);
        setTitle("Notes");
        repository = new NotesRepository(new SharedPreferencesKeyValueStore(
                getSharedPreferences("command_data", MODE_PRIVATE)));
        listView = new ListView(this);
        View empty = emptyView();
        FrameLayout root = new FrameLayout(this);
        root.addView(listView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        root.addView(empty, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        listView.setEmptyView(empty);
        listView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                confirmDelete(notes.get(position));
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
        empty.setText("No notes yet");
        empty.setTextSize(20);
        empty.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        empty.setTypeface(ResourcesCompat.getFont(this, R.font.jetbrains_mono));
        return empty;
    }

    private void refresh() {
        notes = repository.list();
        listView.setAdapter(new ArrayAdapter<Note>(this, android.R.layout.simple_list_item_1, notes) {
            @Override
            public View getView(int position, View convertView, android.view.ViewGroup parent) {
                TextView row = (TextView) super.getView(position, convertView, parent);
                Note note = getItem(position);
                row.setText(note.getText() + "\n" + DateFormat.getDateTimeInstance().format(
                        note.getCreatedAtMillis()));
                row.setPadding(32, 24, 32, 24);
                row.setTypeface(ResourcesCompat.getFont(NotesActivity.this, R.font.jetbrains_mono));
                return row;
            }
        });
    }

    private void confirmDelete(final Note note) {
        new AlertDialog.Builder(this)
                .setMessage("Delete this note?")
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton("Delete", (dialog, which) -> {
                    repository.delete(note.getId());
                    Toast.makeText(this, "Note deleted", Toast.LENGTH_SHORT).show();
                    refresh();
                })
                .show();
    }
}
