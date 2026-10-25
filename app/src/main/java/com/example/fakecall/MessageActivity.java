package com.example.fakecall;

import android.app.Activity;
import android.app.NotificationManager;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.Date;

/** Tiny chat screen opened from the fake message notification. Replies stay on the screen. */
public class MessageActivity extends Activity {

    private LinearLayout chat;
    private ScrollView scroll;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String sender = nonNull(getIntent().getStringExtra(MainActivity.EXTRA_NAME));
        String text = nonNull(getIntent().getStringExtra(MainActivity.EXTRA_TEXT));
        long time = getIntent().getLongExtra(MainActivity.EXTRA_TIME, System.currentTimeMillis());

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFFF2F2F2);

        // header: avatar + name
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setBackgroundColor(Color.WHITE);
        header.setPadding(dp(16), dp(12), dp(16), dp(12));
        int av = dp(44);
        LinearLayout.LayoutParams avLp = new LinearLayout.LayoutParams(av, av);
        header.addView(Media.avatarView(this, sender, av), avLp);
        TextView title = new TextView(this);
        title.setText(sender.isEmpty() ? "Message" : sender);
        title.setTextSize(18);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(0xFF202124);
        title.setPadding(dp(12), 0, 0, 0);
        header.addView(title);
        root.addView(header, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // chat area
        scroll = new ScrollView(this);
        chat = new LinearLayout(this);
        chat.setOrientation(LinearLayout.VERTICAL);
        chat.setPadding(dp(12), dp(12), dp(12), dp(12));
        scroll.addView(chat, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        TextView when = new TextView(this);
        when.setText(DateFormat.getTimeFormat(this).format(new Date(time)));
        when.setTextSize(12);
        when.setTextColor(0xFF80868B);
        when.setGravity(Gravity.CENTER);
        chat.addView(when, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        addBubble(text, false);

        // input row
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackgroundColor(Color.WHITE);
        row.setPadding(dp(8), dp(4), dp(8), dp(4));
        final EditText input = new EditText(this);
        input.setHint("Type a message");
        input.setSingleLine(true);
        row.addView(input, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        Button send = new Button(this);
        send.setText("Send");
        row.addView(send);
        root.addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        send.setOnClickListener(v -> {
            String t = input.getText().toString().trim();
            if (t.isEmpty()) return;
            addBubble(t, true);
            input.setText("");
        });

        setContentView(root);

        // the notification has been opened
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        nm.cancel(Messages.NOTIF_ID);
    }

    private void addBubble(String text, boolean mine) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(16);
        tv.setTextColor(mine ? Color.WHITE : 0xFF202124);
        tv.setMaxWidth((int) (getResources().getDisplayMetrics().widthPixels * 0.75f));
        tv.setPadding(dp(14), dp(10), dp(14), dp(10));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(mine ? 0xFF1E88E5 : Color.WHITE);
        bg.setCornerRadius(dp(18));
        tv.setBackground(bg);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(8);
        lp.gravity = mine ? Gravity.END : Gravity.START;
        chat.addView(tv, lp);

        scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
    }

    private static String nonNull(String s) {
        return s == null ? "" : s.trim();
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}
