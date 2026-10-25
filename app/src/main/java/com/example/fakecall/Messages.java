package com.example.fakecall;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;

/** Shows a fake incoming message as a normal notification that opens a small chat screen. */
final class Messages {

    static final int NOTIF_ID = 2001;

    private Messages() {
    }

    static void show(Context c, String sender, String text) {
        if (sender == null) sender = "";
        if (text == null) text = "";
        long now = System.currentTimeMillis();

        CallReceiver.wakeScreen(c);

        Intent open = new Intent(c, MessageActivity.class);
        open.putExtra(MainActivity.EXTRA_NAME, sender);
        open.putExtra(MainActivity.EXTRA_TEXT, text);
        open.putExtra(MainActivity.EXTRA_TIME, now);
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(c, 3, open, PendingIntent.FLAG_UPDATE_CURRENT);

        Notification.Builder b = new Notification.Builder(c)
                .setSmallIcon(android.R.drawable.stat_notify_chat)
                .setContentTitle(sender)
                .setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text))
                .setContentIntent(pi)
                .setAutoCancel(true)
                .setWhen(now)
                .setDefaults(Notification.DEFAULT_ALL)
                .setPriority(Notification.PRIORITY_HIGH)
                .setCategory(Notification.CATEGORY_MESSAGE)
                .setVisibility(Notification.VISIBILITY_PUBLIC);

        Bitmap photo = Media.loadCaller(c, 128);
        if (photo != null) b.setLargeIcon(Media.circle(photo, 128));

        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        nm.notify(NOTIF_ID, b.build());
    }
}
