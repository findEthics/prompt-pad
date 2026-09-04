package me.pompel.elauncher;

import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import androidx.preference.PreferenceManager;

import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/** Compact 24-hour activity strip. Past/offline hours are white; distracting use is red. */
public final class ActivityBarView extends View {
    private static final int HOURS = 24;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final boolean[] distracting = new boolean[HOURS];

    public ActivityBarView(Context context, AttributeSet attrs) {
        super(context, attrs);
        paint.setStyle(Paint.Style.FILL);
        setMinimumHeight(Math.round(dp(24)));
    }

    public void refresh() {
        for (int hour = 0; hour < HOURS; hour++) distracting[hour] = false;
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getContext());
        Set<String> blocked = preferences.getStringSet("distracting_packages", Collections.emptySet());
        if (!blocked.isEmpty()) {
            UsageStatsManager manager = (UsageStatsManager) getContext().getSystemService(Context.USAGE_STATS_SERVICE);
            Calendar start = Calendar.getInstance();
            start.set(Calendar.HOUR_OF_DAY, 0);
            start.set(Calendar.MINUTE, 0);
            start.set(Calendar.SECOND, 0);
            start.set(Calendar.MILLISECOND, 0);
            long dayStart = start.getTimeInMillis();
            List<UsageStats> stats = manager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY,
                    dayStart, System.currentTimeMillis());
            for (UsageStats stat : stats) {
                if (blocked.contains(stat.getPackageName()) && stat.getTotalTimeInForeground() >= 15 * 60 * 1000L) {
                    Calendar seen = Calendar.getInstance();
                    seen.setTimeInMillis(Math.max(dayStart, stat.getLastTimeUsed()));
                    distracting[seen.get(Calendar.HOUR_OF_DAY)] = true;
                }
            }
        }
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float usable = getWidth() - getPaddingLeft() - getPaddingRight();
        float step = usable / HOURS;
        float radius = Math.min(dp(4), step * 0.32f);
        int currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        float centerY = getHeight() / 2f;
        for (int hour = 0; hour < HOURS; hour++) {
            float centerX = getPaddingLeft() + step * (hour + 0.5f);
            if (distracting[hour]) {
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(Color.rgb(255, 69, 58));
            } else if (hour <= currentHour) {
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(Color.WHITE);
            } else {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(dp(1));
                paint.setColor(Color.rgb(68, 68, 70));
            }
            canvas.drawCircle(centerX, centerY, radius, paint);
        }
    }

    private float dp(int value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
