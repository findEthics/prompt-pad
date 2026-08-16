package me.pompel.elauncher;

import android.content.Intent;
import android.net.Uri;
import android.provider.AlarmClock;
import android.provider.CalendarContract;
import android.provider.MediaStore;

/** Creates intents for the supported command actions. */
public final class CommandIntentFactory {
    private CommandIntentFactory() {
    }

    public static Intent dial(String number) {
        return new Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", number, null));
    }

    public static Intent composeText(String number, String message) {
        return new Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", number, null))
                .putExtra("sms_body", message);
    }

    public static Intent openTelegram(String username, String message) {
        Uri uri = new Uri.Builder().scheme("tg").authority("resolve")
                .appendQueryParameter("domain", username)
                .appendQueryParameter("text", message)
                .build();
        return new Intent(Intent.ACTION_VIEW, uri);
    }

    public static Intent setTimer(int durationSeconds, String label) {
        Intent intent = new Intent(AlarmClock.ACTION_SET_TIMER)
                .putExtra(AlarmClock.EXTRA_LENGTH, durationSeconds);
        if (label != null && !label.isEmpty()) {
            intent.putExtra(AlarmClock.EXTRA_MESSAGE, label);
        }
        return intent;
    }

    public static Intent setAlarm(int hour, int minute) {
        return new Intent(AlarmClock.ACTION_SET_ALARM)
                .putExtra(AlarmClock.EXTRA_HOUR, hour)
                .putExtra(AlarmClock.EXTRA_MINUTES, minute)
                .putExtra(AlarmClock.EXTRA_SKIP_UI, true);
    }

    public static Intent insertEvent(CommandParser.EventCommand event) {
        return new Intent(Intent.ACTION_INSERT)
                .setData(CalendarContract.Events.CONTENT_URI)
                .putExtra(CalendarContract.Events.TITLE, event.getTitle())
                .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, event.getStartTimeMillis())
                .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, event.getStartTimeMillis()
                        + event.getDurationMinutes() * 60L * 1000L);
    }

    public static Intent camera() {
        return new Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA);
    }
}
