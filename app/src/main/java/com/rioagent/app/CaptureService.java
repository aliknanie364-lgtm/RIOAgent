package com.rioagent.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.objects.DetectedObject;
import com.google.mlkit.vision.objects.ObjectDetection;
import com.google.mlkit.vision.objects.ObjectDetector;
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class CaptureService extends Service {
    public static final String ACTION_UPDATE = "com.rioagent.app.CAPTURE_UPDATE";
    public static final String EXTRA_RESULT_CODE = "result_code";
    public static final String EXTRA_RESULT_DATA = "result_data";
    private static final String CHANNEL = "rioagent_capture";
    private MediaProjection projection;
    private VirtualDisplay display;
    private ImageReader reader;
    private HandlerThread thread;
    private Handler handler;
    private ObjectDetector detector;
    private final AtomicBoolean busy = new AtomicBoolean(false);
    private volatile boolean stopped = false;
    private int frames = 0;
    private int analyzed = 0;

    @Override public int onStartCommand(Intent intent, int flags, int id) {
        if (projection != null) {
            update("الالتقاط يعمل بالفعل", "انتظر نتائج التحليل...");
            return START_NOT_STICKY;
        }
        if (intent == null || !intent.hasExtra(EXTRA_RESULT_DATA)) {
            update("بيانات إذن الشاشة غير موجودة", "اضغط بدء تحليل الشاشة ووافق على نافذة أندرويد.");
            stopSelf();
            return START_NOT_STICKY;
        }
        createChannel();
        Notification n = notification("بدء تحليل الشاشة");
        if (Build.VERSION.SDK_INT >= 29)
            startForeground(71, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
        else startForeground(71, n);

        try {
            int code = intent.getIntExtra(EXTRA_RESULT_CODE, 0);
            Intent data = Build.VERSION.SDK_INT >= 33
                ? intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent.class)
                : intent.getParcelableExtra(EXTRA_RESULT_DATA);
            if (data == null) throw new IllegalStateException("لم يصل رمز موافقة التقاط الشاشة");
            projection = ((MediaProjectionManager)getSystemService(Context.MEDIA_PROJECTION_SERVICE))
                .getMediaProjection(code, data);
            if (projection == null) throw new IllegalStateException("تعذر إنشاء MediaProjection");

            ObjectDetectorOptions opts = new ObjectDetectorOptions.Builder()
                .setDetectorMode(ObjectDetectorOptions.STREAM_MODE)
                .enableClassification()
                .enableMultipleObjects()
                .build();
            detector = ObjectDetection.getClient(opts);

            thread = new HandlerThread("RIOFrames");
            thread.start();
            handler = new Handler(thread.getLooper());

            android.util.DisplayMetrics m = getResources().getDisplayMetrics();
            reader = ImageReader.newInstance(m.widthPixels, m.heightPixels, PixelFormat.RGBA_8888, 3);
            reader.setOnImageAvailableListener(this::analyze, handler);

            projection.registerCallback(new MediaProjection.Callback() {
                @Override public void onStop() {
                    update("توقف إذن مشاركة الشاشة", "أعد تشغيل تحليل الشاشة ووافق على المشاركة مجدداً.");
                    stopCapture();
                }
            }, handler);

            display = projection.createVirtualDisplay(
                "RIOAgent", m.widthPixels, m.heightPixels, m.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                reader.getSurface(), null, handler);

            if (display == null) throw new IllegalStateException("لم يتم إنشاء VirtualDisplay");
            update("التقاط الشاشة نشط", "بانتظار أول إطار من اللعبة...");
        } catch (Exception e) {
            update("فشل بدء الالتقاط", readableError(e));
            stopCapture();
        }
        return START_NOT_STICKY;
    }

    private void analyze(ImageReader source) {
        Image image = null;
        try {
            image = source.acquireLatestImage();
            if (image == null || stopped) return;
            frames++;
            if (frames % 8 != 0 || !busy.compareAndSet(false, true)) return;

            Image.Plane plane = image.getPlanes()[0];
            ByteBuffer buffer = plane.getBuffer().duplicate();
            int width = image.getWidth(), height = image.getHeight();
            int pixelStride = plane.getPixelStride(), rowStride = plane.getRowStride();
            int scale = Math.max(1, (int)Math.ceil(Math.max(width, height) / 640.0));
            int outW = Math.max(1, width / scale), outH = Math.max(1, height / scale);
            int[] pixels = new int[outW * outH];

            for (int y = 0; y < outH; y++) {
                for (int x = 0; x < outW; x++) {
                    int index = Math.min(height - 1, y * scale) * rowStride
                        + Math.min(width - 1, x * scale) * pixelStride;
                    int red = buffer.get(index) & 255;
                    int green = buffer.get(index + 1) & 255;
                    int blue = buffer.get(index + 2) & 255;
                    pixels[y * outW + x] = 0xff000000 | (red << 16) | (green << 8) | blue;
                }
            }

            Bitmap bitmap = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888);
            bitmap.setPixels(pixels, 0, outW, 0, 0, outW, outH);
            detector.process(InputImage.fromBitmap(bitmap, 0))
                .addOnSuccessListener(objects -> {
                    analyzed++;
                    publish(objects);
                })
                .addOnFailureListener(error ->
                    update("تعذر تحليل الإطار", readableError(error)))
                .addOnCompleteListener(task -> {
                    if (!bitmap.isRecycled()) bitmap.recycle();
                    busy.set(false);
                });
        } catch (Exception e) {
            busy.set(false);
            update("خطأ أثناء قراءة إطار الشاشة", readableError(e));
        } finally {
            if (image != null) image.close();
        }
    }

    private void publish(List<DetectedObject> objects) {
        StringBuilder result = new StringBuilder();
        result.append("الأجسام المكتشفة: ").append(objects.size())
            .append("\nالإطارات المستلمة: ").append(frames)
            .append("\nعمليات التحليل: ").append(analyzed);
        int count = 0;
        for (DetectedObject object : objects) {
            if (count++ >= 8) break;
            result.append("\n• ");
            if (!object.getLabels().isEmpty()) {
                result.append(object.getLabels().get(0).getText())
                    .append(" (")
                    .append(Math.round(object.getLabels().get(0).getConfidence() * 100))
                    .append("%)");
            } else result.append("جسم غير مصنف");
            result.append(" — ")
                .append(object.getBoundingBox().centerX())
                .append(", ")
                .append(object.getBoundingBox().centerY());
        }
        result.append("\nملاحظة: الكشف عام وليس مدرباً على عناصر RIO RISE.");
        update("الكشف البصري يعمل", result.toString());
    }

    private String readableError(Exception e) {
        String message = e.getMessage();
        return message == null ? e.getClass().getSimpleName() : message;
    }

    private void update(String status, String analysis) {
        Intent intent = new Intent(ACTION_UPDATE).setPackage(getPackageName());
        intent.putExtra("status", status);
        intent.putExtra("analysis", analysis);
        sendBroadcast(intent);
        NotificationManager manager = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        if (manager != null) manager.notify(71, notification(status));
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager manager = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
            manager.createNotificationChannel(new NotificationChannel(
                CHANNEL, "RIOAgent screen analysis", NotificationManager.IMPORTANCE_LOW));
        }
    }

    private Notification notification(String text) {
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
            ? new Notification.Builder(this, CHANNEL) : new Notification.Builder(this);
        return builder.setContentTitle("RIOAgent")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setOngoing(true)
            .build();
    }

    private void stopCapture() {
        if (stopped) return;
        stopped = true;
        try { if (display != null) display.release(); } catch (Exception ignored) { }
        display = null;
        try { if (reader != null) reader.close(); } catch (Exception ignored) { }
        reader = null;
        try { if (projection != null) projection.stop(); } catch (Exception ignored) { }
        projection = null;
        try { if (detector != null) detector.close(); } catch (Exception ignored) { }
        detector = null;
        if (thread != null) {
            thread.quitSafely();
            thread = null;
        }
        stopForeground(true);
        stopSelf();
    }

    @Override public void onDestroy() {
        stopCapture();
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
