package com.hermes.promptpad;

import android.app.RemoteInput;
import android.content.*;
import android.os.Bundle;

public class ReplyReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context ctx, Intent intent) {
        Bundle results = RemoteInput.getResultsFromIntent(intent);
        if (results != null && results.getCharSequence("reply") != null) {
            String response = results.getCharSequence("reply").toString();
            // Round two first updates the live key; the runner withdraws and reposts it afterwards.
            if (response.equals("R2")) NotificationTarget.postInterim(ctx, response);
            else NotificationTarget.post(ctx, response);
        }
    }
}
