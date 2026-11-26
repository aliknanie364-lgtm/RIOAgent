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
    private static boolean recording;
    private static long startedAt;
    private static final Map<Integer, Stroke> active = new LinkedHashMap<>();
    private static final ArrayList<Stroke> completed = new ArrayList<>();
    private static int displayW, displayH;
    private static String lastSaved = "";

    public static final class RecordingInfo {
        public final String name;
        public final long modified;
        public final long bytes;
        RecordingInfo(File f) { name=f.getName(); modified=f.lastModified(); bytes=f.length(); }
    }
    private static final class Point {
        final float x,y; final long t;
        Point(float x,float y,long t){this.x=x;this.y=y;this.t=t;}
    }
    private static final class Stroke {
        final long start; long duration;
        final ArrayList<Point> points=new ArrayList<>();
        Stroke(long start){this.start=start;}
    }
    private TouchRecorder() {}

    public static void start(int width,int height) {
        synchronized(LOCK) {
            active.clear(); completed.clear();
            displayW=Math.max(1,width); displayH=Math.max(1,height);
            startedAt=android.os.SystemClock.uptimeMillis();
            recording=true; lastSaved="";
        }
    }
    public static boolean isRecording(){synchronized(LOCK){return recording;}}

    public static String stopAndSave(Context context) {
        synchronized(LOCK) {
            if(!recording)return lastSaved;
            long now=android.os.SystemClock.uptimeMillis();
            for(Stroke s:active.values()){s.duration=Math.max(1,now-startedAt-s.start);completed.add(s);}
            active.clear(); recording=false;
            if(completed.isEmpty())return "";
            try {
                JSONArray strokes=new JSONArray();
                for(Stroke s:completed) {
                    if(s.points.isEmpty())continue;
                    JSONObject o=new JSONObject(); o.put("start",s.start); o.put("duration",Math.max(1,s.duration));
                    JSONArray pts=new JSONArray();
                    for(Point p:s.points){JSONObject q=new JSONObject();q.put("x",p.x);q.put("y",p.y);q.put("t",p.t);pts.put(q);}
                    o.put("points",pts);strokes.put(o);
                }
                if(strokes.length()==0)return "";
                JSONObject root=new JSONObject();root.put("width",displayW);root.put("height",displayH);
                root.put("strokes",strokes);root.put("created",System.currentTimeMillis());
                File dir=recordingsDir(context);if(!dir.exists()&&!dir.mkdirs())return "";
                String name="RIO-"+System.currentTimeMillis()+".json";File f=new File(dir,name);
                try(FileOutputStream out=new FileOutputStream(f)){
                    out.write(root.toString().getBytes(StandardCharsets.UTF_8));out.getFD().sync();
                }
                lastSaved=name;completed.clear();return name;
            }catch(Exception e){return "";}
        }
    }

    public static void onMotionEvent(MotionEvent e) {
        synchronized(LOCK) {
            if(!recording||e==null)return;
            int action=e.getActionMasked(),index=e.getActionIndex();
            long rel=Math.max(0,android.os.SystemClock.uptimeMillis()-startedAt);
            if(action==MotionEvent.ACTION_DOWN||action==MotionEvent.ACTION_POINTER_DOWN) {
                Stroke s=new Stroke(rel);active.put(e.getPointerId(index),s);addPoint(s,e,index,rel);
            } else if(action==MotionEvent.ACTION_MOVE) {
                for(int i=0;i<e.getPointerCount();i++){
                    Stroke s=active.get(e.getPointerId(i));if(s!=null)addPoint(s,e,i,rel);
                }
            } else if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_POINTER_UP) {
                Stroke s=active.remove(e.getPointerId(index));
                if(s!=null){addPoint(s,e,index,rel);s.duration=Math.max(1,rel-s.start);completed.add(s);}
            } else if(action==MotionEvent.ACTION_CANCEL) {
                for(Stroke s:active.values()){s.duration=Math.max(1,rel-s.start);completed.add(s);}
                active.clear();
            }
        }
    }
    private static void addPoint(Stroke s,MotionEvent e,int i,long t) {
        float x=Math.max(0,Math.min(1,e.getX(i)/Math.max(1,displayW)));
        float y=Math.max(0,Math.min(1,e.getY(i)/Math.max(1,displayH)));
        if(!s.points.isEmpty()){
            Point p=s.points.get(s.points.size()-1);
            if(Math.abs(p.x-x)<.001f&&Math.abs(p.y-y)<.001f&&t-p.t<8)return;
        }
        s.points.add(new Point(x,y,t));
    }
    private static File recordingsDir(Context c){return new File(c.getFilesDir(),"recordings");}

    public static ArrayList<RecordingInfo> listRecordings(Context c) {
        ArrayList<RecordingInfo> out=new ArrayList<>();
        File[] files=recordingsDir(c).listFiles((d,n)->n.endsWith(".json"));
        if(files==null)return out;
        java.util.Arrays.sort(files,(a,b)->Long.compare(b.lastModified(),a.lastModified()));
        for(File f:files)out.add(new RecordingInfo(f));
        return out;
    }
    public static boolean rename(Context c,String oldName,String newName) {
        if(oldName==null||newName==null||oldName.contains("/")||oldName.contains("\\"))return false;
        String clean=newName.trim().replaceAll("[^A-Za-z0-9_\\-\\u0600-\\u06FF ]","");
        if(clean.isEmpty())return false;
        if(!clean.endsWith(".json"))clean+=".json";
        if(clean.contains("/")||clean.contains("\\"))return false;
        File dir=recordingsDir(c),from=new File(dir,oldName),to=new File(dir,clean);
        return from.isFile()&&!to.exists()&&from.renameTo(to);
    }
    public static boolean delete(Context c,String name) {
        if(name==null||name.contains("/")||name.contains("\\"))return false;
        File f=new File(recordingsDir(c),name);return f.isFile()&&f.delete();
    }
    public static boolean playLatest(Context c,int w,int h) {
        ArrayList<RecordingInfo> all=listRecordings(c);
        return !all.isEmpty()&&play(c,all.get(0).name,w,h);
    }
    public static boolean play(Context c,String name,int width,int height) {
        if(name==null||name.contains("/")||name.contains("\\"))return false;
        File f=new File(recordingsDir(c),name);if(!f.isFile())return false;
        try(FileInputStream in=new FileInputStream(f)){
            byte[] data=new byte[(int)Math.min(Integer.MAX_VALUE,f.length())];
            int off=0,n;while(off<data.length&&(n=in.read(data,off,data.length-off))>0)off+=n;
            JSONObject root=new JSONObject(new String(data,0,off,StandardCharsets.UTF_8));
            JSONArray strokes=root.getJSONArray("strokes");
            GestureDescription.Builder builder=new GestureDescription.Builder();int added=0;
            for(int i=0;i<strokes.length()&&added<20;i++){
                JSONObject s=strokes.getJSONObject(i);JSONArray pts=s.getJSONArray("points");
                if(pts.length()==0)continue;
                Path path=new Path();JSONObject first=pts.getJSONObject(0);
                path.moveTo((float)(first.getDouble("x")*width),(float)(first.getDouble("y")*height));
                for(int j=1;j<pts.length();j++){
                    JSONObject p=pts.getJSONObject(j);
                    path.lineTo((float)(p.getDouble("x")*width),(float)(p.getDouble("y")*height));
                }
                long start=Math.min(60000,Math.max(0,s.getLong("start")));
                long duration=Math.min(Math.max(1,60000-start),Math.max(1,s.getLong("duration")));
                builder.addStroke(new GestureDescription.StrokeDescription(path,start,duration));added++;
            }
            return added>0&&GameControlService.dispatchRecorded(builder.build());
        }catch(Exception e){return false;}
    }
    public static String latestName(Context c) {
        ArrayList<RecordingInfo> all=listRecordings(c);
        return all.isEmpty()?"لا توجد تسجيلات محفوظة":all.get(0).name;
    }
}
