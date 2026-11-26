package com.rioagent.app;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class MainActivity extends Activity {
    private static final int REQUEST_CAPTURE = 4102;
    private TextView statusText;
    private TextView analysisText;
    private boolean receiverRegistered = false;

    private final BroadcastReceiver updates = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (CaptureService.ACTION_UPDATE.equals(intent.getAction())) {
                String status = intent.getStringExtra("status");
                String analysis = intent.getStringExtra("analysis");
                if (status != null) statusText.setText(status);
                if (analysis != null) analysisText.setText(analysis);
            }
        }
    };

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
        registerUpdates();
    }

    private void buildUi() {
        int pad = dp(18);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(pad, pad, pad, pad);
        page.setGravity(Gravity.CENTER_HORIZONTAL);
        page.setBackgroundColor(0xFFF4F6FA);

        TextView title = new TextView(this);
        title.setText("RIOAgent");
        title.setTextSize(30);
        title.setTextColor(0xFF14213D);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, dp(14), 0, dp(8));
        page.addView(title, match());

        TextView subtitle = new TextView(this);
        subtitle.setText("مساعد بصري للعبة RIO RISE");
        subtitle.setTextSize(17);
        subtitle.setTextColor(0xFF41516B);
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setPadding(0, 0, 0, dp(22));
        page.addView(subtitle, match());

        statusText = section("الحالة: متوقف");
        page.addView(statusText, match());
        analysisText = section("التحليل: بانتظار بدء مشاركة الشاشة");
        page.addView(analysisText, match());

        TextView privacy = new TextView(this);
        privacy.setText("لن يبدأ التقاط الشاشة إلا بعد موافقتك من نافذة Android. تتم معالجة الإطارات في الذاكرة ولا تُحفظ كصور.");
        privacy.setTextSize(14);
        privacy.setTextColor(0xFF526174);
        privacy.setPadding(0, dp(18), 0, dp(18));
        page.addView(privacy, match());

        Button start = new Button(this);
        start.setText("بدء تحليل الشاشة");
        start.setOnClickListener(v -> requestCapture());
        page.addView(start, match());

        Button stop = new Button(this);
        stop.setText("إيقاف التحليل");
        stop.setOnClickListener(v -> {
            stopService(new Intent(this, CaptureService.class));
            statusText.setText("الحالة: متوقف");
            analysisText.setText("التحليل: تم إيقاف مشاركة الشاشة");
        });
        page.addView(stop, match());

        TextView note = new TextView(this);
        note.setText("ملاحظة: التحليل الحالي عام (سطوع وألوان)، وليس تعرّفاً مخصصاً على عناصر اللعبة أو توصيات لعب مؤكدة.");
        note.setTextSize(13);
        note.setTextColor(0xFF7A4B00);
        note.setPadding(0, dp(22), 0, 0);
        page.addView(note, match());

        ScrollView scroll = new ScrollView(this);
        scroll.addView(page);
        setContentView(scroll);
    }

    private TextView section(String value) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(16);
        t.setTextColor(0xFF172B4D);
        t.setBackgroundColor(0xFFFFFFFF);
        t.setPadding(dp(14), dp(16), dp(14), dp(16));
        LinearLayout.LayoutParams lp = match();
        lp.bottomMargin = dp(10);
        ((LinearLayout.LayoutParams) lp).width = LinearLayout.LayoutParams.MATCH_PARENT;
        return addSectionToPage(t, lp);
    }

    private TextView addSectionToPage(TextView t, LinearLayout.LayoutParams lp) {
        // Sections are added by the caller's current content container.
        return t;
    }

    private void requestCapture() {
        MediaProjectionManager manager =
            (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        startActivityForResult(manager.createScreenCaptureIntent(), REQUEST_CAPTURE);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CAPTURE) {
            if (resultCode == RESULT_OK && data != null) {
                Intent service = new Intent(this, CaptureService.class);
                service.putExtra(CaptureService.EXTRA_RESULT_CODE, resultCode);
                service.putExtra(CaptureService.EXTRA_RESULT_DATA, data);
                if (Build.VERSION.SDK_INT >= 26) startForegroundService(service);
                else startService(service);
                statusText.setText("الحالة: جارٍ بدء التقاط الشاشة");
            } else {
                statusText.setText("الحالة: لم تمنح إذن مشاركة الشاشة");
            }
        }
    }

    private void registerUpdates() {
        IntentFilter filter = new IntentFilter(CaptureService.ACTION_UPDATE);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(updates, filter, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(updates, filter);
        receiverRegistered = true;
    }

    private LinearLayout.LayoutParams match() {
        return new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    @Override protected void onDestroy() {
        if (receiverRegistered) unregisterReceiver(updates);
        super.onDestroy();
    }
}
