package com.rioagent.app;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.accessibility.AccessibilityEvent;

import java.util.ArrayDeque;
import java.util.Queue;

public class GameControlService extends AccessibilityService {
    private static volatile GameControlService instance;
    private static final Handler main = new Handler(Looper.getMainLooper());
    private static final Queue<GestureDescription> queue = new ArrayDeque<>();
    private static boolean dispatching = false;
    private static final int MAX_QUEUE = 12;

    @Override public void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) { }

    @Override public void onInterrupt() {
        clearPendingGestures();
    }

    @Override public void onDestroy() {
        if (instance == this) instance = null;
        clearPendingGestures();
        super.onDestroy();
    }

    public static boolean isReady() { return instance != null; }

    public static boolean tap(float x, float y, long durationMs) {
        if (!validPoint(x, y)) return false;
        Path path = new Path();
        path.moveTo(x, y);
        GestureDescription gesture = new GestureDescription.Builder()
            .addStroke(new GestureDescription.StrokeDescription(
                path, 0, Math.max(40, Math.min(durationMs, 1000))))
            .build();
        return enqueue(gesture);
    }

    public static boolean swipe(float x1, float y1, float x2, float y2, long durationMs) {
        if (!validPoint(x1, y1) || !validPoint(x2, y2)) return false;
        Path path = new Path();
        path.moveTo(x1, y1);
        path.lineTo(x2, y2);
        GestureDescription gesture = new GestureDescription.Builder()
            .addStroke(new GestureDescription.StrokeDescription(
                path, 0, Math.max(80, Math.min(durationMs, 3000))))
            .build();
        return enqueue(gesture);
    }

    private static boolean validPoint(float x, float y) {
        if (instance == null || Build.VERSION.SDK_INT < 24) return false;
        android.util.DisplayMetrics m = instance.getResources().getDisplayMetrics();
        return Float.isFinite(x) && Float.isFinite(y)
            && x >= 0 && y >= 0 && x < m.widthPixels && y < m.heightPixels;
    }

    private static synchronized boolean enqueue(GestureDescription gesture) {
        if (instance == null || queue.size() >= MAX_QUEUE) return false;
        queue.add(gesture);
        main.post(GameControlService::dispatchNext);
        return true;
    }

    private static synchronized void dispatchNext() {
        GameControlService service = instance;
        if (service == null || dispatching || queue.isEmpty()) return;
        GestureDescription next = queue.poll();
        dispatching = true;
        boolean accepted = service.dispatchGesture(next, new GestureResultCallback() {
            @Override public void onCompleted(GestureDescription gesture) {
                synchronized (GameControlService.class) { dispatching = false; }
                main.post(GameControlService::dispatchNext);
            }
            @Override public void onCancelled(GestureDescription gesture) {
                synchronized (GameControlService.class) { dispatching = false; }
                main.post(GameControlService::dispatchNext);
            }
        }, main);
        if (!accepted) {
            dispatching = false;
            main.postDelayed(GameControlService::dispatchNext, 120);
        }
    }

    public static synchronized void clearPendingGestures() {
        queue.clear();
    }
}
