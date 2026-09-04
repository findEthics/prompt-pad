package me.pompel.elauncher;

import android.Manifest;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.LauncherApps;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.os.UserHandle;
import android.os.UserManager;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Bundle;
import android.provider.CalendarContract;
import android.provider.MediaStore;
import android.provider.Settings;
import android.view.GestureDetector;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Offline, dark-only home launcher optimized for the Titan 4:3 display. */
public class MainActivity extends AppCompatActivity {
    static final String HAS_KEYBOARD_PREFERENCE = "has_keyboard_preference";
    private static final int CALENDAR_PERMISSION_REQUEST = 41;
    private final ArrayList<App> apps = new ArrayList<>();
    private SharedPreferences prefs;
    private EditText search;
    private RecyclerView recycler;
    private recyclerAdapter adapter;
    private CommandAdapter commandAdapter;
    private CommandParser commandParser;
    private LocalListRepository notesRepository;
    private LocalListRepository todosRepository;
    private float downY;
    private boolean commandEnterDown;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        ThemePreference.apply(this);
        prefs = PreferenceManager.getDefaultSharedPreferences(this);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_main);
        ThemePreference.applyTypography(findViewById(R.id.MainLayout), this);
        applyInsets();
        setupDrawer();
        setupTiles();
        setupHomeGestures();
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() {
                if (findViewById(R.id.AppDrawer).getVisibility() == View.VISIBLE) showHome();
            }
        });
    }

    private void applyInsets() {
        View root = findViewById(R.id.MainLayout);
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            Insets safe = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout() | WindowInsetsCompat.Type.ime());
            view.setPadding(safe.left, safe.top, safe.right, safe.bottom);
            return insets;
        });
        ViewCompat.requestApplyInsets(root);
    }

    private void setupDrawer() {
        search = findViewById(R.id.search);
        recycler = findViewById(R.id.recycler_view);
        recycler.setLayoutManager(new LinearLayoutManager(this));
        commandParser = new CommandParser();
        SharedPreferencesKeyValueStore listStore = new SharedPreferencesKeyValueStore(
                getSharedPreferences("command_data", MODE_PRIVATE));
        notesRepository = new LocalListRepository(listStore, LocalListKind.NOTES);
        todosRepository = new LocalListRepository(listStore, LocalListKind.TODOS);
        loadApps();
        adapter = new recyclerAdapter(apps, new recyclerAdapter.RecyclerViewClickListener() {
            @Override public void onClick(App app) { openApp(app); }
            @Override public void onLongClick(App app) {
                launch(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:" + app.packageId)));
            }
        });
        commandAdapter = new CommandAdapter(new CommandAdapter.Listener() {
            @Override public void onEdit(String query) {
                search.setText(query);
                search.setSelection(query.length());
            }
            @Override public void onSubmit(String query) { executeListCommand(query); }
            @Override public void onOpenSettings() {
                launch(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:" + getPackageName())));
            }
        });
        recycler.setAdapter(adapter);
        search.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                CommandAdapter.styleCommandToken(MainActivity.this, search.getText());
                CommandQueryClassifier.Result result = CommandQueryClassifier.classify(s.toString());
                boolean command = result.getDisplayState() != CommandQueryClassifier.DisplayState.APP_RESULTS;
                if (command) {
                    if (recycler.getAdapter() != commandAdapter) recycler.setAdapter(commandAdapter);
                    commandAdapter.submit(result);
                } else {
                    if (recycler.getAdapter() != adapter) recycler.setAdapter(adapter);
                    adapter.filter(result.getAppQuery());
                }
                findViewById(R.id.drawer_empty).setVisibility(s.length() == 0 ? View.VISIBLE : View.GONE);
            }
            @Override public void afterTextChanged(android.text.Editable s) { }
        });
        search.setOnKeyListener((view, keyCode, event) -> {
            if (keyCode != KeyEvent.KEYCODE_ENTER && keyCode != KeyEvent.KEYCODE_NUMPAD_ENTER) return false;
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                commandEnterDown = submitCommandFromInput();
                return commandEnterDown;
            }
            if (event.getAction() == KeyEvent.ACTION_UP) {
                boolean handled = commandEnterDown;
                commandEnterDown = false;
                return handled;
            }
            return false;
        });
        search.setOnEditorActionListener((view, actionId, event) -> {
            String query = search.getText().toString();
            if (query.startsWith("!")) {
                executeListCommand(query);
                return true;
            }
            if (recycler.findViewHolderForAdapterPosition(0) != null) {
                recycler.findViewHolderForAdapterPosition(0).itemView.performClick();
                return true;
            }
            return false;
        });
    }

    private boolean submitCommandFromInput() {
        String query = search.getText().toString();
        if (!query.startsWith("!")) return false;
        executeListCommand(query);
        return true;
    }

    private void executeListCommand(String query) {
        CommandParser.ParseResult parsed = commandParser.parse(query);
        if (!parsed.isSuccess()) {
            commandAdapter.showStatus(parsed.getError().getMessage(), parsed.getError().getSyntax());
            return;
        }
        CommandParser.Command command = parsed.getCommand();
        switch (command.getType()) {
            case NOTE:
                notesRepository.add(((CommandParser.NoteCommand) command).getText());
                commandAdapter.showStatus("Note saved", "Stored locally");
                break;
            case NOTES:
                launch(new Intent(this, NotesActivity.class));
                break;
            case TODO:
                todosRepository.add(((CommandParser.TodoCommand) command).getText());
                commandAdapter.showStatus("To-do saved", "Stored locally");
                refreshTodoCard();
                break;
            case TODOS:
                launch(new Intent(this, TodosActivity.class));
                break;
            default:
                commandAdapter.showStatus("Offline command", "Use !note, !notes, !todo, or !todos");
                break;
        }
        search.setText("");
        recycler.setAdapter(commandAdapter);
    }

    private void loadApps() {
        apps.clear();
        LauncherApps launcherApps = (LauncherApps) getSystemService(Context.LAUNCHER_APPS_SERVICE);
        UserManager userManager = (UserManager) getSystemService(Context.USER_SERVICE);
        for (UserHandle user : userManager.getUserProfiles()) {
            for (LauncherActivityInfo activity : launcherApps.getActivityList(null, user)) {
                if (!getPackageName().equals(activity.getComponentName().getPackageName())) {
                    apps.add(new App(activity.getLabel().toString(), activity.getComponentName(), user));
                }
            }
        }
        Collections.sort(apps, (a, b) -> a.appName.toString().compareToIgnoreCase(b.appName.toString()));
    }

    private void setupTiles() {
        bindTile(R.id.tile_note, 0, new Intent(this, NotesActivity.class));
        bindTile(R.id.tile_event, 1, new Intent(Intent.ACTION_VIEW, CalendarContract.CONTENT_URI));
        bindTile(R.id.tile_clock, 2, new Intent(android.provider.AlarmClock.ACTION_SHOW_ALARMS));
        bindTile(R.id.tile_todo, 3, new Intent(this, TodosActivity.class));
        bindTile(R.id.tile_call, 4, new Intent(Intent.ACTION_DIAL));
        bindTile(R.id.tile_message, 5, new Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:")));
        bindTile(R.id.tile_camera, 6, new Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA));
        bindTile(R.id.tile_memo, 7, new Intent(MediaStore.Audio.Media.RECORD_SOUND_ACTION));
    }

    private void bindTile(int viewId, int position, Intent defaultIntent) {
        Button tile = findViewById(viewId);
        tile.setTypeface(ResourcesCompat.getFont(this, R.font.lato_bold));
        String packageName = prefs.getString("tile_package_" + position, null);
        if (packageName != null) {
            Intent custom = getPackageManager().getLaunchIntentForPackage(packageName);
            if (custom != null) {
                tile.setText(prefs.getString("tile_label_" + position, tile.getText().toString()));
                tile.setOnClickListener(view -> launch(custom));
            } else {
                prefs.edit().remove("tile_package_" + position).remove("tile_label_" + position).apply();
                tile.setOnClickListener(view -> launch(defaultIntent));
            }
        } else {
            tile.setOnClickListener(view -> launch(defaultIntent));
        }
        tile.setOnLongClickListener(view -> {
            showAppPicker(position);
            return true;
        });
    }

    private void showAppPicker(int position) {
        loadApps();
        List<String> labels = new ArrayList<>();
        labels.add("Use default action");
        for (App app : apps) labels.add(app.appName.toString());
        new AlertDialog.Builder(this)
                .setTitle("Choose tile app")
                .setItems(labels.toArray(new String[0]), (dialog, which) -> {
                    SharedPreferences.Editor edit = prefs.edit();
                    if (which == 0) {
                        edit.remove("tile_package_" + position).remove("tile_label_" + position);
                    } else {
                        App app = apps.get(which - 1);
                        edit.putString("tile_package_" + position, app.packageId)
                                .putString("tile_label_" + position, app.appName.toString());
                    }
                    edit.apply();
                    recreate();
                }).show();
    }

    private void setupHomeGestures() {
        View home = findViewById(R.id.HomeScreen);
        GestureDetector detector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onDown(@NonNull MotionEvent event) { return true; }
            @Override public boolean onDoubleTap(@NonNull MotionEvent event) {
                return TapToSleepAccessibilityService.lockScreen();
            }
            @Override public boolean onFling(MotionEvent first, MotionEvent second, float vx, float vy) {
                if (first == null) return false;
                float dx = second.getX() - first.getX();
                float dy = second.getY() - first.getY();
                if (-dy > dp(56) && Math.abs(vy) > 100) {
                    showDrawer();
                    return true;
                }
                if (-dx > dp(56) && Math.abs(vx) > 100) {
                    startActivity(new Intent(MainActivity.this, SettingsActivity.class));
                    return true;
                }
                return false;
            }
            @Override public void onLongPress(@NonNull MotionEvent event) {
                startActivity(new Intent(MainActivity.this, SettingsActivity.class));
            }
        });
        home.setOnTouchListener((view, event) -> detector.onTouchEvent(event));
    }

    private void showDrawer() {
        loadApps();
        adapter.filter("");
        findViewById(R.id.HomeScreen).setVisibility(View.GONE);
        findViewById(R.id.AppDrawer).setVisibility(View.VISIBLE);
        search.setText("");
        search.requestFocus();
        if (!hasKeyboard()) ((InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE))
                .showSoftInput(search, InputMethodManager.SHOW_IMPLICIT);
    }

    private void showHome() {
        findViewById(R.id.AppDrawer).setVisibility(View.GONE);
        findViewById(R.id.HomeScreen).setVisibility(View.VISIBLE);
        search.clearFocus();
        ((InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE))
                .hideSoftInputFromWindow(search.getWindowToken(), 0);
    }

    private void openApp(App app) {
        try {
            ((LauncherApps) getSystemService(Context.LAUNCHER_APPS_SERVICE))
                    .startMainActivity(app.componentName, app.userHandle, null, null);
            showHome();
        } catch (ActivityNotFoundException | SecurityException exception) {
            Toast.makeText(this, "App is unavailable", Toast.LENGTH_SHORT).show();
        }
    }

    private void launch(Intent intent) {
        if (intent == null || intent.resolveActivity(getPackageManager()) == null) {
            Toast.makeText(this, "No compatible app installed", Toast.LENGTH_SHORT).show();
            return;
        }
        startActivity(intent);
        showHome();
    }

    @Override protected void onResume() {
        super.onResume();
        refreshPeak();
        refreshMeetingCard();
        refreshTodoCard();
        ((ActivityBarView) findViewById(R.id.activity_bar)).refresh();
    }

    private void refreshPeak() {
        BatteryManager battery = (BatteryManager) getSystemService(BATTERY_SERVICE);
        int percent = battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
        TextView indicator = findViewById(R.id.battery_indicator);
        indicator.setVisibility(prefs.getBoolean("show_battery", true) ? View.VISIBLE : View.GONE);
        indicator.setText(percent >= 0 ? "battery  " + percent + "%" : "");
        TextView weather = findViewById(R.id.weather_indicator);
        String localWeather = prefs.getString("offline_weather", "").trim();
        weather.setText(localWeather);
        weather.setVisibility(prefs.getBoolean("show_weather", false) && !localWeather.isEmpty()
                ? View.VISIBLE : View.GONE);
        LinearLayout peak = findViewById(R.id.peak_widget);
        peak.setGravity(prefs.getBoolean("peak_right", false) ? android.view.Gravity.END : android.view.Gravity.START);
    }

    private void refreshMeetingCard() {
        TextView card = findViewById(R.id.meeting_card);
        card.setOnClickListener(view -> {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALENDAR)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.READ_CALENDAR}, CALENDAR_PERMISSION_REQUEST);
            } else {
                launch(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_CALENDAR));
            }
        });
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALENDAR)
                != PackageManager.PERMISSION_GRANTED) {
            card.setText("tap to show next meeting");
            return;
        }
        long now = System.currentTimeMillis();
        Calendar end = Calendar.getInstance();
        end.setTimeInMillis(now);
        end.add(Calendar.DAY_OF_YEAR, 7);
        Uri.Builder builder = CalendarContract.Instances.CONTENT_URI.buildUpon();
        ContentUris.appendId(builder, now);
        ContentUris.appendId(builder, end.getTimeInMillis());
        String[] projection = {CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN};
        try (Cursor cursor = getContentResolver().query(builder.build(), projection, null, null,
                CalendarContract.Instances.BEGIN + " ASC")) {
            if (cursor != null && cursor.moveToFirst()) {
                String title = cursor.getString(0);
                long begin = cursor.getLong(1);
                String time = new java.text.SimpleDateFormat("h:mm a", Locale.getDefault())
                        .format(new Date(begin)).toLowerCase(Locale.getDefault());
                card.setText(title + " · " + time);
            } else {
                card.setText("no upcoming meetings");
            }
        } catch (SecurityException exception) {
            card.setText("tap to show next meeting");
        }
    }

    @Override public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                                      @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CALENDAR_PERMISSION_REQUEST) refreshMeetingCard();
    }

    private void refreshTodoCard() {
        TextView card = findViewById(R.id.todo_card);
        LocalListItem first = null;
        for (LocalListItem item : todosRepository.list()) if (!item.isCompleted()) { first = item; break; }
        card.setText(first == null ? "no active tasks" : first.getText());
        card.setOnClickListener(view -> launch(new Intent(this, TodosActivity.class)));
    }

    @Override public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (findViewById(R.id.HomeScreen).getVisibility() == View.VISIBLE && hasKeyboard()) {
            int unicode = event.getUnicodeChar(event.getMetaState());
            if (Character.isValidCodePoint(unicode) && !Character.isISOControl(unicode)) {
                showDrawer();
                String typed = new String(Character.toChars(unicode));
                search.setText(typed);
                search.setSelection(typed.length());
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    private boolean hasKeyboard() {
        if (prefs.contains(HAS_KEYBOARD_PREFERENCE)) return prefs.getBoolean(HAS_KEYBOARD_PREFERENCE, false);
        Configuration c = getResources().getConfiguration();
        return c.keyboard != Configuration.KEYBOARD_NOKEYS
                && c.hardKeyboardHidden == Configuration.HARDKEYBOARDHIDDEN_NO;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
