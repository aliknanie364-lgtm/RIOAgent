package com.rioagent.app;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.os.Handler;
import android.os.Looper;

public final class ContinuousTouchController {
    private final AccessibilityService service;
    private final Handler main = new Handler(Looper.getMainLooper());
    private GestureDescription.StrokeDescription stroke;
    private float lastX, lastY, targetX, targetY;
    private volatile boolean active;
    private boolean dispatching, stopRequested;

    public ContinuousTouchController(AccessibilityService service) {
        this.service = service;
    }

    public void start(float x, float y) {
        main.post(() -> {
            if (active) return;
            lastX = targetX = x;
            lastY = targetY = y;
            stopRequested = false;
            active = true;
            Path path = new Path();
            path.moveTo(x, y);
            path.lineTo(x + 0.1f, y + 0.1f);
            GestureDescription.StrokeDescription first =
                new GestureDescription.StrokeDescription(path, 0, 120, true);
            send(first, true);
        });
    }

    public void moveTo(float x, float y) {
        main.post(() -> {
            if (active && !stopRequested) {
                targetX = x;
                targetY = y;
            }
        });
    }

    public void stop() {
        main.post(() -> {
            if (!active) return;
            stopRequested = true;
            if (!dispatching) pump();
        });
    }

    public boolean isActive() {
        return active;
    }

    private void pump() {
        if (!active || dispatching || stroke == null) return;

        Path path = new Path();
        path.moveTo(lastX, lastY);
        float nx = stopRequested ? lastX + 0.1f : targetX;
        float ny = stopRequested ? lastY + 0.1f : targetY;
        path.lineTo(nx, ny);

        boolean continuing = !stopRequested;
        GestureDescription.StrokeDescription next;
        try {
            next = stroke.continueStroke(path, 0, 120, continuing);
        } catch (IllegalStateException e) {
            reset();
            return;
        }

        lastX = nx;
        lastY = ny;
        send(next, continuing);
    }

    private void send(GestureDescription.StrokeDescription next,
                      boolean continuing) {
        dispatching = true;
        boolean accepted = service.dispatchGesture(
            new GestureDescription.Builder().addStroke(next).build(),
            new AccessibilityService.GestureResultCallback() {
                @Override
                public void onCompleted(GestureDescription gesture) {
                    dispatching = false;
                    if (!active) return;
                    if (continuing) {
                        stroke = next;
                        pump();
                    } else {
                        reset();
                    }
                }

                @Override
                public void onCancelled(GestureDescription gesture) {
                    reset();
                }
            }, main
        );
        if (!accepted) reset();
    }

    private void reset() {
        active = false;
        dispatching = false;
        stopRequested = false;
        stroke = null;
    }
}
