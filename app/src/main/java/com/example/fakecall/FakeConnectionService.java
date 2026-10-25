package com.example.fakecall;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.telecom.Connection;
import android.telecom.ConnectionRequest;
import android.telecom.ConnectionService;
import android.telecom.DisconnectCause;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;

import java.io.File;

/**
 * Tells Telecom about the fake call so the phone's own incoming-call UI is used.
 * The stock call screen can't show our caller photo; the answer audio does play once answered.
 */
public class FakeConnectionService extends ConnectionService {

    private static final long RING_TIMEOUT_MS = 45000;
    private static final long VOICE_DELAY_MS = 700; // let Telecom switch the audio to call mode first

    @Override
    public Connection onCreateIncomingConnection(PhoneAccountHandle account,
                                                 ConnectionRequest request) {
        SharedPreferences p = getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE);
        return new FakeConnection(getApplicationContext(),
                p.getString(RealCall.P_NAME, ""),
                p.getString(RealCall.P_NUMBER, ""));
    }

    static class FakeConnection extends Connection {
        private final Context ctx;
        private final Handler handler = new Handler(Looper.getMainLooper());
        private MediaPlayer voice;

        private final Runnable missed = new Runnable() {
            @Override
            public void run() {
                end(DisconnectCause.MISSED);
            }
        };

        private final Runnable startVoice = new Runnable() {
            @Override
            public void run() {
                File f = Media.audioFile(ctx);
                if (!f.exists()) return;
                try {
                    voice = new MediaPlayer();
                    voice.setDataSource(f.getPath());
                    voice.setAudioStreamType(AudioManager.STREAM_VOICE_CALL);
                    voice.prepare();
                    voice.start();
                } catch (Exception e) {
                    stopVoice();
                }
            }
        };

        FakeConnection(Context ctx, String name, String number) {
            this.ctx = ctx;
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

        private void stopVoice() {
            handler.removeCallbacks(startVoice);
            if (voice != null) {
                try {
                    voice.stop();
                } catch (Exception ignored) {
                }
                voice.release();
                voice = null;
            }
        }

        private void end(int cause) {
            handler.removeCallbacks(missed);
            stopVoice();
            setDisconnected(new DisconnectCause(cause));
            destroy();
        }

        private void answered() {
            handler.removeCallbacks(missed);
            setActive();
            handler.postDelayed(startVoice, VOICE_DELAY_MS);
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
