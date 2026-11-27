package com.rioagent.app;
import java.util.List;

/** Converts red enemy health-bar detections into a smoothed pursuit/attack decision. */
public final class DecisionEngine {
    public static final class Command {
        public final float steering,throttle,targetX,targetY,distance;
        public final boolean hasTarget,attack,run;
        Command(float s,float t,float x,float y,float d,boolean h,boolean a,boolean r){steering=s;throttle=t;targetX=x;targetY=y;distance=d;hasTarget=h;attack=a;run=r;}
    }
    private float previousSteering;
    private float lockedX=-1f,lockedY=-1f;
    private int lostFrames;
    public synchronized Command decide(List<RedMarkerDetector.Marker> markers){
        if(markers==null||markers.isEmpty()){
            lostFrames++;previousSteering*=0.72f;
            if(lostFrames>5){lockedX=lockedY=-1f;}
            return new Command(previousSteering,0.22f,-1,-1,1f,false,false,false);
        }
        lostFrames=0;
        RedMarkerDetector.Marker best=null;float bestScore=Float.MAX_VALUE;
        for(RedMarkerDetector.Marker m:markers){
            float dx=m.x-AgentConfig.tractorX,dy=m.y-AgentConfig.tractorY;
            float d=(float)Math.sqrt(dx*dx+dy*dy);
            // Prefer a previously tracked bar to prevent target switching between nearby enemies.
            float continuity=lockedX<0?0:distance(m.x,m.y,lockedX,lockedY)*0.32f;
            float score=d+continuity;
            if(m.y>AgentConfig.tractorY+0.08f)score+=0.9f;
            if(score<bestScore){bestScore=score;best=m;}
        }
        if(best==null)return new Command(previousSteering,0,-1,-1,1,false,false,false);
        lockedX=best.x;lockedY=best.y;
        float dx=lockedX-AgentConfig.tractorX,dy=AgentConfig.tractorY-lockedY;
        float raw=AgentConfig.clamp(dx/Math.max(0.16f,Math.abs(dy)+0.10f)*AgentConfig.steeringSensitivity,-1,1);
        float smooth=AgentConfig.clamp(AgentConfig.steeringSmoothing,0,0.9f);
        previousSteering=previousSteering*smooth+raw*(1-smooth);
        float d=(float)Math.sqrt(dx*dx+dy*dy);
        float throttle=d>0.30f?1f:(d>0.17f?0.72f:0.35f);
        boolean attack=d<0.43f || (Math.abs(dx)<0.18f && dy<0.48f);
        boolean run=d>0.34f && !attack;
        return new Command(previousSteering,throttle,lockedX,lockedY,d,true,attack,run);
    }
    public synchronized void reset(){previousSteering=0;lockedX=lockedY=-1;lostFrames=0;}
    private float distance(float a,float b,float c,float d){float x=a-c,y=b-d;return(float)Math.sqrt(x*x+y*y);}
}
