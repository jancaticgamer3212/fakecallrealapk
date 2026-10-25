package com.example.fakecall;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Vibrator;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Locale;

/**
 * Full-screen fake incoming call: rings and vibrates; once answered it shows an in-call timer
 * and plays the chosen "answer" audio (if any) through the earpiece.
 */
public class IncomingCallActivity extends Activity {

    private static final long RING_TIMEOUT_MS = 45000;

    private final Handler handler = new Handler();
    private MediaPlayer player;        // ringtone
    private MediaPlayer voicePlayer;   // answer audio
    private Vibrator vibrator;
    private TextView statusView, timerView, speakerButton;
    private View ringButtons, endButton;
    private long answeredAt;
    private boolean hasAudio;
    private boolean speakerOn;

    // audio state to put back when the screen closes
    private boolean audioChanged;
    private int savedMode;
    private boolean savedSpeaker;

    private final Runnable missedCall = new Runnable() {
        @Override
        public void run() {
            finish();
        }
    };

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            long s = (System.currentTimeMillis() - answeredAt) / 1000;
            timerView.setText(String.format(Locale.US, "%02d:%02d", s / 60, s % 60));
            handler.postDelayed(this, 500);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
                | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        String name = nonNull(getIntent().getStringExtra(MainActivity.EXTRA_NAME));
        String number = nonNull(getIntent().getStringExtra(MainActivity.EXTRA_NUMBER));
        String title = name.isEmpty() ? number : name;
        String subtitle = name.isEmpty() ? "" : number;
        hasAudio = Media.audioFile(this).exists();

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setBackgroundColor(0xFF12161B);
        root.setPadding(dp(24), dp(56), dp(24), dp(40));

        statusView = label("Incoming call", 16, 0xFFB0B7C0, false);
        root.addView(statusView);

        int avatarSize = dp(132);
        LinearLayout.LayoutParams avatarLp = new LinearLayout.LayoutParams(avatarSize, avatarSize);
        avatarLp.topMargin = dp(28);
        root.addView(Media.avatarView(this, name, avatarSize), avatarLp);

        TextView nameView = label(title, 34, Color.WHITE, true);
        nameView.setPadding(0, dp(20), 0, 0);
        TextView numberView = label(subtitle, 18, 0xFFB0B7C0, false);
        numberView.setPadding(0, dp(8), 0, 0);
        timerView = label("00:00", 22, 0xFFB0B7C0, false);
        timerView.setPadding(0, dp(16), 0, 0);
        timerView.setVisibility(View.GONE);

        root.addView(nameView);
        root.addView(numberView);
        root.addView(timerView);

        root.addView(new View(this), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        speakerButton = pill("Speaker: off", 0xFF3A4350);
        speakerButton.setVisibility(View.GONE);
        LinearLayout.LayoutParams spLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        spLp.bottomMargin = dp(16);
        root.addView(speakerButton, spLp);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        TextView decline = pill("Decline", 0xFFD93025);
        TextView accept = pill("Accept", 0xFF1E9E4A);
        LinearLayout.LayoutParams lpL = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lpL.rightMargin = dp(8);
        LinearLayout.LayoutParams lpR = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lpR.leftMargin = dp(8);
        row.addView(decline, lpL);
        row.addView(accept, lpR);
        ringButtons = row;
        root.addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView end = pill("End call", 0xFFD93025);
        end.setVisibility(View.GONE);
        endButton = end;
        root.addView(end, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        decline.setOnClickListener(v -> finish());
        end.setOnClickListener(v -> finish());
        accept.setOnClickListener(v -> answer());
        speakerButton.setOnClickListener(v -> toggleSpeaker());

        setContentView(root);

        startRinging();
        handler.postDelayed(missedCall, RING_TIMEOUT_MS);
    }

    private void answer() {
        stopRinging();
        handler.removeCallbacks(missedCall);
        statusView.setText("Call in progress");
        ringButtons.setVisibility(View.GONE);
        endButton.setVisibility(View.VISIBLE);
        timerView.setVisibility(View.VISIBLE);
        answeredAt = System.currentTimeMillis();
        handler.post(tick);

        if (hasAudio) {
            speakerButton.setVisibility(View.VISIBLE);
            startVoice();
        }
    }

    /** Plays the answer audio the way a call is heard: voice-call stream, earpiece. */
    private void startVoice() {
        try {
            AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            savedMode = am.getMode();
            savedSpeaker = am.isSpeakerphoneOn();
            audioChanged = true;
            am.setMode(AudioManager.MODE_IN_CALL);
            am.setSpeakerphoneOn(false);

            voicePlayer = new MediaPlayer();
            voicePlayer.setDataSource(Media.audioFile(this).getPath());
            voicePlayer.setAudioStreamType(AudioManager.STREAM_VOICE_CALL);
            voicePlayer.prepare();
            voicePlayer.start();
        } catch (Exception e) {
            releaseVoice();
        }
    }

    private void toggleSpeaker() {
        speakerOn = !speakerOn;
        AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        am.setSpeakerphoneOn(speakerOn);
        speakerButton.setText(speakerOn ? "Speaker: on" : "Speaker: off");
    }

    private void startRinging() {
        AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        int mode = am.getRingerMode();

        if (mode == AudioManager.RINGER_MODE_NORMAL) {
            try {
                Uri uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
                player = new MediaPlayer();
                player.setDataSource(this, uri);
                player.setAudioStreamType(AudioManager.STREAM_RING);
                player.setLooping(true);
                player.prepare();
                player.start();
            } catch (Exception e) {
                releasePlayer();
            }
        }
        if (mode != AudioManager.RINGER_MODE_SILENT) {
            vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (vibrator != null && vibrator.hasVibrator()) {
                vibrator.vibrate(new long[]{0, 1000, 1000}, 0);
            }
        }
    }

    private void stopRinging() {
        releasePlayer();
        if (vibrator != null) {
            vibrator.cancel();
            vibrator = null;
        }
    }

    private void releasePlayer() {
        if (player != null) {
            try {
                player.stop();
            } catch (Exception ignored) {
            }
            player.release();
            player = null;
        }
    }

    private void releaseVoice() {
        if (voicePlayer != null) {
            try {
                voicePlayer.stop();
            } catch (Exception ignored) {
            }
            voicePlayer.release();
            voicePlayer = null;
        }
    }

    @Override
    protected void onDestroy() {
        stopRinging();
        releaseVoice();
        if (audioChanged) {
            AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            am.setSpeakerphoneOn(savedSpeaker);
            am.setMode(savedMode);
        }
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    private static String nonNull(String s) {
        return s == null ? "" : s.trim();
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private TextView label(String text, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setGravity(Gravity.CENTER);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private TextView pill(String text, int color) {
        TextView t = label(text, 18, Color.WHITE, true);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(color);
        bg.setCornerRadius(dp(40));
        t.setBackground(bg);
        t.setPadding(dp(8), dp(18), dp(8), dp(18));
        t.setClickable(true);
        return t;
    }
}
