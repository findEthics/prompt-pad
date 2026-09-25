package com.hermes.promptpad;

import android.app.*;
import android.content.*;
import android.os.Bundle;
import android.widget.TextView;

public class NotificationTarget extends Activity {
    static void post(Context ctx, String response) { publish(ctx, response, false, true); }

    static void postIncoming(Context ctx, String response) { publish(ctx, response, true, false); }

    static void postInterim(Context ctx, String response) { publish(ctx, response, false, false); }

    static void withdraw(Context ctx) { ctx.getSystemService(NotificationManager.class).cancel(71); }

    private static void publish(Context ctx, String response, boolean incoming, boolean withdraw) {
        int round = response == null ? 0 : Integer.parseInt(response.substring(1)); // synthetic R1, R2, R3
        NotificationManager nm = ctx.getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel("regression", "Regression", NotificationManager.IMPORTANCE_DEFAULT));
        // Round two updates the same key before withdrawal; other outgoing reposts withdraw first.
        if (withdraw) nm.cancel(71);
        PendingIntent open = PendingIntent.getActivity(ctx, 71, new Intent(ctx, NotificationTarget.class).putExtra("conversation", "71"), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent reply = PendingIntent.getBroadcast(ctx, 71, new Intent(ctx, ReplyReceiver.class), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
        RemoteInput input = new RemoteInput.Builder("reply").setLabel("Reply").build();
        nm.notify(72, new Notification.Builder(ctx, "regression").setSmallIcon(android.R.drawable.ic_dialog_email)
            .setGroup("regression-chat").setGroupSummary(true).build());
        Notification.Builder message = new Notification.Builder(ctx, "regression").setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentTitle(response == null || incoming ? "Regression chat" : "You").setCategory(Notification.CATEGORY_MESSAGE)
            .setGroup("regression-chat").setContentIntent(open)
            .addAction(new Notification.Action.Builder(null, "Reply", reply).addRemoteInput(input).build());
        if (response == null) message.setContentText("Original message");
        else {
            Person sender = new Person.Builder().setName("Regression chat").build();
            Person user = new Person.Builder().setName("Regression author").build();
            // Alternate the platform's null self-sender and an explicit non-"You" account Person.
            Notification.MessagingStyle style = new Notification.MessagingStyle(user)
                .addMessage(response, round * 2L, round % 2 == 1 ? null : user);
            if (incoming) style.addMessage("t" + round, round * 2L + 1, sender);
            message.setStyle(style);
        }
        nm.notify(71, message.build());
    }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        showIntent();
    }
    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        showIntent();
    }
    private void showIntent() {
        if (getIntent().getBooleanExtra("postFixture", false)) { post(this, null); finish(); return; }
        if (getIntent().getBooleanExtra("withdrawFixture", false)) { withdraw(this); finish(); return; }
        if (getIntent().getBooleanExtra("postReply", false)) { post(this, getIntent().getStringExtra("response")); finish(); return; }
        if (getIntent().getBooleanExtra("postIncoming", false)) {
            postIncoming(this, getIntent().getStringExtra("response")); finish(); return;
        }
        TextView view = new TextView(this);
        String conversation = getIntent().getStringExtra("conversation");
        view.setText(conversation == null ? "Notification app" : "Conversation " + conversation);
        setContentView(view);
    }
}
