package com.rioagent.app;

public final class AgentState {
    public enum Mode {
        STOPPED, INITIALIZING, SCANNING, TRACKING,
        DRIVING, RECOVERING, COMPLETED, ERROR
    }

    private volatile Mode mode = Mode.STOPPED;
    private volatile String message = "الوكيل متوقف";
    private volatile int frames;
    private volatile int markers;
    private volatile long lastFrameTime;
    private volatile float steering;
    private volatile float throttle;

    public synchronized void setMode(Mode value, String text) {
        mode = value == null ? Mode.ERROR : value;
        message = text == null ? "" : text;
    }

    public void onFrame(int count) {
        frames++;
        markers = Math.max(0, count);
        lastFrameTime = System.currentTimeMillis();
    }

    public void setControls(float direction, float speed) {
        steering = AgentConfig.clamp(direction, -1f, 1f);
        throttle = AgentConfig.clamp(speed, 0f, 1f);
    }

    public Mode getMode() { return mode; }
    public String getMessage() { return message; }
    public int getFrames() { return frames; }
    public int getMarkers() { return markers; }
    public long getLastFrameTime() { return lastFrameTime; }
    public float getSteering() { return steering; }
    public float getThrottle() { return throttle; }

    public synchronized void reset() {
        mode = Mode.STOPPED;
        message = "الوكيل متوقف";
        frames = markers = 0;
        lastFrameTime = 0;
        steering = throttle = 0f;
    }
}
