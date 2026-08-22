package me.pompel.elauncher;

import android.content.ComponentName;
import android.os.UserHandle;
import android.text.SpannableString;

public class App {
    public SpannableString appName;
    public String packageId;
    public ComponentName componentName;
    public UserHandle userHandle;

    public App(String appName, ComponentName componentName, UserHandle userHandle) {
        this.appName = new SpannableString(appName);
        this.packageId = componentName.getPackageName();
        this.componentName = componentName;
        this.userHandle = userHandle;
    }
}
