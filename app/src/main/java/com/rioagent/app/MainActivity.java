package com.rioagent.app;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class MainActivity extends Activity {
    private static final int REQUEST_CAPTURE = 4102;
    private TextView statusText, controlText, autoText;
    private EditText moveX, moveY, moveToX, moveToY, punchX, punchY, jumpX, jumpY;
    private final Handler automationHandler = new Handler(Looper.getMainLooper());
    private boolean receiverRegistered = false;
    private boolean autoRunning = false;
    private int autoCycles = 0;

    private final BroadcastReceiver captureReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (intent == null || !CaptureService.ACTION_UPDATE.equals(intent.getAction())) return;
            String status = intent.getStringExtra("status");
            String analysis = intent.getStringExtra("analysis");
            if (statusText != null) {
                StringBuilder text = new StringBuilder("تحليل الشاشة: ");
                if (status != null) text.append(status);
                if (analysis != null && !analysis.isEmpty()) text.append("\n\n").append(analysis);
                statusText.setText(text.toString());
            }
        }
    };

    private final Runnable autoStep = new Runnable() {
        @Override public void run() {
            if (!autoRunning) return;
            if (!GameControlService.isReady()) {
                stopAutomation("توقفت الأتمتة: خدمة التحكم غير متاحة");
                return;
            }
            try {
                float[] move = point(moveX, moveY);
                float[] moveEnd = point(moveToX, moveToY);
                float[] punch = point(punchX, punchY);
                float[] jump = point(jumpX, jumpY);

                // Short, bounded action cycle. It is a user-configured macro, not AI.
                GameControlService.swipe(move[0], move[1], moveEnd[0], moveEnd[1], 650);
                automationHandler.postDelayed(() -> {
                    if (!autoRunning) return;
                    GameControlService.tap(punch[0], punch[1], 65);
                    automationHandler.postDelayed(() -> {
                        if (!autoRunning) return;
                        GameControlService.tap(jump[0], jump[1], 65);
                        autoCycles++;
                        if (autoText != null) autoText.setText("الأتمتة التجريبية تعمل — الدورات: " + autoCycles
                            + "\nاضغط إيقاف فوراً إذا تحركت الشخصية بشكل غير مناسب.");
                        automationHandler.postDelayed(this, 450);
                    }, 180);
                }, 180);
            } catch (Exception e) {
                stopAutomation("توقفت الأتمتة بسبب إعداد غير صالح: " + e.getMessage());
            }
        }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        buildUi();
    }

    @Override protected void onStart() {
        super.onStart();
        if (!receiverRegistered) {
            IntentFilter filter = new IntentFilter(CaptureService.ACTION_UPDATE);
            if (Build.VERSION.SDK_INT >= 33)
                registerReceiver(captureReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
            else registerReceiver(captureReceiver, filter);
            receiverRegistered = true;
        }
    }

    @Override protected void onStop() {
        stopAutomation("توقفت الأتمتة عند مغادرة واجهة RIOAgent");
        if (receiverRegistered) {
            unregisterReceiver(captureReceiver);
            receiverRegistered = false;
        }
        super.onStop();
    }

    @Override protected void onResume() {
        super.onResume();
        if (controlText != null) updateControlStatus();
    }

    @Override public void onBackPressed() {
        stopAutomation("تم إيقاف الأتمتة");
        super.onBackPressed();
    }

    private void buildUi() {
        int padding = dp(14);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(padding, padding, padding, padding);
        page.setBackgroundColor(0xFFF3F5F9);

        TextView title = new TextView(this);
        title.setText("RIOAgent 1.3");
        title.setTextSize(26);
        title.setGravity(Gravity.CENTER);
        title.setTextColor(0xFF14213D);
        page.addView(title, match());

        statusText = card("تحليل الشاشة: متوقف");
        page.addView(statusText, match());
        controlText = card("التحكم: تحقق من صلاحية إمكانية الوصول");
        page.addView(controlText, match());

        addButton(page, "تفعيل صلاحية التحكم", v ->
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        TextView recorderTitle = new TextView(this);
        recorderTitle.setText("مسجّل الحركات");
        recorderTitle.setTextSize(20);
        recorderTitle.setTextColor(0xFF14213D);
        recorderTitle.setPadding(0, dp(14), 0, dp(4));
        page.addView(recorderTitle, match());
        TextView recorderStatus = card("التسجيل متوقف — يتطلب Android 14 أو أحدث ودعم إرسال أحداث اللمس من خدمة إمكانية الوصول");
        page.addView(recorderStatus, match());
        addButton(page, "بدء تسجيل حركاتي", v -> {
            if (Build.VERSION.SDK_INT < 34) { recorderStatus.setText("التسجيل غير مدعوم على هذا الإصدار من أندرويد؛ يلزم Android 14 أو أحدث."); return; }
            if (!GameControlService.isReady()) { recorderStatus.setText("فعّل خدمة إمكانية الوصول أولاً."); return; }
            android.util.DisplayMetrics dm = new android.util.DisplayMetrics();
            android.view.WindowManager wm = (android.view.WindowManager)getSystemService(WINDOW_SERVICE);
            if (wm != null) wm.getDefaultDisplay().getRealMetrics(dm);
            TouchRecorder.start(dm.widthPixels, dm.heightPixels);
            recorderStatus.setText("جاري التسجيل. ارجع إلى اللعبة ونفّذ حركاتك، ثم افتح RIOAgent واضغط إيقاف وحفظ.");
        });
        addButton(page, "إيقاف التسجيل وحفظه", v -> {
            String saved = TouchRecorder.stopAndSave(this);
            recorderStatus.setText(saved.isEmpty() ? "لم تُحفظ حركات؛ تأكد من وصول أحداث اللمس أثناء التسجيل." : "تم حفظ التسجيل: " + saved);
        });
        addButton(page, "تشغيل آخر تسجيل محفوظ", v -> {
            if (!GameControlService.isReady()) { recorderStatus.setText("فعّل خدمة إمكانية الوصول أولاً."); return; }
            android.util.DisplayMetrics dm = new android.util.DisplayMetrics();
            android.view.WindowManager wm = (android.view.WindowManager)getSystemService(WINDOW_SERVICE);
            if (wm != null) wm.getDefaultDisplay().getRealMetrics(dm);
            boolean ok = TouchRecorder.playLatest(this, dm.widthPixels, dm.heightPixels);
            recorderStatus.setText(ok ? "تم إرسال آخر تسجيل لإعادة التشغيل." : "تعذر تشغيل التسجيل؛ تأكد من وجود تسجيل محفوظ وخدمة التحكم.");
        });
        addButton(page, "عرض اسم آخر تسجيل", v -> recorderStatus.setText(TouchRecorder.latestName(this)));
        addButton(page, "بدء تحليل الشاشة", v -> {
            MediaProjectionManager manager =
                (MediaProjectionManager)getSystemService(MEDIA_PROJECTION_SERVICE);
            startActivityForResult(manager.createScreenCaptureIntent(), REQUEST_CAPTURE);
        });
        addButton(page, "نقرة تجريبية بمنتصف الشاشة", v -> {
            boolean sent = GameControlService.tap(
                getResources().getDisplayMetrics().widthPixels / 2f,
                getResources().getDisplayMetrics().heightPixels / 2f, 90);
            statusText.setText(sent ? "تم إرسال النقرة التجريبية" : "تعذر إرسال النقرة: تحقق من صلاحية التحكم");
        });
        addButton(page, "سحب تجريبي من اليسار إلى اليمين", v -> {
            float width = getResources().getDisplayMetrics().widthPixels;
            float height = getResources().getDisplayMetrics().heightPixels;
            boolean sent = GameControlService.swipe(width * .25f, height * .7f,
                width * .75f, height * .7f, 350);
            statusText.setText(sent ? "تم إرسال السحب التجريبي" : "تعذر إرسال السحب: تحقق من صلاحية التحكم");
        });

        TextView autoTitle = new TextView(this);
        autoTitle.setText("التحكم التلقائي التجريبي — اضبط إحداثيات أزرار لعبتك");
        autoTitle.setTextSize(18);
        autoTitle.setTextColor(0xFF14213D);
        autoTitle.setPadding(0, dp(12), 0, dp(6));
        page.addView(autoTitle, match());

        page.addView(card("الإحداثيات كنسبة مئوية من الشاشة: 0 إلى 100. القيم الافتراضية تقريبية للعرض الأفقي، ويجب معايرتها للعبتك."), match());
        moveX = addNumber(page, "مركز عصا الحركة X", "10");
        moveY = addNumber(page, "مركز عصا الحركة Y", "82");
        moveToX = addNumber(page, "اتجاه الحركة X", "18");
        moveToY = addNumber(page, "اتجاه الحركة Y", "82");
        punchX = addNumber(page, "زر الضرب X", "84");
        punchY = addNumber(page, "زر الضرب Y", "65");
        jumpX = addNumber(page, "زر القفز X", "91");
        jumpY = addNumber(page, "زر القفز Y", "78");

        autoText = card("الأتمتة متوقفة");
        page.addView(autoText, match());
        addButton(page, "تشغيل التسلسل التلقائي", v -> startAutomation());
        Button stop = new Button(this);
        stop.setText("إيقاف فوري للطوارئ");
        stop.setTextColor(0xFFB00020);
        stop.setOnClickListener(v -> stopAutomation("تم إيقاف الأتمتة يدوياً"));
        page.addView(stop, match());

        TextView warning = new TextView(this);
        warning.setText("مهم: هذا الإصدار يضيف تسلسلاً آلياً قابلاً للضبط (حركة ثم ضرب ثم قفز)، وليس ذكاءً اصطناعياً يفهم أهداف اللعبة أو يقرر الفوز. الكشف البصري ML Kit عام ولا يتعرف تلقائياً على عناصر RIO RISE. لا تستخدم الأتمتة قبل معايرة الإحداثيات، وابقَ مستعداً لإيقافها.");
        warning.setTextColor(0xFF7A4B00);
        warning.setTextSize(14);
        warning.setPadding(0, dp(12), 0, dp(10));
        page.addView(warning, match());

        addButton(page, "إيقاف تحليل الشاشة", v -> {
            stopService(new Intent(this, CaptureService.class));
            statusText.setText("تحليل الشاشة: تم طلب الإيقاف");
        });

        ScrollView scroll = new ScrollView(this);
        scroll.addView(page);
        setContentView(scroll);
        updateControlStatus();
    }

    private EditText addNumber(LinearLayout page, String label, String value) {
        TextView title = new TextView(this);
        title.setText(label);
        title.setTextColor(0xFF172B4D);
        title.setTextSize(14);
        page.addView(title, match());
        EditText field = new EditText(this);
        field.setSingleLine(true);
        field.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        field.setText(value);
        field.setTextSize(16);
        field.setPadding(dp(10), 0, dp(10), 0);
        page.addView(field, match());
        return field;
    }

    private float[] point(EditText xField, EditText yField) {
        float x = Float.parseFloat(xField.getText().toString().trim());
        float y = Float.parseFloat(yField.getText().toString().trim());
        if (x < 0 || x > 100 || y < 0 || y > 100)
            throw new IllegalArgumentException("الإحداثيات يجب أن تكون بين 0 و100");
        float width = getResources().getDisplayMetrics().widthPixels;
        float height = getResources().getDisplayMetrics().heightPixels;
        return new float[] { width * x / 100f, height * y / 100f };
    }

    private void startAutomation() {
        if (!GameControlService.isReady()) {
            autoText.setText("لم تبدأ: فعّل خدمة إمكانية الوصول أولاً");
            return;
        }
        if (autoRunning) return;
        try {
            point(moveX, moveY); point(moveToX, moveToY);
            point(punchX, punchY); point(jumpX, jumpY);
            autoRunning = true;
            autoCycles = 0;
            autoText.setText("بدأ التسلسل التجريبي — اضغط إيقاف للطوارئ");
            automationHandler.post(autoStep);
        } catch (Exception e) {
            autoText.setText("تحقق من الإحداثيات: " + e.getMessage());
        }
    }

    private void stopAutomation(String reason) {
        autoRunning = false;
        automationHandler.removeCallbacksAndMessages(null);
        if (autoText != null) autoText.setText(reason);
    }

    private void updateControlStatus() {
        controlText.setText(GameControlService.isReady()
            ? "التحكم: مفعّل وجاهز"
            : "التحكم: غير مفعّل — فعّل خدمة RIOAgent من إعدادات إمكانية الوصول");
    }

    private TextView card(String value) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextSize(15);
        text.setTextColor(0xFF172B4D);
        text.setBackgroundColor(0xFFFFFFFF);
        text.setPadding(dp(12), dp(13), dp(12), dp(13));
        return text;
    }

    private void addButton(LinearLayout page, String label, android.view.View.OnClickListener click) {
        Button button = new Button(this);
        button.setText(label);
        button.setOnClickListener(click);
        page.addView(button, match());
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != REQUEST_CAPTURE) return;
        if (result == RESULT_OK && data != null) {
            Intent service = new Intent(this, CaptureService.class);
            service.putExtra(CaptureService.EXTRA_RESULT_CODE, result);
            service.putExtra(CaptureService.EXTRA_RESULT_DATA, data);
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(service);
            else startService(service);
            statusText.setText("تحليل الشاشة: تم منح الإذن، بانتظار أول إطار...");
        } else {
            statusText.setText("لم تتم الموافقة على التقاط الشاشة");
        }
    }

    private LinearLayout.LayoutParams match() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = dp(7);
        return params;
    }

    private int dp(int value) {
        return (int)(value * getResources().getDisplayMetrics().density + .5f);
    }
}
