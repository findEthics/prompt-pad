package me.pompel.elauncher;

import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.view.Window;
import android.view.WindowManager;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.res.ResourcesCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

/** Shared UI for the local notes, to-do, and grocery lists. */
public class LocalListActivity extends AppCompatActivity {
    private final LocalListKind kind;
    private LocalListRepository repository;
    private ListView listView;
    private List<LocalListItem> items;
    private TextView actionPill;
    private final DateFormat noteDateFormat = new SimpleDateFormat("dd MMM, HH:mm", Locale.US);
    private final Runnable hideActionPill = () -> {
        if (actionPill == null) {
            return;
        }
        actionPill.animate()
                .translationY(dp(16))
                .alpha(0f)
                .setDuration(220)
                .setInterpolator(new android.view.animation.AccelerateInterpolator())
                .withEndAction(() -> {
                    actionPill.setVisibility(View.GONE);
                    actionPill.setTranslationY(0f);
                })
                .start();
    };

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
        actionPill = new TextView(this);
        actionPill.setBackgroundResource(R.drawable.save_pill_background);
        actionPill.setGravity(Gravity.CENTER);
        actionPill.setMinHeight(dp(48));
        actionPill.setPadding(dp(24), dp(10), dp(24), dp(10));
        actionPill.setTypeface(ResourcesCompat.getFont(this, R.font.poppins));
        actionPill.setTextColor(ResourcesCompat.getColor(getResources(),
                R.color.on_surface_dark,
                getTheme()));
        actionPill.setTextSize(16);
        actionPill.setVisibility(View.GONE);
        FrameLayout.LayoutParams pillParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        pillParams.setMargins(dp(20), 0, dp(20), dp(62));
        root.addView(actionPill, pillParams);
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
        refresh(false);
    }

    private void refresh(boolean animate) {
        items = repository.list();
        listView.setAdapter(new ArrayAdapter<LocalListItem>(this,
                android.R.layout.simple_list_item_1, items) {
            @Override
            public View getView(int position, View convertView, android.view.ViewGroup parent) {
                LocalListItem item = getItem(position);
                LinearLayout row = new LinearLayout(LocalListActivity.this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(32, 24, 32, 24);

                if (kind.checklist) {
                    CheckBox checkbox = new CheckBox(LocalListActivity.this);
                    checkbox.setContentDescription("Mark " + item.getText() + " complete");
                    checkbox.setChecked(item.isCompleted());
                    checkbox.setOnCheckedChangeListener((buttonView, checked) -> {
                        if (checked != item.isCompleted()) {
                            repository.toggleCompletion(item.getId());
                            refresh(true);
                        }
                    });
                    row.addView(checkbox, new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT));

                }

                TextView label = new TextView(LocalListActivity.this);
                label.setText(kind.checklist
                        ? item.getText()
                        : item.getText() + "\n" + noteDateFormat.format(item.getCreatedAtMillis()));
                label.setTypeface(ResourcesCompat.getFont(LocalListActivity.this, R.font.poppins));
                label.setOnClickListener(view -> editItem(item));
                LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                        0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
                if (kind.checklist) {
                    labelParams.setMargins(16, 0, 0, 0);
                }
                row.addView(label, labelParams);
                row.addView(deleteButton(item), new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT));
                row.setOnClickListener(view -> editItem(item));
                return row;
            }
        });
        if (animate) {
            listView.animate().cancel();
            listView.setAlpha(0.85f);
            listView.animate()
                    .alpha(1f)
                    .setDuration(160)
                    .setInterpolator(new DecelerateInterpolator())
                    .start();
        }
    }

    private void editItem(final LocalListItem item) {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setText(item.getText());
        input.setSelection(input.length());

        FrameLayout inputContainer = new FrameLayout(this);
        inputContainer.setPadding(dp(20), 0, dp(20), 0);
        inputContainer.addView(input, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Edit item")
                .setView(inputContainer)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton("Save", null)
                .create();
        dialog.setOnShowListener(ignored -> {
            styleBottomDialog(dialog);
            input.requestFocus();
            input.post(() -> WindowCompat.getInsetsController(dialog.getWindow(), input)
                    .show(WindowInsetsCompat.Type.ime()));
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
                if (input.getText().toString().trim().isEmpty()) {
                    input.setError("Enter an item");
                    return;
                }
                if (repository.updateText(item.getId(), input.getText().toString())) {
                    dialog.dismiss();
                    refresh(true);
                }
            });
        });
        dialog.show();
    }

    private ImageButton deleteButton(final LocalListItem item) {
        ImageButton button = new ImageButton(this);
        button.setImageResource(android.R.drawable.ic_menu_delete);
        button.setContentDescription("Delete " + item.getText());
        button.setOnClickListener(view -> {
            if (repository.delete(item.getId())) {
                showActionPill(kind.deleteToast);
                refresh(true);
            }
        });
        return button;
    }

    private void styleBottomDialog(AlertDialog dialog) {
        Window window = dialog.getWindow();
        if (window == null) {
            return;
        }
        window.setBackgroundDrawable(getResources().getDrawable(
                R.drawable.save_pill_background, getTheme()));
        WindowManager.LayoutParams attributes = window.getAttributes();
        attributes.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        attributes.y = dp(24);
        window.setAttributes(attributes);
        window.setLayout(getResources().getDisplayMetrics().widthPixels - dp(40),
                WindowManager.LayoutParams.WRAP_CONTENT);
    }

    private void showActionPill(String message) {
        actionPill.removeCallbacks(hideActionPill);
        actionPill.animate().cancel();
        actionPill.setText(message);
        actionPill.setVisibility(View.VISIBLE);
        actionPill.setTranslationY(dp(16));
        actionPill.setAlpha(0f);
        actionPill.animate()
                .translationY(0f)
                .alpha(1f)
                .setDuration(220)
                .setInterpolator(new DecelerateInterpolator())
                .start();
        actionPill.postDelayed(hideActionPill, 1200);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
