package com.rioagent.app;

import android.app.Activity;
import android.content.Intent;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class MainActivity extends Activity {
    private static final int REQUEST_CAPTURE = 4102;
    private TextView statusText;
    private TextView controlText;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        buildUi();
    }

    @Override protected void onResume() {
        super.onResume();
        if (controlText != null) updateControlStatus();
    }

    private void buildUi() {
        int p = dp(16);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(p, p, p, p);
        page.setBackgroundColor(0xFFF3F5F9);

        TextView title = new TextView(this);
        title.setText("RIOAgent 1.2");
        title.setTextSize(28);
        title.setGravity(Gravity.CENTER);
        title.setTextColor(0xFF14213D);
        page.addView(title, match());

        statusText = card("تحليل الشاشة: متوقف");
        page.addView(statusText, match());
        controlText = card("التحكم: تحقق من صلاحية إمكانية الوصول");
        page.addView(controlText, match());

        addButton(page, "تفعيل صلاحية التحكم", v ->
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        addButton(page, "بدء تحليل الشاشة", v -> {
            MediaProjectionManager m = (MediaProjectionManager)getSystemService(MEDIA_PROJECTION_SERVICE);
            startActivityForResult(m.createScreenCaptureIntent(), REQUEST_CAPTURE);
        });
        addButton(page, "نقرة تجريبية بمنتصف الشاشة", v -> {
            boolean ok = GameControlService.tap(getResources().getDisplayMetrics().widthPixels / 2f,
                getResources().getDisplayMetrics().heightPixels / 2f, 90);
            statusText.setText(ok ? "أُرسل أمر النقرة" : "فعّل صلاحية التحكم أولاً");
        });
        addButton(page, "سحب تجريبي من اليسار إلى اليمين", v -> {
            float w = getResources().getDisplayMetrics().widthPixels;
            float h = getResources().getDisplayMetrics().heightPixels;
            boolean ok = GameControlService.swipe(w*.25f, h*.7f, w*.75f, h*.7f, 350);
            statusText.setText(ok ? "أُرسل أمر السحب" : "فعّل صلاحية التحكم أولاً");
        });
        addButton(page, "إيقاف تحليل الشاشة", v -> {
            stopService(new Intent(this, CaptureService.class));
            statusText.setText("تحليل الشاشة: متوقف");
        });

        TextView warning = new TextView(this);
        warning.setText("التحكم اليدوي يرسل نقرة أو سحباً تجريبياً عند ضغطك. التحكم التلقائي الذكي غير مفعل بعد: يحتاج تحديد أزرار اللعبة وقواعدها واختبارها؛ لا توجد حالياً استراتيجية موثوقة للعب الآلي.");
        warning.setTextColor(0xFF7A4B00);
        warning.setTextSize(14);
        warning.setPadding(0, dp(16), 0, dp(12));
        page.addView(warning, match());

        ScrollView scroll = new ScrollView(this);
        scroll.addView(page);
        setContentView(scroll);
        updateControlStatus();
    }

    private void updateControlStatus() {
        controlText.setText(GameControlService.isReady()
            ? "التحكم: مفعّل وجاهز" : "التحكم: غير مفعّل — فعّل خدمة RIOAgent من إعدادات إمكانية الوصول");
    }

    private TextView card(String value) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(16);
        t.setTextColor(0xFF172B4D);
        t.setBackgroundColor(0xFFFFFFFF);
        t.setPadding(dp(14), dp(16), dp(14), dp(16));
        return t;
    }

    private void addButton(LinearLayout page, String text, android.view.View.OnClickListener click) {
        Button b = new Button(this);
        b.setText(text);
        b.setOnClickListener(click);
        page.addView(b, match());
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == REQUEST_CAPTURE) {
            if (result == RESULT_OK && data != null) {
                Intent service = new Intent(this, CaptureService.class);
                service.putExtra(CaptureService.EXTRA_RESULT_CODE, result);
                service.putExtra(CaptureService.EXTRA_RESULT_DATA, data);
                if (Build.VERSION.SDK_INT >= 26) startForegroundService(service);
                else startService(service);
                statusText.setText("تحليل الشاشة: جارٍ البدء");
            } else statusText.setText("لم تتم الموافقة على التقاط الشاشة");
        }
    }

    private LinearLayout.LayoutParams match() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(8);
        return lp;
    }
    private int dp(int n) { return (int)(n * getResources().getDisplayMetrics().density + .5f); }
}
