package com.rioagent.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
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

import java.nio.ByteBuffer;

public class CaptureService extends Service {
    public static final String ACTION_UPDATE = "com.rioagent.app.CAPTURE_UPDATE";
    public static final String EXTRA_RESULT_CODE = "result_code";
    public static final String EXTRA_RESULT_DATA = "result_data";
    private static final String CHANNEL_ID = "rioagent_capture";
    private static final int NOTIFICATION_ID = 71;

    private MediaProjection projection;
    private VirtualDisplay virtualDisplay;
    private ImageReader imageReader;
    private HandlerThread workerThread;
    private Handler worker;
    private int frameCount = 0;

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || !intent.hasExtra(EXTRA_RESULT_DATA)) {
            stopSelf();
            return START_NOT_STICKY;
        }
        createChannel();
        Notification notification = buildNotification("RIOAgent يراقب الشاشة بعد موافقتك");
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIFICATION_ID, notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }

        try {
            int resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0);
            Intent resultData = getIntentExtra(intent, EXTRA_RESULT_DATA);
            MediaProjectionManager manager =
                (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
            projection = manager.getMediaProjection(resultCode, resultData);
            if (projection == null) throw new IllegalStateException("تعذر بدء مشاركة الشاشة");

            workerThread = new HandlerThread("RIOAgentFrameWorker");
            workerThread.start();
            worker = new Handler(workerThread.getLooper());

            android.util.DisplayMetrics metrics = getResources().getDisplayMetrics();
            int width = metrics.widthPixels;
            int height = metrics.heightPixels;
            int density = metrics.densityDpi;

            imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2);
            imageReader.setOnImageAvailableListener(this::analyzeLatestFrame, worker);
            projection.registerCallback(new MediaProjection.Callback() {
                @Override public void onStop() {
                    stopCapture();
                }
            }, worker);

            virtualDisplay = projection.createVirtualDisplay(
                "RIOAgentCapture", width, height, density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader.getSurface(), null, worker);

            sendUpdate("الحالة: يعمل — مشاركة الشاشة نشطة",
                "التحليل: بانتظار أول إطار");
        } catch (Exception e) {
            sendUpdate("الحالة: تعذر بدء الالتقاط",
                "التفاصيل: " + (e.getMessage() == null ? "خطأ غير معروف" : e.getMessage()));
            stopCapture();
        }
        return START_NOT_STICKY;
    }

    private Intent getIntentExtra(Intent source, String key) {
        if (Build.VERSION.SDK_INT >= 33) return source.getParcelableExtra(key, Intent.class);
        return source.getParcelableExtra(key);
    }

    private void analyzeLatestFrame(ImageReader reader) {
        Image image = null;
        try {
            image = reader.acquireLatestImage();
            if (image == null) return;
            Image.Plane plane = image.getPlanes()[0];
            ByteBuffer buffer = plane.getBuffer();
            int pixelStride = plane.getPixelStride();
            int rowStride = plane.getRowStride();
            int width = image.getWidth();
            int height = image.getHeight();
            int rowPadding = rowStride - pixelStride * width;

            long brightnessSum = 0;
            long warm = 0;
            long cool = 0;
            int samples = 0;
            int stepX = Math.max(1, width / 80);
            int stepY = Math.max(1, height / 80);

            for (int y = 0; y < height; y += stepY) {
                for (int x = 0; x < width; x += stepX) {
                    int index = y * rowStride + x * pixelStride;
                    if (index + 2 >= buffer.limit()) continue;
                    int r = buffer.get(index) & 0xff;
                    int g = buffer.get(index + 1) & 0xff;
                    int b = buffer.get(index + 2) & 0xff;
                    brightnessSum += (r + g + b) / 3;
                    if (r > b + 25) warm++;
                    else if (b > r + 25) cool++;
                    samples++;
                }
            }

            frameCount++;
            if (samples > 0 && frameCount % 10 == 0) {
                int brightness = (int) (brightnessSum / samples);
                int warmPct = (int) (warm * 100 / samples);
                int coolPct = (int) (cool * 100 / samples);
                sendUpdate("الحالة: يعمل — تم تحليل " + frameCount + " إطار",
                    "تحليل بصري عام\nمتوسط السطوع: " + brightness + "/255"
                    + "\nألوان دافئة تقريباً: " + warmPct + "%"
                    + "\nألوان باردة تقريباً: " + coolPct + "%"
                    + "\nهذه مؤشرات عامة وليست كشفاً لعناصر RIO RISE.");
            }
        } catch (Exception ignored) {
            sendUpdate("الحالة: يعمل مع مشكلة في قراءة إطار",
                "قد يكون التطبيق الآخر غيّر اتجاه الشاشة أو حجمها.");
        } finally {
            if (image != null) image.close();
        }
    }

    private void sendUpdate(String status, String analysis) {
        Intent update = new Intent(ACTION_UPDATE);
        update.setPackage(getPackageName());
        update.putExtra("status", status);
        update.putExtra("analysis", analysis);
        sendBroadcast(update);
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        nm.notify(NOTIFICATION_ID, buildNotification(status));
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, "RIOAgent screen analysis", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("إشعار أثناء مشاركة الشاشة مع RIOAgent");
            ((NotificationManager) getSystemService(NOTIFICATION_SERVICE))
                .createNotificationChannel(channel);
        }
    }

    private Notification buildNotification(String text) {
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
            ? new Notification.Builder(this, CHANNEL_ID)
            : new Notification.Builder(this);
        return builder.setContentTitle("RIOAgent")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setOngoing(true)
            .build();
    }

    private void stopCapture() {
        try { if (virtualDisplay != null) virtualDisplay.release(); } catch (Exception ignored) {}
        virtualDisplay = null;
        try { if (imageReader != null) imageReader.close(); } catch (Exception ignored) {}
        imageReader = null;
        MediaProjection oldProjection = projection;
        projection = null;
        try { if (oldProjection != null) oldProjection.stop(); } catch (Exception ignored) {}
        if (workerThread != null) {
            workerThread.quitSafely();
            workerThread = null;
        }
        stopForeground(true);
        stopSelf();
    }

    @Override public void onDestroy() {
        if (projection != null || virtualDisplay != null || imageReader != null) stopCapture();
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
