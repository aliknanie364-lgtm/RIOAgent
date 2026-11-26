package com.rioagent.app;

import android.content.Context;
import android.graphics.Path;
import android.view.MotionEvent;
import android.accessibilityservice.GestureDescription;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

/** Records touch paths received by AccessibilityService on API 34+. */
public final class TouchRecorder {
    private static final Object LOCK = new Object();
    private static boolean recording = false;
    private static long startedAt;
    private static final Map<Integer, Stroke> active = new LinkedHashMap<>();
    private static final ArrayList<Stroke> completed = new ArrayList<>();
    private static int displayW, displayH;
    private static String lastSaved = "";

    private static final class Point { float x,y; long t; Point(float x,float y,long t){this.x=x;this.y=y;this.t=t;} }
    private static final class Stroke {
        int pointerId; long start; long duration; final ArrayList<Point> points = new ArrayList<>();
        Stroke(int id,long start){pointerId=id;this.start=start;}
    }
    private TouchRecorder() {}

    public static void start(int width, int height) {
        synchronized (LOCK) { active.clear(); completed.clear(); displayW=Math.max(1,width); displayH=Math.max(1,height); startedAt=android.os.SystemClock.uptimeMillis(); recording=true; lastSaved=""; }
    }
    public static String stopAndSave(Context context) {
        synchronized (LOCK) {
            if (!recording) return lastSaved;
            long now=android.os.SystemClock.uptimeMillis();
            for (Stroke s: active.values()) { s.duration=Math.max(1,now-startedAt-s.start); completed.add(s); }
            active.clear(); recording=false;
            if (completed.isEmpty()) return "";
            try {
                JSONArray strokes=new JSONArray();
                for (Stroke s:completed) {
                    JSONObject o=new JSONObject(); o.put("start",s.start); o.put("duration",Math.max(1,s.duration));
                    JSONArray pts=new JSONArray(); for(Point p:s.points){ JSONObject q=new JSONObject(); q.put("x",p.x);q.put("y",p.y);q.put("t",p.t);pts.put(q); }
                    o.put("points",pts); strokes.put(o);
                }
                JSONObject root=new JSONObject(); root.put("width",displayW);root.put("height",displayH);root.put("strokes",strokes);root.put("created",System.currentTimeMillis());
                File dir=new File(context.getFilesDir(),"recordings"); if(!dir.exists()&&!dir.mkdirs())return "";
                String name="RIO-"+System.currentTimeMillis()+".json"; File f=new File(dir,name);
                try(FileOutputStream out=new FileOutputStream(f)){out.write(root.toString().getBytes(StandardCharsets.UTF_8));out.getFD().sync();}
                lastSaved=name; completed.clear(); return name;
            } catch(Exception e){return "";}
        }
    }
    public static boolean isRecording(){ synchronized(LOCK){return recording;} }

    public static void onMotionEvent(MotionEvent e) {
        synchronized(LOCK) {
            if(!recording || e==null) return;
            int action=e.getActionMasked(); int index=e.getActionIndex(); long rel=Math.max(0,android.os.SystemClock.uptimeMillis()-startedAt);
            if(action==MotionEvent.ACTION_DOWN || action==MotionEvent.ACTION_POINTER_DOWN){
                int id=e.getPointerId(index); Stroke s=new Stroke(id,rel); active.put(id,s); addPoint(s,e,index,rel);
            } else if(action==MotionEvent.ACTION_MOVE){
                for(int i=0;i<e.getPointerCount();i++){ Stroke s=active.get(e.getPointerId(i)); if(s!=null)addPoint(s,e,i,rel); }
            } else if(action==MotionEvent.ACTION_UP || action==MotionEvent.ACTION_POINTER_UP || action==MotionEvent.ACTION_CANCEL){
                if(action==MotionEvent.ACTION_CANCEL){ for(Stroke s:active.values()){s.duration=Math.max(1,rel-s.start);completed.add(s);} active.clear(); }
                else { int id=e.getPointerId(index); Stroke s=active.remove(id); if(s!=null){addPoint(s,e,index,rel);s.duration=Math.max(1,rel-s.start);completed.add(s);} }
            }
        }
    }
    private static void addPoint(Stroke s,MotionEvent e,int i,long t){
        float x=Math.max(0,Math.min(1,e.getX(i)/Math.max(1,displayW))); float y=Math.max(0,Math.min(1,e.getY(i)/Math.max(1,displayH)));
        if(!s.points.isEmpty()){Point p=s.points.get(s.points.size()-1);if(Math.abs(p.x-x)<0.001f&&Math.abs(p.y-y)<0.001f&&t-p.t<8)return;}
        s.points.add(new Point(x,y,t));
    }

    public static boolean playLatest(Context context, int width, int height) {
        File dir=new File(context.getFilesDir(),"recordings"); File[] files=dir.listFiles((d,n)->n.endsWith(".json"));
        if(files==null||files.length==0)return false;
        java.util.Arrays.sort(files,(a,b)->Long.compare(b.lastModified(),a.lastModified()));
        try(FileInputStream in=new FileInputStream(files[0])){
            byte[] data=new byte[(int)files[0].length()]; int off=0,n; while(off<data.length&&(n=in.read(data,off,data.length-off))>0)off+=n;
            JSONObject root=new JSONObject(new String(data,0,off,StandardCharsets.UTF_8)); JSONArray strokes=root.getJSONArray("strokes");
            GestureDescription.Builder builder=new GestureDescription.Builder(); int added=0;
            for(int i=0;i<strokes.length()&&added<10;i++){
                JSONObject s=strokes.getJSONObject(i); JSONArray pts=s.getJSONArray("points"); if(pts.length()==0)continue;
                Path path=new Path(); JSONObject first=pts.getJSONObject(0); path.moveTo((float)(first.getDouble("x")*width),(float)(first.getDouble("y")*height));
                for(int j=1;j<pts.length();j++){JSONObject p=pts.getJSONObject(j);path.lineTo((float)(p.getDouble("x")*width),(float)(p.getDouble("y")*height));}
                long start=Math.min(60000,Math.max(0,s.getLong("start"))); long duration=Math.min(Math.max(1,60000-start),Math.max(1,s.getLong("duration")));
                builder.addStroke(new GestureDescription.StrokeDescription(path,start,duration)); added++;
            }
            return added>0 && GameControlService.dispatchRecorded(builder.build());
        }catch(Exception e){return false;}
    }
    public static String latestName(Context c){File d=new File(c.getFilesDir(),"recordings");File[] f=d.listFiles((x,n)->n.endsWith(".json"));if(f==null||f.length==0)return "لا توجد تسجيلات محفوظة";java.util.Arrays.sort(f,(a,b)->Long.compare(b.lastModified(),a.lastModified()));return f[0].getName();}
}
