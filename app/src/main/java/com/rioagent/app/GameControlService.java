package com.rioagent.app;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.os.Build;
import android.view.accessibility.AccessibilityEvent;

public class GameControlService extends AccessibilityService {
    private static volatile GameControlService instance;
    private ContinuousTouchController continuousTouch;

    @Override public void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        continuousTouch = new ContinuousTouchController(this);
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) { }

    @Override public void onInterrupt() { if (continuousTouch != null) continuousTouch.stop(); }

    @Override public void onDestroy() {
        if (continuousTouch != null) continuousTouch.stop();
        continuousTouch = null;
        if (instance == this) instance = null;
        super.onDestroy();
    }

    public static boolean isReady() { return instance != null; }
    public static boolean startContinuous(float x,float y) { GameControlService s=instance;if(s==null||s.continuousTouch==null)return false;s.continuousTouch.start(x,y);return true; }
    public static void moveContinuous(float x,float y) { GameControlService s=instance;if(s!=null&&s.continuousTouch!=null)s.continuousTouch.moveTo(x,y); }
    public static void stopContinuous() { GameControlService s=instance;if(s!=null&&s.continuousTouch!=null)s.continuousTouch.stop(); }


    /** Updates the persistent movement finger and schedules attack/run taps in the same gesture stream. */
    public static boolean executeAgentCommand(float steering,float throttle,boolean attack,boolean run) {
        GameControlService s=instance;
        if(s==null||Build.VERSION.SDK_INT<26)return false;
        android.util.DisplayMetrics dm=s.getResources().getDisplayMetrics();
        int w=dm.widthPixels,h=dm.heightPixels;
        if(w<=0||h<=0||h>w)return false;
        float cx=AgentConfig.joystickX*w,cy=AgentConfig.joystickY*h;
        float radius=AgentConfig.joystickRadius*w;
        float sx=AgentConfig.clamp(steering,-1f,1f);
        float forward=AgentConfig.clamp(throttle,0f,1f);
        float ex=AgentConfig.clamp(cx+sx*radius,1,w-2);
        float ey=AgentConfig.clamp(cy-forward*radius*0.78f,1,h-2);
        if(s.continuousTouch==null)return false;
        if(!s.continuousTouch.isActive())s.continuousTouch.start(cx,cy);
        s.continuousTouch.command(ex,ey,attack,AgentConfig.attackX*w,AgentConfig.attackY*h,
                run,AgentConfig.runX*w,AgentConfig.runY*h);
        return true;
    }

    public static boolean dispatchRecorded(GestureDescription gesture) {
        GameControlService s=instance;
        return gesture!=null && s!=null && s.dispatchGesture(gesture,null,null);
    }

    public static boolean tap(float x, float y, long durationMs) {
        GameControlService s = instance;
        if (s == null || Build.VERSION.SDK_INT < 24) return false;
        Path path = new Path();
        path.moveTo(x, y);
        GestureDescription gesture = new GestureDescription.Builder()
            .addStroke(new GestureDescription.StrokeDescription(path, 0, Math.max(40, Math.min(durationMs, 1000))))
            .build();
        return s.dispatchGesture(gesture, null, null);
    }

    public static boolean swipe(float x1, float y1, float x2, float y2, long durationMs) {
        GameControlService s = instance;
        if (s == null || Build.VERSION.SDK_INT < 24) return false;
        Path path = new Path();
        path.moveTo(x1, y1);
        path.lineTo(x2, y2);
        GestureDescription gesture = new GestureDescription.Builder()
            .addStroke(new GestureDescription.StrokeDescription(path, 0, Math.max(80, Math.min(durationMs, 3000))))
            .build();
        return s.dispatchGesture(gesture, null, null);
    }
}
