package com.rioagent.app;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends Activity {
    private TextView status, detail;
    private Button start, stop;
    private boolean running = false;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(24), dp(36), dp(24), dp(24));
        root.setBackgroundColor(Color.rgb(15, 18, 24));

        TextView title = new TextView(this);
        title.setText("RIO AGENT");
        title.setTextColor(Color.WHITE);
        title.setTextSize(30);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        root.addView(title, new LinearLayout.LayoutParams(-1, dp(72)));

        TextView sub = new TextView(this);
        sub.setText("RIO RISE • Farming assistant");
        sub.setTextColor(Color.LTGRAY);
        sub.setGravity(Gravity.CENTER);
        root.addView(sub);

        gap(root, 32);
        status = new TextView(this);
        status.setTextSize(20);
        status.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        status.setGravity(Gravity.CENTER);
        status.setBackgroundColor(Color.rgb(35, 40, 48));
        root.addView(status, new LinearLayout.LayoutParams(-1, dp(72)));

        gap(root, 12);
        detail = new TextView(this);
        detail.setTextColor(Color.LTGRAY);
        detail.setTextSize(14);
        detail.setGravity(Gravity.CENTER);
        root.addView(detail);

        gap(root, 28);
        start = new Button(this);
        start.setText("START AGENT");
        root.addView(start, new LinearLayout.LayoutParams(-1, dp(56)));
        stop = new Button(this);
        stop.setText("STOP AGENT");
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1, dp(56));
        sp.topMargin = dp(12);
        root.addView(stop, sp);

        gap(root, 28);
        TextView modules = new TextView(this);
        modules.setText("MODULE STATUS\n\nScreen capture: Not connected\nRed marker vision: Not initialized\nPath tracking: Not initialized\nAccessibility control: Not enabled");
        modules.setTextColor(Color.LTGRAY);
        modules.setGravity(Gravity.CENTER);
        root.addView(modules);

        start.setOnClickListener(v -> { running = true; render(); });
        stop.setOnClickListener(v -> { running = false; render(); });
        setContentView(root);
        render();
    }

    private void render() {
        if (running) {
            status.setText("SESSION ACTIVE");
            status.setTextColor(Color.rgb(80, 220, 120));
            detail.setText("Session active. No game input is sent yet.");
            start.setEnabled(false); stop.setEnabled(true);
        } else {
            status.setText("AGENT STOPPED");
            status.setTextColor(Color.RED);
            detail.setText("Idle. Screen capture and game control are not active.");
            start.setEnabled(true); stop.setEnabled(false);
        }
    }
    private void gap(LinearLayout parent, int h) {
        View v = new View(this);
        parent.addView(v, new LinearLayout.LayoutParams(1, dp(h)));
    }
    private int dp(int n) {
        return (int)(n * getResources().getDisplayMetrics().density + 0.5f);
    }
}
