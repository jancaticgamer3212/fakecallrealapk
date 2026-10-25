package com.example.fakecall;

import android.app.Activity;
import android.app.TimePickerDialog;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Calendar;
import java.util.Locale;

/**
 * The "when" menu (presets, custom delay, custom clock time). Used twice on the main
 * screen: once for the call and once for the message.
 */
final class WhenPicker {

    // Spinner positions: 0-7 are presets (0 = immediately), then the two custom options.
    private static final long[] PRESET_SECONDS = {0, 10, 30, 60, 300, 900, 1800, 3600};
    private static final int POS_CUSTOM_DELAY = 8;
    private static final int POS_CUSTOM_CLOCK = 9;

    private Activity activity;
    private Spinner spinner;
    private View delayRow;
    private EditText hoursEdit, minutesEdit, secondsEdit;
    private Button pickTimeButton;

    private int clockHour = -1;
    private int clockMinute = -1;

    WhenPicker(Activity activity, ViewGroup parent, String title) {
        this.activity = activity;
        View root = LayoutInflater.from(activity).inflate(R.layout.when_picker, parent, false);
        parent.addView(root);

        ((TextView) root.findViewById(R.id.whenTitle)).setText(title);
        spinner = (Spinner) root.findViewById(R.id.whenSpinner);
        delayRow = root.findViewById(R.id.customDelayRow);
        hoursEdit = (EditText) root.findViewById(R.id.hoursEdit);
        minutesEdit = (EditText) root.findViewById(R.id.minutesEdit);
        secondsEdit = (EditText) root.findViewById(R.id.secondsEdit);
        pickTimeButton = (Button) root.findViewById(R.id.pickTimeButton);

        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(
                activity, R.array.when_options, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                delayRow.setVisibility(pos == POS_CUSTOM_DELAY ? View.VISIBLE : View.GONE);
                pickTimeButton.setVisibility(pos == POS_CUSTOM_CLOCK ? View.VISIBLE : View.GONE);
                if (pos == POS_CUSTOM_CLOCK && clockHour < 0) showTimePicker();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        pickTimeButton.setOnClickListener(v -> showTimePicker());
    }

    boolean isImmediate() {
        return spinner.getSelectedItemPosition() == 0;
    }

    private void showTimePicker() {
        Calendar now = Calendar.getInstance();
        int h = clockHour >= 0 ? clockHour : now.get(Calendar.HOUR_OF_DAY);
        int m = clockHour >= 0 ? clockMinute : now.get(Calendar.MINUTE);
        new TimePickerDialog(activity, (view, hourOfDay, minute) -> {
            clockHour = hourOfDay;
            clockMinute = minute;
            pickTimeButton.setText(String.format(Locale.getDefault(),
                    "At %02d:%02d (tap to change)", hourOfDay, minute));
        }, h, m, DateFormat.is24HourFormat(activity)).show();
    }

    private static long parse(EditText e) {
        try {
            return Long.parseLong(e.getText().toString().trim());
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    /** Returns the time to fire (ms since epoch), or -1 if the input is invalid (a toast says why). */
    long triggerTime() {
        int pos = spinner.getSelectedItemPosition();
        long now = System.currentTimeMillis();

        if (pos < PRESET_SECONDS.length) {
            return now + PRESET_SECONDS[pos] * 1000L;
        }
        if (pos == POS_CUSTOM_DELAY) {
            long secs = parse(hoursEdit) * 3600 + parse(minutesEdit) * 60 + parse(secondsEdit);
            if (secs <= 0) {
                Toast.makeText(activity, "Enter a delay greater than zero", Toast.LENGTH_SHORT).show();
                return -1;
            }
            return now + secs * 1000L;
        }
        if (clockHour < 0) {
            Toast.makeText(activity, "Pick a time first", Toast.LENGTH_SHORT).show();
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
}
