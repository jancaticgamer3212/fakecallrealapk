package com.example.fakecall;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity {

    static final String PREFS = "fakecall";

    // fake call
    static final String K_NAME = "name";
    static final String K_NUMBER = "number";
    static final String K_WHEN = "when";
    static final String K_SCHED_NAME = "sched_name";
    static final String K_SCHED_NUMBER = "sched_number";
    static final String K_REAL = "real_mode";

    // fake message
    static final String K_MSG_SENDER = "msg_sender";
    static final String K_MSG_TEXT = "msg_text";
    static final String K_MSG_WHEN = "msg_when";
    static final String K_MSG_SCHED_SENDER = "msg_sched_sender";
    static final String K_MSG_SCHED_TEXT = "msg_sched_text";

    static final String EXTRA_NUMBER = "number";
    static final String EXTRA_NAME = "name";
    static final String EXTRA_TEXT = "text";
    static final String EXTRA_TIME = "time";

    private static final int REQ_IMAGE = 1;
    private static final int REQ_AUDIO = 2;
    private static final int REQ_RING_FILE = 3;
    private static final int REQ_RING_SYSTEM = 4;

    private EditText nameEdit, numberEdit, msgSenderEdit, msgTextEdit;
    private TextView imageLabel, audioLabel, ringLabel, statusText, msgStatusText;
    private WhenPicker callWhen, msgWhen;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);

        nameEdit = (EditText) findViewById(R.id.nameEdit);
        numberEdit = (EditText) findViewById(R.id.numberEdit);
        msgSenderEdit = (EditText) findViewById(R.id.msgSenderEdit);
        msgTextEdit = (EditText) findViewById(R.id.msgTextEdit);
        imageLabel = (TextView) findViewById(R.id.imageLabel);
        audioLabel = (TextView) findViewById(R.id.audioLabel);
        ringLabel = (TextView) findViewById(R.id.ringLabel);
        statusText = (TextView) findViewById(R.id.statusText);
        msgStatusText = (TextView) findViewById(R.id.msgStatusText);
        CheckBox realModeCheck = (CheckBox) findViewById(R.id.realModeCheck);

        callWhen = new WhenPicker(this, (ViewGroup) findViewById(R.id.callWhenContainer),
                "When should the call come in?");
        msgWhen = new WhenPicker(this, (ViewGroup) findViewById(R.id.msgWhenContainer),
                "When should the message arrive?");

        nameEdit.setText(prefs.getString(K_NAME, ""));
        numberEdit.setText(prefs.getString(K_NUMBER, ""));
        msgSenderEdit.setText(prefs.getString(K_MSG_SENDER, ""));
        msgTextEdit.setText(prefs.getString(K_MSG_TEXT, ""));

        realModeCheck.setChecked(prefs.getBoolean(K_REAL, false));
        realModeCheck.setOnCheckedChangeListener((button, checked) -> {
            if (checked) {
                String err = RealCall.register(this);
                if (err != null) {
                    Toast.makeText(this, "Real call screen unavailable: " + err,
                            Toast.LENGTH_LONG).show();
                    button.setChecked(false);
                    return;
                }
            }
            prefs.edit().putBoolean(K_REAL, checked).apply();
        });

        findViewById(R.id.imageChooseButton).setOnClickListener(v -> pick("image/*", REQ_IMAGE));
        findViewById(R.id.audioChooseButton).setOnClickListener(v -> pick("audio/*", REQ_AUDIO));
        findViewById(R.id.imageClearButton).setOnClickListener(v -> {
            Media.clearImage(this);
            refreshMedia();
        });
        findViewById(R.id.audioClearButton).setOnClickListener(v -> {
            Media.clearAudio(this);
            refreshMedia();
        });
        findViewById(R.id.ringChooseButton).setOnClickListener(v -> chooseRingtone());
        findViewById(R.id.ringClearButton).setOnClickListener(v -> setRingtone("default", null, null));

        findViewById(R.id.scheduleButton).setOnClickListener(v -> onScheduleCall());
        findViewById(R.id.cancelButton).setOnClickListener(v -> onCancelCall());
        findViewById(R.id.msgScheduleButton).setOnClickListener(v -> onScheduleMessage());
        findViewById(R.id.msgCancelButton).setOnClickListener(v -> onCancelMessage());
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshMedia();
        refreshStatus();
    }

    // ------------------------------------------------------------------ photo / audio

    private void pick(String mimeType, int requestCode) {
        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
        i.setType(mimeType);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        try {
            startActivityForResult(Intent.createChooser(i, "Choose a file"), requestCode);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "No app on this phone can pick that kind of file",
                    Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null) return;

        if (requestCode == REQ_RING_SYSTEM) {
            // the system picker returns the choice as an extra; "Silent" comes back as null
            onSystemRingtonePicked((Uri) data.getParcelableExtra(
                    RingtoneManager.EXTRA_RINGTONE_PICKED_URI));
            return;
        }
        if (data.getData() == null) return;
        if (requestCode != REQ_IMAGE && requestCode != REQ_AUDIO && requestCode != REQ_RING_FILE) {
            return;
        }

        final Uri uri = data.getData();
        final int req = requestCode;
        new Thread(() -> {
            final String name = Media.displayName(this, uri);
            final String err = req == REQ_IMAGE ? Media.saveImage(this, uri)
                    : req == REQ_AUDIO ? Media.saveAudio(this, uri)
                    : Media.saveRingtone(this, uri);
            runOnUiThread(() -> {
                if (err != null) {
                    Toast.makeText(this, "Couldn't use that file: " + err, Toast.LENGTH_LONG).show();
                } else if (req == REQ_IMAGE) {
                    prefs.edit().putString(Media.K_IMG_NAME, name).apply();
                } else if (req == REQ_AUDIO) {
                    prefs.edit().putString(Media.K_AUDIO_NAME, name).apply();
                } else {
                    setRingtone("file", null, name);
                }
                refreshMedia();
            });
        }).start();
    }

    private void chooseRingtone() {
        new AlertDialog.Builder(this)
                .setTitle("Ringtone")
                .setItems(new CharSequence[]{"Phone ringtones", "Audio file from storage"},
                        (dialog, which) -> {
                            if (which == 0) pickSystemRingtone();
                            else pick("audio/*", REQ_RING_FILE);
                        })
                .show();
    }

    private void pickSystemRingtone() {
        Intent i = new Intent(RingtoneManager.ACTION_RINGTONE_PICKER);
        i.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_RINGTONE);
        i.putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Fake call ringtone");
        i.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true);
        i.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true);
        String current = prefs.getString(Media.K_RING_URI, null);
        if ("system".equals(prefs.getString(Media.K_RING_KIND, "default")) && current != null) {
            i.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Uri.parse(current));
        }
        try {
            startActivityForResult(i, REQ_RING_SYSTEM);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "This phone has no ringtone picker - use an audio file instead",
                    Toast.LENGTH_LONG).show();
        }
    }

    private void onSystemRingtonePicked(Uri picked) {
        if (picked == null) {
            setRingtone("silent", null, "Silent");
            return;
        }
        if (picked.equals(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE))) {
            setRingtone("default", null, null);
            return;
        }
        String title = null;
        try {
            Ringtone r = RingtoneManager.getRingtone(this, picked);
            if (r != null) title = r.getTitle(this);
        } catch (Exception ignored) {
        }
        setRingtone("system", picked.toString(), title == null ? "phone ringtone" : title);
    }

    /** kind: "default", "silent", "system" (uri given) or "file" (the copied ringtone_audio file). */
    private void setRingtone(String kind, String uri, String name) {
        if (!"file".equals(kind)) Media.ringtoneFile(this).delete();
        prefs.edit().putString(Media.K_RING_KIND, kind)
                .putString(Media.K_RING_URI, uri)
                .putString(Media.K_RING_NAME, name).apply();
        refreshMedia();
    }

    private void refreshMedia() {
        imageLabel.setText(Media.imageFile(this).exists()
                ? "Photo: " + prefs.getString(Media.K_IMG_NAME, "set") : "Photo: none");
        audioLabel.setText(Media.audioFile(this).exists()
                ? "Answer audio: " + prefs.getString(Media.K_AUDIO_NAME, "set") : "Answer audio: none");

        String kind = prefs.getString(Media.K_RING_KIND, "default");
        String ring;
        if ("silent".equals(kind)) {
            ring = "silent";
        } else if ("file".equals(kind) && Media.ringtoneFile(this).exists()) {
            ring = prefs.getString(Media.K_RING_NAME, "audio file");
        } else if ("system".equals(kind)) {
            ring = prefs.getString(Media.K_RING_NAME, "phone ringtone");
        } else {
            ring = "phone default";
        }
        ringLabel.setText("Ringtone: " + ring);
    }

    // ------------------------------------------------------------------ alarms

    private PendingIntent callIntent(String name, String number) {
        Intent i = new Intent(this, CallReceiver.class);
        if (number != null) {
            i.putExtra(EXTRA_NAME, name);
            i.putExtra(EXTRA_NUMBER, number);
        }
        return PendingIntent.getBroadcast(this, 1, i, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private PendingIntent messageIntent(String sender, String text) {
        Intent i = new Intent(this, MessageReceiver.class);
        if (text != null) {
            i.putExtra(EXTRA_NAME, sender);
            i.putExtra(EXTRA_TEXT, text);
        }
        return PendingIntent.getBroadcast(this, 2, i, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private void setAlarm(long when, PendingIntent pi) {
        AlarmManager am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        if (Build.VERSION.SDK_INT >= 19) {
            am.setExact(AlarmManager.RTC_WAKEUP, when, pi);
        } else {
            am.set(AlarmManager.RTC_WAKEUP, when, pi);
        }
    }

    private void cancelAlarm(PendingIntent pi) {
        AlarmManager am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        am.cancel(pi);
        pi.cancel();
    }

    // ------------------------------------------------------------------ fake call

    private void onScheduleCall() {
        String name = nameEdit.getText().toString().trim();
        String number = numberEdit.getText().toString().trim();
        if (name.isEmpty() && number.isEmpty()) {
            nameEdit.setError("Enter a name or a number");
            return;
        }
        long when = callWhen.triggerTime();
        if (when < 0) return;

        prefs.edit().putString(K_NAME, name).putString(K_NUMBER, number).apply();

        if (callWhen.isImmediate()) {
            CallReceiver.trigger(this, name, number);
            return;
        }

        setAlarm(when, callIntent(name, number));
        prefs.edit().putLong(K_WHEN, when)
                .putString(K_SCHED_NAME, name).putString(K_SCHED_NUMBER, number).apply();
        refreshStatus();
        Toast.makeText(this, "Fake call scheduled", Toast.LENGTH_SHORT).show();
    }

    private void onCancelCall() {
        cancelAlarm(callIntent(null, null));
        prefs.edit().putLong(K_WHEN, 0).apply();
        refreshStatus();
    }

    // ------------------------------------------------------------------ fake message

    private void onScheduleMessage() {
        String typedSender = msgSenderEdit.getText().toString().trim();
        String text = msgTextEdit.getText().toString().trim();

        String sender = typedSender;
        if (sender.isEmpty()) sender = nameEdit.getText().toString().trim();
        if (sender.isEmpty()) sender = numberEdit.getText().toString().trim();
        if (sender.isEmpty()) {
            msgSenderEdit.setError("Enter a sender name");
            return;
        }
        if (text.isEmpty()) {
            msgTextEdit.setError("Enter the message text");
            return;
        }
        long when = msgWhen.triggerTime();
        if (when < 0) return;

        prefs.edit().putString(K_MSG_SENDER, typedSender).putString(K_MSG_TEXT, text).apply();

        if (msgWhen.isImmediate()) {
            Messages.show(this, sender, text);
            return;
        }

        setAlarm(when, messageIntent(sender, text));
        prefs.edit().putLong(K_MSG_WHEN, when)
                .putString(K_MSG_SCHED_SENDER, sender).putString(K_MSG_SCHED_TEXT, text).apply();
        refreshStatus();
        Toast.makeText(this, "Fake message scheduled", Toast.LENGTH_SHORT).show();
    }

    private void onCancelMessage() {
        cancelAlarm(messageIntent(null, null));
        prefs.edit().putLong(K_MSG_WHEN, 0).apply();
        refreshStatus();
    }

    // ------------------------------------------------------------------ status

    private static String clock(long when) {
        return new SimpleDateFormat("EEE HH:mm:ss", Locale.getDefault()).format(new Date(when));
    }

    private void refreshStatus() {
        long now = System.currentTimeMillis();

        long callWhenMs = prefs.getLong(K_WHEN, 0);
        if (callWhenMs > now) {
            String name = prefs.getString(K_SCHED_NAME, "");
            String number = prefs.getString(K_SCHED_NUMBER, "");
            String label = name.isEmpty() ? number
                    : (number.isEmpty() ? name : name + " (" + number + ")");
            statusText.setText("Scheduled: fake call from " + label + " at " + clock(callWhenMs));
        } else {
            statusText.setText("No call scheduled.");
        }

        long msgWhenMs = prefs.getLong(K_MSG_WHEN, 0);
        if (msgWhenMs > now) {
            msgStatusText.setText("Scheduled: message from "
                    + prefs.getString(K_MSG_SCHED_SENDER, "") + " at " + clock(msgWhenMs));
        } else {
            msgStatusText.setText("No message scheduled.");
        }
    }
}
