package com.rioagent.app;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.ComponentName;
import android.content.Context;
import android.view.accessibility.AccessibilityManager;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.os.Build;
import android.view.accessibility.AccessibilityEvent;

public class GameControlService extends AccessibilityService {
    private static volatile GameControlService instance;
    private ContinuousTouchController continuousTouch;

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        continuousTouch = new ContinuousTouchController(this);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {}

    @Override
    public void onInterrupt() {
        stopContinuous();
    }

    @Override
    public void onDestroy() {
        stopContinuous();
        continuousTouch = null;
        if (instance == this) instance = null;
        super.onDestroy();
    }

    public static boolean isReady() {
        return instance != null;
    }

    public static boolean startContinuous(float x, float y) {
        GameControlService s = instance;
        if (s == null || s.continuousTouch == null) return false;
        s.continuousTouch.start(x, y);
        return true;
    }

    public static boolean moveContinuous(float x, float y) {
        GameControlService s = instance;
        if (s == null || s.continuousTouch == null
                || !s.continuousTouch.isActive()) return false;
        s.continuousTouch.moveTo(x, y);
        return true;
    }

    public static void stopContinuous() {
        GameControlService s = instance;
        if (s != null && s.continuousTouch != null)
            s.continuousTouch.stop();
    }

    public static boolean isAccessibilityEnabled(Context context) {
        if (context == null) return false;
        AccessibilityManager manager = (AccessibilityManager)
                context.getSystemService(Context.ACCESSIBILITY_SERVICE);
        if (manager == null) return false;

        ComponentName expected = new ComponentName(context, GameControlService.class);
        for (AccessibilityServiceInfo info :
                manager.getEnabledAccessibilityServiceList(
                        AccessibilityServiceInfo.FEEDBACK_ALL_MASK)) {
            if (info.getResolveInfo() == null ||
                    info.getResolveInfo().serviceInfo == null) continue;
            ComponentName actual = new ComponentName(
                    info.getResolveInfo().serviceInfo.packageName,
                    info.getResolveInfo().serviceInfo.name);
            if (expected.equals(actual)) return true;
        }
        return false;
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
