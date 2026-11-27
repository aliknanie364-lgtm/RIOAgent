package com.rioagent.app;

/** RIO RISE landscape control profile, scaled from the verified 1536x681 gameplay capture. */
public final class AgentConfig {
    private AgentConfig() {}
    public static final int PROCESS_WIDTH = 480;
    public static volatile boolean controlsCalibrated = true;
    public static volatile boolean autoEnabled = false;
    // Screen-normalized controls from the user's RIO RISE screenshot (1536x681).
    public static volatile float joystickX = 0.3711f, joystickY = 0.9471f;
    public static volatile float joystickRadius = 0.052f;
    public static volatile float attackX = 0.836f, attackY = 0.674f;
    public static volatile float runX = 0.902f, runY = 0.814f;
    // Approximate player anchor in the captured gameplay view.
    public static volatile float tractorX = 0.495f, tractorY = 0.72f;
    public static volatile float roiLeft = 0.04f, roiTop = 0.06f, roiRight = 0.96f, roiBottom = 0.90f;
    public static volatile int redMin = 150, greenMax = 105, blueMax = 105;
    public static volatile float steeringSensitivity = 1.15f, steeringSmoothing = 0.22f, arrivalRadius = 0.045f;
    public static volatile long lastCommandAt = 0L;
    public static float clamp(float v,float min,float max){return Math.max(min,Math.min(max,v));}
}
