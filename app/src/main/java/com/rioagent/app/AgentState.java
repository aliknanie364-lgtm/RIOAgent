package com.rioagent.app;
public final class AgentState {
    public enum Mode { STOPPED, INITIALIZING, SCANNING, TRACKING, DRIVING, RECOVERING, COMPLETED, ERROR }
    private volatile Mode mode=Mode.STOPPED; private volatile String message="الوكيل متوقف";
    private volatile int frames,markers; private volatile long lastFrameTime; private volatile float steering,throttle;
    public synchronized void setMode(Mode m,String s){mode=m==null?Mode.ERROR:m;message=s==null?"":s;}
    public void onFrame(int count){frames++;markers=Math.max(0,count);lastFrameTime=System.currentTimeMillis();}
    public void setControls(float s,float t){steering=AgentConfig.clamp(s,-1,1);throttle=AgentConfig.clamp(t,0,1);}
    public Mode getMode(){return mode;} public String getMessage(){return message;} public int getFrames(){return frames;} public int getMarkers(){return markers;} public long getLastFrameTime(){return lastFrameTime;} public float getSteering(){return steering;} public float getThrottle(){return throttle;}
    public synchronized void reset(){mode=Mode.STOPPED;message="الوكيل متوقف";frames=markers=0;lastFrameTime=0;steering=throttle=0;}
}
