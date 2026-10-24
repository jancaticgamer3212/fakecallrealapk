package com.example.fakecall;

import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;

import java.util.Arrays;

/**
 * Hands an incoming call to the phone's own Telecom service so the stock call screen,
 * ringtone and call log are used. Needs the app installed as a system (priv-app) app on
 * Android 5.x. Every method returns an error string (or null on success) instead of throwing,
 * so the caller can fall back to the simulated screen.
 */
final class RealCall {

    static final String ACCOUNT_ID = "fakecall_account";
    static final String P_NAME = "rc_name";
    static final String P_NUMBER = "rc_number";

    private RealCall() {
    }

    static PhoneAccountHandle handle(Context c) {
        return new PhoneAccountHandle(new ComponentName(c, FakeConnectionService.class), ACCOUNT_ID);
    }

    /** Registers the call-provider account with Telecom. Returns null on success. */
    static String register(Context c) {
        try {
            TelecomManager tm = (TelecomManager) c.getSystemService(Context.TELECOM_SERVICE);
            if (tm == null) return "no Telecom service";
            PhoneAccount account = PhoneAccount.builder(handle(c), "Fake Call")
                    .setCapabilities(PhoneAccount.CAPABILITY_CALL_PROVIDER)
                    .setSupportedUriSchemes(Arrays.asList("tel"))
                    .build();
            tm.registerPhoneAccount(account);
            return null;
        } catch (Throwable t) {
            return describe(t);
        }
    }

    /** Starts a real incoming call. Returns null on success, otherwise the reason it failed. */
    static String start(Context c, String name, String number) {
        String err = register(c);
        if (err != null) return err;
        try {
            SharedPreferences p = c.getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE);
            p.edit().putString(P_NAME, name == null ? "" : name)
                    .putString(P_NUMBER, number == null ? "" : number).commit();
            TelecomManager tm = (TelecomManager) c.getSystemService(Context.TELECOM_SERVICE);
            tm.addNewIncomingCall(handle(c), new Bundle());
            return null;
        } catch (Throwable t) {
            return describe(t);
        }
    }

    private static String describe(Throwable t) {
        String m = t.getMessage();
        return t.getClass().getSimpleName() + (m == null ? "" : ": " + m);
    }
}
