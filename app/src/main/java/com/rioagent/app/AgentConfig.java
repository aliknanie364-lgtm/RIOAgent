package com.rioagent.app;

public final class AgentConfig {
    private AgentConfig() {}

    public static final int PROCESS_WIDTH = 480;

    public static volatile float tractorX = 0.50f;
    public static volatile float tractorY = 0.65f;

    public static volatile float roiLeft = 0.03f;
    public static volatile float roiTop = 0.08f;
    public static volatile float roiRight = 0.97f;
    public static volatile float roiBottom = 0.92f;

    public static volatile int redMin = 140;
    public static volatile int greenMax = 110;
    public static volatile int blueMax = 110;

    public static volatile float steeringSensitivity = 1.0f;
    public static volatile float steeringSmoothing = 0.35f;
    public static volatile float arrivalRadius = 0.045f;

    public static float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }
}
