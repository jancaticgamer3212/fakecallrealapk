package com.example.fakecall;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity {

    static final String PREFS = "fakecall";
    static final String K_NAME = "name";
    static final String K_NUMBER = "number";
    static final String K_WHEN = "when";
    static final String K_SCHED_NAME = "sched_name";
    static final String K_SCHED_NUMBER = "sched_number";
    static final String K_REAL = "real_mode";
    static final String EXTRA_NUMBER = "number";
    static final String EXTRA_NAME = "name";

    // Spinner positions: 0-7 are presets, then the two custom options.
    private static final long[] PRESET_SECONDS = {0, 10, 30, 60, 300, 900, 1800, 3600};
    private static final int POS_CUSTOM_DELAY = 8;
    private static final int POS_CUSTOM_CLOCK = 9;

    private EditText nameEdit, numberEdit, hoursEdit, minutesEdit, secondsEdit;
    private Spinner whenSpinner;
    private View customDelayRow;
    private Button pickTimeButton;
    private CheckBox realModeCheck;
    private TextView statusText;
    private SharedPreferences prefs;

    private int clockHour = -1;
    private int clockMinute = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);

        nameEdit = (EditText) findViewById(R.id.nameEdit);
        numberEdit = (EditText) findViewById(R.id.numberEdit);
        hoursEdit = (EditText) findViewById(R.id.hoursEdit);
        minutesEdit = (EditText) findViewById(R.id.minutesEdit);
        secondsEdit = (EditText) findViewById(R.id.secondsEdit);
        whenSpinner = (Spinner) findViewById(R.id.whenSpinner);
        customDelayRow = findViewById(R.id.customDelayRow);
        pickTimeButton = (Button) findViewById(R.id.pickTimeButton);
        statusText = (TextView) findViewById(R.id.statusText);
        realModeCheck = (CheckBox) findViewById(R.id.realModeCheck);

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

        nameEdit.setText(prefs.getString(K_NAME, ""));
        numberEdit.setText(prefs.getString(K_NUMBER, ""));

        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(
                this, R.array.when_options, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        whenSpinner.setAdapter(adapter);
        whenSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                customDelayRow.setVisibility(pos == POS_CUSTOM_DELAY ? View.VISIBLE : View.GONE);
                pickTimeButton.setVisibility(pos == POS_CUSTOM_CLOCK ? View.VISIBLE : View.GONE);
                if (pos == POS_CUSTOM_CLOCK && clockHour < 0) showTimePicker();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        pickTimeButton.setOnClickListener(v -> showTimePicker());
        findViewById(R.id.scheduleButton).setOnClickListener(v -> onSchedule());
        findViewById(R.id.cancelButton).setOnClickListener(v -> onCancel());
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStatus();
    }

    private void showTimePicker() {
        Calendar now = Calendar.getInstance();
        int h = clockHour >= 0 ? clockHour : now.get(Calendar.HOUR_OF_DAY);
        int m = clockHour >= 0 ? clockMinute : now.get(Calendar.MINUTE);
        new TimePickerDialog(this, (view, hourOfDay, minute) -> {
            clockHour = hourOfDay;
            clockMinute = minute;
            pickTimeButton.setText(String.format(Locale.getDefault(),
                    "Call at %02d:%02d (tap to change)", hourOfDay, minute));
        }, h, m, DateFormat.is24HourFormat(this)).show();
    }

    private long parseNumber(EditText e) {
        try {
            return Long.parseLong(e.getText().toString().trim());
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    /** Returns the time the fake call should ring (ms since epoch), or -1 if the input is invalid. */
    private long computeTriggerTime() {
        int pos = whenSpinner.getSelectedItemPosition();
        long now = System.currentTimeMillis();

        if (pos < PRESET_SECONDS.length) {
            return now + PRESET_SECONDS[pos] * 1000L;
        }
        if (pos == POS_CUSTOM_DELAY) {
            long secs = parseNumber(hoursEdit) * 3600 + parseNumber(minutesEdit) * 60
                    + parseNumber(secondsEdit);
            if (secs <= 0) {
                Toast.makeText(this, "Enter a delay greater than zero", Toast.LENGTH_SHORT).show();
                return -1;
            }
            return now + secs * 1000L;
        }
        if (clockHour < 0) {
            Toast.makeText(this, "Pick a time first", Toast.LENGTH_SHORT).show();
            return -1;
        }
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, clockHour);
        c.set(Calendar.MINUTE, clockMinute);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        if (c.getTimeInMillis() <= now) c.add(Calendar.DAY_OF_YEAR, 1); // already passed -> tomorrow
        return c.getTimeInMillis();
    }

    private PendingIntent alarmIntent(String name, String number) {
        Intent i = new Intent(this, CallReceiver.class);
        if (number != null) {
            i.putExtra(EXTRA_NAME, name);
            i.putExtra(EXTRA_NUMBER, number);
        }
        return PendingIntent.getBroadcast(this, 1, i, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private void onSchedule() {
        String name = nameEdit.getText().toString().trim();
        String number = numberEdit.getText().toString().trim();
        if (name.isEmpty() && number.isEmpty()) {
            nameEdit.setError("Enter a name or a number");
            return;
        }
        long when = computeTriggerTime();
        if (when < 0) return;

        prefs.edit().putString(K_NAME, name).putString(K_NUMBER, number).apply();

        if (whenSpinner.getSelectedItemPosition() == 0) {
            CallReceiver.trigger(this, name, number);
            return;
        }

        AlarmManager am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        PendingIntent pi = alarmIntent(name, number);
        if (Build.VERSION.SDK_INT >= 19) {
            am.setExact(AlarmManager.RTC_WAKEUP, when, pi);
        } else {
            am.set(AlarmManager.RTC_WAKEUP, when, pi);
        }
        prefs.edit().putLong(K_WHEN, when)
                .putString(K_SCHED_NAME, name).putString(K_SCHED_NUMBER, number).apply();
        refreshStatus();
        Toast.makeText(this, "Fake call scheduled", Toast.LENGTH_SHORT).show();
    }

    private void onCancel() {
        AlarmManager am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        PendingIntent pi = alarmIntent(null, null);
        am.cancel(pi);
        pi.cancel();
        prefs.edit().putLong(K_WHEN, 0).apply();
        refreshStatus();
    }

    private void refreshStatus() {
        long when = prefs.getLong(K_WHEN, 0);
        if (when > System.currentTimeMillis()) {
            String name = prefs.getString(K_SCHED_NAME, "");
            String number = prefs.getString(K_SCHED_NUMBER, "");
            String label = name.isEmpty() ? number
                    : (number.isEmpty() ? name : name + " (" + number + ")");
            String time = new SimpleDateFormat("EEE HH:mm:ss", Locale.getDefault())
                    .format(new Date(when));
            statusText.setText("Scheduled: fake call from " + label + " at " + time);
        } else {
            statusText.setText("No call scheduled.");
        }
    }
}
