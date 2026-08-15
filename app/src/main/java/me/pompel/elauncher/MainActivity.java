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
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Configuration;
import android.content.res.TypedArray;
import android.graphics.Typeface;
import android.net.Uri;
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
    private boolean isBackGesture = false;
    private float startX = 0f;
    private float startY = 0f;
    private boolean isLeftEdge = false;
    private boolean isRightEdge = false;

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

        RecyclerView recyclerView = findViewById(R.id.recycler_view);
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

        search = findViewById(R.id.search);
        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }

            @Override
            public void afterTextChanged(Editable editable) {
            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                adapter.getFilter().filter(charSequence);
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
