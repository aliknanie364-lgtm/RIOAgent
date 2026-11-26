package com.rioagent.app;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.os.Build;
import android.view.accessibility.AccessibilityEvent;

public class GameControlService extends AccessibilityService {
    private static volatile GameControlService instance;

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {}

    @Override
    public void onInterrupt() {
        if (instance == this) instance = null;
    }

    @Override
    public void onDestroy() {
        if (instance == this) instance = null;
        super.onDestroy();
    }

    public static boolean isReady() {
        return instance != null;
    }

    public static boolean dispatchRecorded(GestureDescription gesture) {
        GameControlService s = instance;
        return gesture != null && s != null
                && s.dispatchGesture(gesture, null, null);
    }

    public static boolean tap(float x, float y, long durationMs) {
        GameControlService s = instance;
        if (s == null || Build.VERSION.SDK_INT < 24) return false;
        Path path = new Path();
        path.moveTo(x, y);
        GestureDescription gesture = new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(
                        path, 0, Math.max(40, Math.min(durationMs, 1000))))
                .build();
        return s.dispatchGesture(gesture, null, null);
    }

    public static boolean swipe(float x1, float y1, float x2, float y2,
                                long durationMs) {
        GameControlService s = instance;
        if (s == null || Build.VERSION.SDK_INT < 24) return false;
        Path path = new Path();
        path.moveTo(x1, y1);
        path.lineTo(x2, y2);
        GestureDescription gesture = new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(
                        path, 0, Math.max(80, Math.min(durationMs, 3000))))
                .build();
        return s.dispatchGesture(gesture, null, null);
    }
}
