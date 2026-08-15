package me.pompel.elauncher;

import static org.junit.Assert.assertEquals;

import android.content.Context;
import android.content.Intent;
import android.view.View;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class MainActivityGestureInstrumentedTest {
    @Test
    public void backFromDrawerReturnsToHome() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Intent intent = new Intent(context, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        MainActivity activity = (MainActivity) InstrumentationRegistry.getInstrumentation()
                .startActivitySync(intent);

        try {
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
                activity.findViewById(R.id.HomeScreen).setVisibility(View.GONE);
                activity.findViewById(R.id.AppDrawer).setVisibility(View.VISIBLE);
                activity.getOnBackPressedDispatcher().onBackPressed();
            });

            assertEquals(View.VISIBLE, activity.findViewById(R.id.HomeScreen).getVisibility());
            assertEquals(View.GONE, activity.findViewById(R.id.AppDrawer).getVisibility());
        } finally {
            activity.finish();
        }
    }
}
