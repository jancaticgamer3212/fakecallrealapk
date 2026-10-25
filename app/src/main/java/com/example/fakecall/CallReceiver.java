package com.example.fakecall;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.PowerManager;
import android.widget.Toast;

/** Fires when the alarm goes off and shows the fake incoming call. */
public class CallReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        wakeScreen(context);

        context.getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE)
                .edit().putLong(MainActivity.K_WHEN, 0).apply();

        trigger(context,
                intent.getStringExtra(MainActivity.EXTRA_NAME),
                intent.getStringExtra(MainActivity.EXTRA_NUMBER));
    }

    /** Turns the screen on for a few seconds so a call or message shows up on a sleeping phone. */
    static void wakeScreen(Context context) {
        try {
            PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            PowerManager.WakeLock wl = pm.newWakeLock(
                    PowerManager.FULL_WAKE_LOCK
                            | PowerManager.ACQUIRE_CAUSES_WAKEUP
                            | PowerManager.ON_AFTER_RELEASE,
                    "fakecall:wake");
            wl.acquire(10000);
        } catch (Exception ignored) {
        }
    }

    /** Uses the phone's real call screen if enabled and working, else the simulated one. */
    static void trigger(Context context, String name, String number) {
        boolean real = context.getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE)
                .getBoolean(MainActivity.K_REAL, false);
        if (real) {
            String err = RealCall.start(context, name, number);
            if (err == null) return;
            Toast.makeText(context, "Real call screen failed (" + err + ") - using simulated screen",
                    Toast.LENGTH_LONG).show();
        }
        showIncomingCall(context, name, number);
    }

    static void showIncomingCall(Context context, String name, String number) {
        Intent i = new Intent(context, IncomingCallActivity.class);
        i.putExtra(MainActivity.EXTRA_NAME, name);
        i.putExtra(MainActivity.EXTRA_NUMBER, number);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        context.startActivity(i);
    }
}
