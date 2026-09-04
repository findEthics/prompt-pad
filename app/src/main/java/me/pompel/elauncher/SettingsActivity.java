package me.pompel.elauncher;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.provider.Settings;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.preference.MultiSelectListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class SettingsActivity extends AppCompatActivity {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        ThemePreference.apply(this);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.settings_activity);
        ThemePreference.applyTypography(findViewById(R.id.settings_root), this);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.settings_root), (view, insets) -> {
            Insets safe = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout() | WindowInsetsCompat.Type.ime());
            view.setPadding(safe.left, safe.top, safe.right, safe.bottom);
            return insets;
        });
        if (state == null) getSupportFragmentManager().beginTransaction()
                .replace(R.id.settings, new SettingsFragment()).commit();
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() { finish(); }
        });
    }

    public static class SettingsFragment extends PreferenceFragmentCompat {
        @Override public void onCreatePreferences(Bundle state, String rootKey) {
            setPreferencesFromResource(R.xml.root_preferences, rootKey);
            Preference usage = findPreference("usage_access");
            if (usage != null) usage.setOnPreferenceClickListener(preference -> {
                startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS));
                return true;
            });
            addDistractingAppsPreference();
        }

        private void addDistractingAppsPreference() {
            PackageManager pm = requireContext().getPackageManager();
            Intent launcherQuery = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
            List<ResolveInfo> installed = new ArrayList<>(pm.queryIntentActivities(launcherQuery, 0));
            for (int i = installed.size() - 1; i >= 0; i--) {
                if (installed.get(i).activityInfo.packageName.equals(requireContext().getPackageName())) {
                    installed.remove(i);
                }
            }
            Collections.sort(installed, new Comparator<ResolveInfo>() {
                @Override public int compare(ResolveInfo first, ResolveInfo second) {
                    return first.loadLabel(pm).toString().compareToIgnoreCase(second.loadLabel(pm).toString());
                }
            });
            CharSequence[] labels = new CharSequence[installed.size()];
            CharSequence[] packages = new CharSequence[installed.size()];
            for (int i = 0; i < installed.size(); i++) {
                labels[i] = installed.get(i).loadLabel(pm);
                packages[i] = installed.get(i).activityInfo.packageName;
            }
            MultiSelectListPreference preference = new MultiSelectListPreference(requireContext());
            preference.setKey("distracting_packages");
            preference.setTitle("Distracting apps");
            preference.setSummary("Hours with 15+ minutes in a selected app turn red");
            preference.setEntries(labels);
            preference.setEntryValues(packages);
            getPreferenceScreen().addPreference(preference);
        }
    }
}
