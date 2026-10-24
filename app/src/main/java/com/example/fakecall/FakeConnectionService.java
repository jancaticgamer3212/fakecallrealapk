package com.example.fakecall;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.telecom.Connection;
import android.telecom.ConnectionRequest;
import android.telecom.ConnectionService;
import android.telecom.DisconnectCause;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;

/** Tells Telecom about the fake call so the phone's own incoming-call UI is used. */
public class FakeConnectionService extends ConnectionService {

    private static final long RING_TIMEOUT_MS = 45000;

    @Override
    public Connection onCreateIncomingConnection(PhoneAccountHandle account,
                                                 ConnectionRequest request) {
        SharedPreferences p = getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE);
        return new FakeConnection(p.getString(RealCall.P_NAME, ""),
                p.getString(RealCall.P_NUMBER, ""));
    }

    static class FakeConnection extends Connection {
        private final Handler handler = new Handler(Looper.getMainLooper());
        private final Runnable missed = new Runnable() {
            @Override
            public void run() {
                end(DisconnectCause.MISSED);
            }
        };

        FakeConnection(String name, String number) {
            if (number == null || number.isEmpty()) {
                setAddress(null, TelecomManager.PRESENTATION_UNKNOWN);
            } else {
                setAddress(Uri.fromParts("tel", number, null), TelecomManager.PRESENTATION_ALLOWED);
            }
            if (name != null && !name.isEmpty()) {
                setCallerDisplayName(name, TelecomManager.PRESENTATION_ALLOWED);
            }
            setRinging();
            handler.postDelayed(missed, RING_TIMEOUT_MS);
        }

        private void end(int cause) {
            handler.removeCallbacks(missed);
            setDisconnected(new DisconnectCause(cause));
            destroy();
        }

        private void answered() {
            handler.removeCallbacks(missed);
            setActive();
        }

        @Override
        public void onAnswer() {
            answered();
        }

        @Override
        public void onAnswer(int videoState) {
            answered();
        }

        @Override
        public void onReject() {
            end(DisconnectCause.REJECTED);
        }

        @Override
        public void onDisconnect() {
            end(DisconnectCause.LOCAL);
        }

        @Override
        public void onAbort() {
            end(DisconnectCause.CANCELED);
        }
    }
}
