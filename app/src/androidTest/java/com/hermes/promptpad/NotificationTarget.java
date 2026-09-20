package com.hermes.promptpad;

import android.app.*;
import android.content.*;
import android.os.Bundle;
import android.widget.TextView;

public class NotificationTarget extends Activity {
    static void post(Context ctx, String response) {
        NotificationManager nm = ctx.getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel("regression", "Regression", NotificationManager.IMPORTANCE_DEFAULT));
        PendingIntent open = PendingIntent.getActivity(ctx, 71, new Intent(ctx, NotificationTarget.class).putExtra("conversation", "71"), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent reply = PendingIntent.getBroadcast(ctx, 71, new Intent(ctx, ReplyReceiver.class), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
        RemoteInput input = new RemoteInput.Builder("reply").setLabel("Reply").build();
        nm.notify(72, new Notification.Builder(ctx, "regression").setSmallIcon(android.R.drawable.ic_dialog_email)
            .setGroup("regression-chat").setGroupSummary(true).build());
        nm.notify(71, new Notification.Builder(ctx, "regression").setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentTitle("Regression chat").setContentText(response == null ? "Original message" : response)
            .setCategory(Notification.CATEGORY_MESSAGE).setGroup("regression-chat").setContentIntent(open)
            .addAction(new Notification.Action.Builder(null, "Reply", reply).addRemoteInput(input).build()).build());
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
        TextView view = new TextView(this);
        String conversation = getIntent().getStringExtra("conversation");
        view.setText(conversation == null ? "Notification app" : "Conversation " + conversation);
        setContentView(view);
    }
}
