package me.pompel.elauncher;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.os.Build;
import android.view.accessibility.AccessibilityEvent;

/** Accessibility-backed lock action borrowed from Katapult. */
public final class TapToSleepAccessibilityService extends AccessibilityService {
    private static volatile TapToSleepAccessibilityService activeService;

    @Override protected void onServiceConnected() { activeService = this; }
    @Override public void onAccessibilityEvent(AccessibilityEvent event) { }
    @Override public void onInterrupt() { }

    @Override public boolean onUnbind(Intent intent) {
        if (activeService == this) activeService = null;
        return super.onUnbind(intent);
    }

    static boolean lockScreen() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                && activeService != null
                && activeService.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN);
    }
}
