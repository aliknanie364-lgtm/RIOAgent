package com.rioagent.app;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.os.Handler;
import android.os.Looper;

/** Keeps the movement finger down by chaining continued accessibility strokes. */
public final class ContinuousTouchController {
    private final AccessibilityService service;
    private final Handler main = new Handler(Looper.getMainLooper());
    private GestureDescription.StrokeDescription stroke;
    private float lastX, lastY, targetX, targetY;
    private float attackX, attackY, runX, runY;
    private volatile boolean active;
    private boolean dispatching, stopRequested, attackPending, runPending;
    private long lastAttackAt, lastRunAt;

    public ContinuousTouchController(AccessibilityService service) { this.service = service; }

    public void start(float x, float y) {
        main.post(() -> {
            if (active) return;
            lastX = x; lastY = y; targetX = x; targetY = y;
            stopRequested = false; active = true; stroke = null;
            pump();
        });
    }

    public void command(float x, float y, boolean attack, float ax, float ay, boolean run, float rx, float ry) {
        main.post(() -> {
            if (!active || stopRequested) return;
            targetX = x; targetY = y;
            long now = android.os.SystemClock.uptimeMillis();
            if (attack && now - lastAttackAt >= 520) { attackPending = true; attackX = ax; attackY = ay; lastAttackAt = now; }
            if (run && now - lastRunAt >= 900) { runPending = true; runX = rx; runY = ry; lastRunAt = now; }
            if (!dispatching) pump();
        });
    }

    public void moveTo(float x, float y) { command(x, y, false, 0, 0, false, 0, 0); }

    public void stop() {
        main.post(() -> {
            if (!active) return;
            stopRequested = true;
            if (!dispatching) pump();
        });
    }

    public boolean isActive() { return active; }

    private void pump() {
        if (!active || dispatching) return;
        Path path = new Path();
        path.moveTo(lastX, lastY);
        float nx = stopRequested ? lastX : targetX;
        float ny = stopRequested ? lastY : targetY;
        if (Math.abs(nx-lastX) + Math.abs(ny-lastY) < 0.01f) { nx = lastX + 0.1f; ny = lastY + 0.1f; }
        path.lineTo(nx, ny);
        boolean continuing = !stopRequested;
        GestureDescription.StrokeDescription next;
        try {
            next = stroke == null
                ? new GestureDescription.StrokeDescription(path, 0, 120, continuing)
                : stroke.continueStroke(path, 0, 120, continuing);
        } catch (IllegalStateException e) { reset(); return; }

        GestureDescription.Builder builder = new GestureDescription.Builder().addStroke(next);
        if (attackPending) addTap(builder, attackX, attackY, 75);
        if (runPending) addTap(builder, runX, runY, 100);
        attackPending = false; runPending = false;
        lastX = nx; lastY = ny;
        dispatching = true;
        boolean accepted = service.dispatchGesture(builder.build(), new AccessibilityService.GestureResultCallback() {
            @Override public void onCompleted(GestureDescription gesture) {
                dispatching = false;
                if (!active) return;
                stroke = next;
                if (continuing) pump(); else reset();
            }
            @Override public void onCancelled(GestureDescription gesture) { reset(); }
        }, main);
        if (!accepted) reset();
    }

    private static void addTap(GestureDescription.Builder builder, float x, float y, long duration) {
        Path tap = new Path(); tap.moveTo(x, y); tap.lineTo(x + 0.1f, y + 0.1f);
        builder.addStroke(new GestureDescription.StrokeDescription(tap, 0, duration));
    }

    private void reset() {
        active = false; dispatching = false; stopRequested = false;
        attackPending = false; runPending = false; stroke = null;
    }
}
