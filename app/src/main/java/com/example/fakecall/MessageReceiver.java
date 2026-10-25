package com.example.fakecall;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Fires when the scheduled alarm goes off and posts the fake message. */
public class MessageReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        context.getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE)
                .edit().putLong(MainActivity.K_MSG_WHEN, 0).apply();

        Messages.show(context,
                intent.getStringExtra(MainActivity.EXTRA_NAME),
                intent.getStringExtra(MainActivity.EXTRA_TEXT));
    }
}
