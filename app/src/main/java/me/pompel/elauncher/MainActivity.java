package me.pompel.elauncher;

import androidx.annotation.NonNull;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.annotation.SuppressLint;
import android.Manifest;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Configuration;
import android.content.res.TypedArray;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.provider.Settings;
import android.text.Editable;
import android.text.SpannableString;
import android.text.TextWatcher;
import android.transition.Fade;
import android.transition.Transition;
import android.transition.TransitionManager;
import android.util.Log;
import android.util.TypedValue;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.KeyEvent;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private static final String NUMBER_OF_APPS = "number_of_apps_preference";
    private static final String DARK_MODE = "dark_mode_preference";

    private ArrayList<App> appList;
    private ArrayList<SpannableString> appNames;
    private EditText search;
    private SharedPreferences prefs;

    private recyclerAdapter adapter;
    private RecyclerView recyclerView;
    private CommandAdapter commandAdapter;
    private ContactsResolver contactsResolver;
    private TorchController torchController;
    private NotesRepository notesRepository;
    private TodosRepository todosRepository;
    private CommandParser commandParser;
    private String pendingPermissionCommand;
    private boolean isBackGesture = false;
    private float startX = 0f;
    private float startY = 0f;
    private boolean isLeftEdge = false;
    private boolean isRightEdge = false;

    private static final int CONTACTS_PERMISSION_REQUEST = 1001;
    private static final int CAMERA_PERMISSION_REQUEST = 1002;

    private void loadApps() {
        appList.clear();

        PackageManager packageManager = getApplicationContext().getPackageManager();
        Intent intent = new Intent(Intent.ACTION_MAIN, null);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        for (ResolveInfo info : packageManager.queryIntentActivities(intent, 0)) appList.add(new App(info.loadLabel(packageManager).toString(), info.activityInfo.packageName));
        Collections.sort(appList, (app1, app2) -> app1.appName.toString().compareToIgnoreCase(app2.appName.toString()));
        for (App app : appList) {
            appNames.add(app.appName);
        }
    }

    long keyboardActionTime = 0;

    private void keyboardAction(boolean hide) {
        // if this method has been called in the last 100 milliseconds, return
        long currentTime = System.currentTimeMillis();
        if (currentTime - 100 < keyboardActionTime) return;
        keyboardActionTime = currentTime;
        
        InputMethodManager inputManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (hide) {
            search.clearFocus();
            inputManager.hideSoftInputFromWindow(search.getWindowToken(), InputMethodManager.HIDE_NOT_ALWAYS);
        } else {
            search.requestFocus();
            if (!hasHardwareKeyboard()) {
                inputManager.showSoftInput(search, InputMethodManager.SHOW_IMPLICIT);
            }
        }
    }

    private boolean hasHardwareKeyboard() {
        Configuration configuration = getResources().getConfiguration();
        return configuration.keyboard != Configuration.KEYBOARD_NOKEYS
                && configuration.hardKeyboardHidden == Configuration.HARDKEYBOARDHIDDEN_NO;
    }

    private void changeLayout(boolean home, boolean animated) {
        if (!home) loadApps();
        if (animated) {
            Transition transition = new Fade();
            transition.setDuration(300);
            transition.addTarget(R.id.HomeScreen);
            TransitionManager.beginDelayedTransition(findViewById(R.id.MainLayout), transition);
        }
        findViewById(R.id.HomeScreen).setVisibility(home ? View.VISIBLE : View.GONE);
        findViewById(R.id.AppDrawer).setVisibility(home ? View.GONE : View.VISIBLE);
        keyboardAction(home);
    }

    private void openAppWithIntent(Intent intent, boolean change) {
        keyboardAction(true);
        search.setText("");
        safeStartActivity(intent);
        if (change) changeLayout(true, false);
    }

    private void safeStartActivity(Intent intent) {
        if (intent != null) startActivity(intent);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        switch (ev.getAction()) {
            case MotionEvent.ACTION_DOWN:
                startX = ev.getX();
                startY = ev.getY();
                // Check if touch started from left or right edge (within 50dp)
                float edgeThreshold = 50 * getResources().getDisplayMetrics().density;
                int screenWidth = getResources().getDisplayMetrics().widthPixels;
                
                isLeftEdge = startX < edgeThreshold;
                isRightEdge = startX > (screenWidth - edgeThreshold);
                isBackGesture = false;
                break;
                
            case MotionEvent.ACTION_MOVE:
                if (isLeftEdge || isRightEdge) {
                    float currentX = ev.getX();
                    float currentY = ev.getY();
                    float deltaX = currentX - startX;
                    float deltaY = currentY - startY;
                    
                    // Only consider horizontal swipes (more horizontal than vertical)
                    if (Math.abs(deltaX) > Math.abs(deltaY) && Math.abs(deltaX) > 100) {
                        if (isLeftEdge && deltaX > 0) {
                            // Left edge swipe to the right (back gesture)
                            isBackGesture = true;
                        } else if (isRightEdge && deltaX < 0) {
                            // Right edge swipe to the left (back gesture)  
                            isBackGesture = true;
                        }
                    }
                }
                break;
                
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                // Reset flags when touch ends
                break;
        }
        return super.dispatchTouchEvent(ev);
    }

    private void handleBack() {
        if (isBackGesture) {
            // Block back gestures - do nothing
        } else if (isLeftEdge) {
            launchGesturePackageOrDefault("left_gesture_package",
                getDefaultLeftGestureIntent());
        } else if (isRightEdge) {
            launchGesturePackageOrDefault("right_gesture_package",
                getDefaultBrowserIntent());
        }
        // Reset flags
        isBackGesture = false;
        isLeftEdge = false;
        isRightEdge = false;
    }

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        prefs = PreferenceManager.getDefaultSharedPreferences(this);

        // Get system dark mode as default
        boolean systemDarkMode = (getResources().getConfiguration().uiMode & 
                Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        boolean isDarkMode = prefs.getBoolean(DARK_MODE, systemDarkMode);

        if (isDarkMode) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
            setTheme(R.style.AppTheme_InvertedDark);
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
            setTheme(R.style.AppTheme);
        }

        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_main);

        View mainLayout = findViewById(R.id.MainLayout);
        ViewCompat.setOnApplyWindowInsetsListener(mainLayout, (view, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout() | WindowInsetsCompat.Type.ime());
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
        ViewCompat.requestApplyInsets(mainLayout);

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                handleBack();
            }
        });

        appList = new ArrayList<>();
        appNames = new ArrayList<>();
        loadApps();
        search = findViewById(R.id.search);
        contactsResolver = new ContactsResolver(this);
        torchController = new TorchController(this);
        SharedPreferences commandPreferences = getSharedPreferences("command_data", MODE_PRIVATE);
        notesRepository = new NotesRepository(new SharedPreferencesKeyValueStore(commandPreferences));
        todosRepository = new TodosRepository(new SharedPreferencesKeyValueStore(commandPreferences));
        commandParser = new CommandParser(contactsResolver);

        recyclerView = findViewById(R.id.recycler_view);
        adapter = new recyclerAdapter(appList, new recyclerAdapter.RecyclerViewClickListener() {
            @Override
            public void onClick(App app) {
                openAppWithIntent(getPackageManager().getLaunchIntentForPackage(app.packageId), true);
            }

            @Override
            public void onLongClick(App app) {
                Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                intent.setData(Uri.parse("package:" + app.packageId));
                openAppWithIntent(intent, false);
            }
        });
        commandAdapter = new CommandAdapter(new CommandAdapter.Listener() {
            @Override
            public void onEdit(String query) {
                search.setText(query);
                search.setSelection(query.length());
            }

            @Override
            public void onSubmit(String query) {
                if (!query.equals(search.getText().toString())) {
                    search.setText(query);
                    search.setSelection(query.length());
                }
                submitCommand();
            }

            @Override
            public void onOpenSettings() {
                openAppSettings("Allow the needed permission in Settings.");
            }
        });
        RecyclerView.LayoutManager layoutManager = new LinearLayoutManager(getApplicationContext());
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setAdapter(adapter);

        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            private boolean onTop = false;
            private boolean onBottom = false;

            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);
                if (newState == RecyclerView.SCREEN_STATE_ON) {
                    onTop = !recyclerView.canScrollVertically(-1);
                    onBottom = !recyclerView.canScrollVertically(1);
                    if (onTop) keyboardAction(true);
                } else if (newState == RecyclerView.SCREEN_STATE_OFF) {
                    if (!recyclerView.canScrollVertically(1)) {
                        if (onBottom) changeLayout(true, true);
                        else keyboardAction(true);
                    } else if (!recyclerView.canScrollVertically(-1)) {
                        if (onTop) changeLayout(true, true);
                        else keyboardAction(true);
                    }
                }
            }

            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
            }
        });

        search.setOnEditorActionListener((view, actionId, event) -> {
            boolean enterPressed = event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                    && event.getAction() == KeyEvent.ACTION_DOWN;
            if (search.getText().length() > 0 && search.getText().charAt(0) == '!'
                    && (actionId != 0 || enterPressed)) {
                submitCommand();
                return true;
            }
            return false;
        });
        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }

            @Override
            public void afterTextChanged(Editable editable) {
            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                CommandQueryClassifier.Result result = CommandQueryClassifier.classify(charSequence.toString());
                if (result.getMode() == CommandQueryClassifier.Mode.COMMAND_SEARCH) {
                    // Keep delayed app-filter results from auto-launching while commands are shown.
                    adapter.pauseFiltering();
                    if (recyclerView.getAdapter() != commandAdapter) {
                        recyclerView.setAdapter(commandAdapter);
                    }
                    if (!showContactPreview(charSequence.toString(), result)) {
                        commandAdapter.submit(result);
                    }
                } else {
                    if (recyclerView.getAdapter() != adapter) {
                        recyclerView.setAdapter(adapter);
                    }
                    adapter.filter(result.getAppQuery());
                }
            }
        });

        LinearLayout homescreen = findViewById(R.id.HomeScreen);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);

        CharSequence[] alertApps = appNames.toArray(new CharSequence[0]);
        int i = 0;
        for (i = 0; i < prefs.getInt(NUMBER_OF_APPS, 8); i++) {
            TextView textView = new TextView(this);
            textView.setTextColor(getColorFromAttr(androidx.appcompat.R.attr.colorPrimary));
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 32);
            textView.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
            textView.setPadding(0, 0, 0, 50);
            textView.setText(prefs.getString(Integer.toString(i), "App"));
            textView.setTag(i);
            textView.setLayoutParams(params);
            textView.setOnLongClickListener(v -> {
                loadApps();
                AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                builder.setTitle("Select app");
                builder.setItems(alertApps, (dialog, which) -> {
                    AlertDialog.Builder builder1 = new AlertDialog.Builder(MainActivity.this);
                    builder1.setTitle("Set app name");
                    final EditText input = new EditText(MainActivity.this);
                    input.setText(appNames.get(which));
                    builder1.setView(input);
                    input.setTag(appList.get(which).packageId);
                    builder1.setPositiveButton("Add", (dialog1, which1) -> {
                        String name = input.getText().toString();
                        textView.setText(name);
                        SharedPreferences.Editor editor = prefs.edit();
                        editor.putString(String.valueOf(textView.getTag()), name);
                        editor.putString("p" + textView.getTag(), String.valueOf(input.getTag()));
                        editor.apply();
                    });
                    builder1.create();
                    builder1.show();
                });
                builder.create();
                builder.show();
                return true;
            });
            textView.setOnClickListener(v -> openAppWithIntent(getPackageManager().getLaunchIntentForPackage(prefs.getString("p" + textView.getTag(), "")), true));
            homescreen.addView(textView);
        }

        new SwipeListener(homescreen);

    }

    private void submitCommand() {
        String query = search.getText().toString();
        if (CommandQueryClassifier.classify(query).getMode()
                != CommandQueryClassifier.Mode.COMMAND_SEARCH) {
            return;
        }

        if (isNamedTextCommandWithoutContacts(query)) {
            requestContactsFor(query);
            return;
        }

        CommandParser.ParseResult parsed = commandParser.parse(query);
        if (!parsed.isSuccess()) {
            if (parsed.getError().getCode() == CommandParser.ErrorCode.CONTACT_RESOLUTION_REQUIRED) {
                requestContactsFor(query);
            } else {
                showCommandStatus(parsed.getError().getMessage(), parsed.getError().getSyntax());
            }
            return;
        }
        executeCommand(parsed.getCommand());
    }

    private boolean showContactPreview(String query, CommandQueryClassifier.Result result) {
        if (result.getDisplayState() != CommandQueryClassifier.DisplayState.PREVIEW
                || (result.getCommand() != CommandQueryClassifier.Command.CALL
                && result.getCommand() != CommandQueryClassifier.Command.TEXT)) {
            return false;
        }
        CommandParser.ParseResult parsed = commandParser.parse(query);
        if (!parsed.isSuccess()) {
            if (isNamedTextCommandWithoutContacts(query)) {
                showCommandPermissionStatus("Contacts permission required",
                        "Allow Contacts to use a named text recipient.");
                return true;
            }
            if (contactsResolver.hasPermission()) {
                showCommandStatus(parsed.getError().getMessage(), parsed.getError().getSyntax());
                return true;
            }
            return false;
        }
        CommandParser.Recipient recipient = recipientFor(parsed.getCommand());
        if (recipient == null || recipient.getKind() == CommandParser.Recipient.Kind.PHONE_NUMBER) {
            return false;
        }
        if (!contactsResolver.hasPermission()) {
            showCommandPermissionStatus("Contacts permission required",
                    "Allow Contacts to use a named recipient.");
            return true;
        }
        List<ContactsResolver.Contact> contacts = contactsResolver.contactsFor(recipient.getValue());
        if (contacts.isEmpty()) {
            showCommandStatus("No matching contact", "Use a phone number or check the contact name.");
            return true;
        }
        if (contacts.size() > 1) {
            showCommandStatus("Contact choice required", "Submit to choose a matching contact number.");
            return true;
        }
        return false;
    }

    private boolean isNamedTextCommandWithoutContacts(String query) {
        if (contactsResolver.hasPermission()) {
            return false;
        }
        CommandParser.ParseResult withoutContacts = new CommandParser().parse(query);
        return !withoutContacts.isSuccess()
                && withoutContacts.getError().getCode()
                == CommandParser.ErrorCode.CONTACT_RESOLUTION_REQUIRED;
    }

    private static CommandParser.Recipient recipientFor(CommandParser.Command command) {
        if (command instanceof CommandParser.CallCommand) {
            return ((CommandParser.CallCommand) command).getRecipient();
        }
        if (command instanceof CommandParser.TextCommand) {
            return ((CommandParser.TextCommand) command).getRecipient();
        }
        return null;
    }

    private void executeCommand(CommandParser.Command command) {
        switch (command.getType()) {
            case HELP:
                if (recyclerView.getAdapter() != commandAdapter) {
                    recyclerView.setAdapter(commandAdapter);
                }
                commandAdapter.submit(CommandQueryClassifier.classify("!"));
                return;
            case CALL:
                executeCall((CommandParser.CallCommand) command);
                return;
            case TEXT:
                executeText((CommandParser.TextCommand) command);
                return;
            case TIMER:
                CommandParser.TimerCommand timer = (CommandParser.TimerCommand) command;
                launchCommandIntent(CommandIntentFactory.setTimer(timer.getDurationMinutes(), timer.getLabel()),
                        "No Clock app is available.", "Timer form opened.");
                return;
            case TODO:
                Todo todo = todosRepository.add(((CommandParser.TodoCommand) command).getText());
                showCommandStatus("To-do saved", todo.getText());
                return;
            case TODOS:
                launchCommandIntent(new Intent(this, TodosActivity.class),
                        "The to-do list is unavailable.", "Opening to-dos.");
                return;
            case NOTE:
                Note note = notesRepository.add(((CommandParser.NoteCommand) command).getText());
                showCommandStatus("Note saved", note.getText());
                return;
            case NOTES:
                launchCommandIntent(new Intent(this, NotesActivity.class),
                        "The notes list is unavailable.", "Opening notes.");
                return;
            case EVENT:
                CommandParser.EventCommand event = (CommandParser.EventCommand) command;
                launchCommandIntent(CommandIntentFactory.insertEvent(event), "No Calendar app is available.",
                        "Calendar event form opened.");
                return;
            case TORCH:
                toggleTorch();
                return;
            case CAMERA:
                launchCommandIntent(CommandIntentFactory.camera(),
                        "No camera app is available.", "Camera opened.");
                return;
        }
    }

    private void executeCall(CommandParser.CallCommand command) {
        executeRecipient(command.getRecipient(), new ContactNumberAction() {
            @Override
            public void open(String number) {
                launchCommandIntent(CommandIntentFactory.dial(number),
                        "No dialer is available.", "Dialer opened with " + number + ".");
            }
        });
    }

    private void executeText(final CommandParser.TextCommand command) {
        executeRecipient(command.getRecipient(), new ContactNumberAction() {
            @Override
            public void open(String number) {
                launchCommandIntent(CommandIntentFactory.composeText(number, command.getMessage()),
                        "No SMS app is available.",
                        "SMS composer opened for " + number + ".");
            }
        });
    }

    private void executeRecipient(CommandParser.Recipient recipient, ContactNumberAction action) {
        if (recipient.getKind() == CommandParser.Recipient.Kind.PHONE_NUMBER) {
            action.open(recipient.getValue());
            return;
        }
        if (!contactsResolver.hasPermission()) {
            requestContactsFor(search.getText().toString());
            return;
        }
        List<ContactsResolver.Contact> contacts = contactsResolver.contactsFor(recipient.getValue());
        if (contacts.isEmpty()) {
            showCommandStatus("No matching contact", "Use !call or !text with a phone number.");
            return;
        }
        if (contacts.size() == 1) {
            action.open(contacts.get(0).getNumber());
            return;
        }

        String[] labels = new String[contacts.size()];
        for (int index = 0; index < contacts.size(); index++) {
            labels[index] = contacts.get(index).getLabel();
        }
        new AlertDialog.Builder(this)
                .setTitle("Choose a contact number")
                .setItems(labels, (dialog, which) -> action.open(contacts.get(which).getNumber()))
                .show();
    }

    private void toggleTorch() {
        showCommandStatus("Checking torch", "Reading the rear torch state.");
        torchController.toggle(new TorchController.Callback() {
            @Override
            public void onResult(final TorchController.Result result) {
                runOnUiThread(() -> {
                    if ("!torch".equalsIgnoreCase(search.getText().toString().trim())) {
                        showTorchResult(result);
                    }
                });
            }
        });
    }

    private void showTorchResult(TorchController.Result result) {
        switch (result) {
            case ON:
                showCommandStatus("Torch on", "Rear torch enabled.");
                return;
            case OFF:
                showCommandStatus("Torch off", "Rear torch disabled.");
                return;
            case PERMISSION_DENIED:
                requestCameraForTorch();
                return;
            case UNAVAILABLE:
                showCommandStatus("Torch unavailable", "No rear torch is available.");
                return;
        }
    }

    private void launchCommandIntent(Intent intent, String unavailableMessage, String confirmationMessage) {
        if (intent.resolveActivity(getPackageManager()) == null) {
            showCommandStatus("Unavailable", unavailableMessage);
            return;
        }
        try {
            startActivity(intent);
            showCommandStatus("Ready", confirmationMessage);
        } catch (ActivityNotFoundException | SecurityException exception) {
            showCommandStatus("Unavailable", unavailableMessage);
        }
    }

    private void requestContactsFor(String query) {
        pendingPermissionCommand = query;
        requestPermission(Manifest.permission.READ_CONTACTS, CONTACTS_PERMISSION_REQUEST,
                "Contacts permission is required for named recipients.");
    }

    private void requestCameraForTorch() {
        pendingPermissionCommand = search.getText().toString();
        requestPermission(Manifest.permission.CAMERA, CAMERA_PERMISSION_REQUEST,
                "Camera permission is required for torch.");
    }

    private void requestPermission(String permission, int requestCode, String denialMessage) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            showPermissionSettings(denialMessage);
            return;
        }
        if (checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED) {
            submitPendingPermissionCommand();
            return;
        }
        requestPermissions(new String[]{permission}, requestCode);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        boolean granted = grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED;
        if (granted) {
            submitPendingPermissionCommand();
        } else if (requestCode == CONTACTS_PERMISSION_REQUEST) {
            showPermissionSettings("Contacts permission is required for named recipients.");
        } else if (requestCode == CAMERA_PERMISSION_REQUEST) {
            showPermissionSettings("Camera permission is required for torch.");
        }
    }

    private void submitPendingPermissionCommand() {
        if (pendingPermissionCommand == null) {
            return;
        }
        String query = pendingPermissionCommand;
        pendingPermissionCommand = null;
        if (!query.equals(search.getText().toString())) {
            search.setText(query);
            search.setSelection(query.length());
        }
        submitCommand();
    }

    private void showPermissionSettings(String message) {
        showCommandStatus("Permission required", message);
        new AlertDialog.Builder(this)
                .setMessage(message)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton("Open settings", (dialog, which) -> openAppSettings(message))
                .show();
    }

    private void showCommandStatus(String title, String detail) {
        adapter.pauseFiltering();
        if (recyclerView.getAdapter() != commandAdapter) {
            recyclerView.setAdapter(commandAdapter);
        }
        commandAdapter.showStatus(title, detail);
    }

    private void showCommandPermissionStatus(String title, String detail) {
        adapter.pauseFiltering();
        if (recyclerView.getAdapter() != commandAdapter) {
            recyclerView.setAdapter(commandAdapter);
        }
        commandAdapter.showPermissionStatus(title, detail);
    }

    private void openAppSettings(String fallbackMessage) {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.parse("package:" + getPackageName()));
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException ignored) {
            Toast.makeText(this, fallbackMessage, Toast.LENGTH_LONG).show();
        }
    }

    private interface ContactNumberAction {
        void open(String number);
    }

    private boolean canOpenDialer() {
        PackageManager packageManager = getPackageManager();
        Intent intent = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:"));
        return intent.resolveActivity(packageManager) != null;
    }

    private String getDefaultBrowserPackage() {
        Intent browserIntent = new Intent("android.intent.action.VIEW", Uri.parse("http://"));
        ResolveInfo resolveInfo = getPackageManager().resolveActivity(browserIntent,PackageManager.MATCH_DEFAULT_ONLY);

        if (resolveInfo == null) return null;

        // This is the default browser's packageName
        return resolveInfo.activityInfo.packageName;
    }

    private Intent getDefaultBrowserIntent() {
        String pkg = getDefaultBrowserPackage();

        // if there is no default browser, return default browser selection intent
        if (pkg == null || pkg.equals("android")) {
            Intent selector = new Intent(Intent.ACTION_VIEW);
            selector.setData(Uri.parse("http://"));
            return selector;
        }

        return getPackageManager().getLaunchIntentForPackage(pkg);
    }

    private Intent getDefaultLeftGestureIntent() {
        return canOpenDialer()
                ? new Intent(Intent.ACTION_DIAL, Uri.parse("tel:"))
                : new Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA);
    }

    private List<ResolveInfo> getLaunchersResolveInfos() {
        List<ResolveInfo> launchers = new LinkedList<ResolveInfo>();
        PackageManager packageManager = getPackageManager();
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        intent.addCategory(Intent.CATEGORY_HOME);
        intent.addCategory(Intent.CATEGORY_DEFAULT);

        List<ResolveInfo> resolveInfoList = packageManager.queryIntentActivities(intent, 0);
        String currentPackageName = getPackageName();

        for (ResolveInfo resolveInfo : resolveInfoList) {
            String packageName = resolveInfo.activityInfo.packageName;
            if (!packageName.equals(currentPackageName)) {
                launchers.add(resolveInfo);
            }
        }

        return launchers;
    }

    public Intent getLastLauncherIntent() {
        ResolveInfo[] launcherResolveInfos = getLaunchersResolveInfos().toArray(new ResolveInfo[0]);
        ResolveInfo lastLauncher = launcherResolveInfos[launcherResolveInfos.length-1];

        if (lastLauncher != null) {
            String packageName = lastLauncher.activityInfo.packageName;

            Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.addCategory(Intent.CATEGORY_LAUNCHER);
            intent.addCategory(Intent.CATEGORY_HOME);
            intent.addCategory(Intent.CATEGORY_DEFAULT);
            intent.setPackage(packageName);
            intent.setClassName(packageName, lastLauncher.activityInfo.name);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);

            return intent;
        } else {
            return null;
        }
    }

    private void launchGesturePackageOrDefault(String prefKey, Intent defaultIntent) {
        String pkg = prefs.getString(prefKey, "");
        if (pkg.isEmpty()) {
            safeStartActivity(defaultIntent);
            return;
        }
        Intent intent = getPackageManager().getLaunchIntentForPackage(pkg);
        if (intent != null) {
            safeStartActivity(intent);
        } else {
            prefs.edit().remove(prefKey).apply();
            safeStartActivity(defaultIntent);
        }
    }

    private class SwipeListener implements View.OnTouchListener {
        private final GestureDetector gestureDetector;

        SwipeListener(View view) {
            GestureDetector.SimpleOnGestureListener simple = new GestureDetector.SimpleOnGestureListener() {
                @Override public boolean onDown(@NonNull MotionEvent e) { return true; }
                @SuppressWarnings("JavaReflectionMemberAccess") @SuppressLint({"WrongConstant"}) @Override public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                    assert e1 != null;
                    float xDiff = e2.getX() - e1.getX();
                    float yDiff = e2.getY() - e1.getY();
                    if (Math.abs(xDiff) > Math.abs(yDiff) && Math.abs(xDiff) > 100 && Math.abs(velocityX) > 100) {
                        if (xDiff > 0) {
                            launchGesturePackageOrDefault("right_gesture_package",
                                getDefaultBrowserIntent());
                        } else {
                            launchGesturePackageOrDefault("left_gesture_package",
                                getDefaultLeftGestureIntent());
                        }
                    }
                    else if (Math.abs(yDiff) > 100 && Math.abs(velocityY) > 100) {
                        if (yDiff > 0)
                            try { Class.forName("android.app.StatusBarManager").getMethod("expandNotificationsPanel").invoke(getSystemService("statusbar")); }
                            catch (Exception e) { Log.d(App.class.toString(), SwipeListener.class+": onFling", e); }
                        else {
                            changeLayout(false, true);
                        }
                    }
                    return true;
                }

                @Override public boolean onDoubleTap(@NonNull MotionEvent e) {
                    openAppWithIntent(getLastLauncherIntent(), true);
                    return true;
                }

                @Override public void onLongPress(@NonNull MotionEvent e) {
                    super.onLongPress(e);

                    // start SettingsActivity
                    Intent intent = new Intent(MainActivity.this, SettingsActivity.class);
                    startActivity(intent);
                }
            };
            gestureDetector = new GestureDetector(getApplicationContext(), simple);
            view.setOnTouchListener(this);
        }
        @Override public boolean onTouch (View view, MotionEvent motionEvent) { view.performClick(); return gestureDetector.onTouchEvent(motionEvent); }
    }

    private int getColorFromAttr(int attr) {
        TypedValue typedValue = new TypedValue();
        int color;
        try (TypedArray a = obtainStyledAttributes(typedValue.data, new int[]{attr})) {
            color = a.getColor(0, 0);
        }
        return color;
    }
}
