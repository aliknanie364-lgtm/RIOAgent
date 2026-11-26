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

/** Records batched MotionEvent samples and their original event timestamps. */
public final class TouchRecorder {
    private static final Object LOCK = new Object();
    private static final int MAX_POINTS_PER_STROKE = 12000;
    private static final int MAX_STROKES = 20;
    private static final long MAX_GESTURE_MS = 60000L;
    private static final long MAX_FILE_BYTES = 16L * 1024L * 1024L;
    private static boolean recording;
    private static long recordingStart;
    private static final Map<Integer, Stroke> active = new LinkedHashMap<>();
    private static final ArrayList<Stroke> completed = new ArrayList<>();
    private static int displayW, displayH;
    private static String lastSaved = "";

    public static final class RecordingInfo {
        public final String name; public final long modified, bytes;
        RecordingInfo(File f) { name=f.getName(); modified=f.lastModified(); bytes=f.length(); }
    }
    private static final class Point {
        final float x,y; final long t;
        Point(float x,float y,long t) { this.x=x; this.y=y; this.t=t; }
    }
    private static final class Stroke {
        final long start; long duration;
        final ArrayList<Point> points = new ArrayList<>();
        Stroke(long start) { this.start=start; }
    }
    private TouchRecorder() {}

    public static void start(int width,int height) {
        synchronized (LOCK) {
            active.clear(); completed.clear();
            displayW=Math.max(1,width); displayH=Math.max(1,height);
            recordingStart=android.os.SystemClock.uptimeMillis();
            recording=true; lastSaved="";
        }
    }
    public static boolean isRecording() { synchronized (LOCK) { return recording; } }

    public static String stopAndSave(Context context) {
        synchronized (LOCK) {
            if (!recording) return lastSaved;
            long now=Math.max(0,android.os.SystemClock.uptimeMillis()-recordingStart);
            for (Stroke s:active.values()) {
                s.duration=Math.max(s.duration,now-s.start);
                if (!s.points.isEmpty()) completed.add(s);
            }
            active.clear(); recording=false;
            if (completed.isEmpty()) return "";
            try {
                JSONArray strokes=new JSONArray();
                for (Stroke s:completed) {
                    if (s.points.isEmpty()) continue;
                    JSONObject o=new JSONObject(); o.put("start",s.start); o.put("duration",Math.max(1,s.duration));
                    JSONArray pts=new JSONArray();
                    for (Point p:s.points) {
                        JSONObject q=new JSONObject(); q.put("x",p.x); q.put("y",p.y); q.put("t",p.t); pts.put(q);
                    }
                    o.put("points",pts); strokes.put(o);
                }
                if (strokes.length()==0) return "";
                JSONObject root=new JSONObject(); root.put("width",displayW); root.put("height",displayH);
                root.put("strokes",strokes); root.put("created",System.currentTimeMillis());
                File dir=recordingsDir(context); if (!dir.exists()&&!dir.mkdirs()) return "";
                String name="RIO-"+System.currentTimeMillis()+".json"; File f=new File(dir,name);
                try (FileOutputStream out=new FileOutputStream(f)) {
                    out.write(root.toString().getBytes(StandardCharsets.UTF_8)); out.getFD().sync();
                }
                lastSaved=name; completed.clear(); return name;
            } catch (Exception e) { return ""; }
        }
    }

    public static void onMotionEvent(MotionEvent e) {
        if (e==null) return;
        synchronized (LOCK) {
            if (!recording) return;
            int action=e.getActionMasked(), index=e.getActionIndex();
            long t=relative(e.getEventTime());
            if (action==MotionEvent.ACTION_DOWN || action==MotionEvent.ACTION_POINTER_DOWN) {
                Stroke s=new Stroke(t); active.put(e.getPointerId(index),s);
                addPoint(s,e.getX(index),e.getY(index),t); return;
            }
            if (action==MotionEvent.ACTION_MOVE) {
                // Android batches intermediate coordinates; record them in chronological order.
                for (int h=0;h<e.getHistorySize();h++) {
                    long ht=relative(e.getHistoricalEventTime(h));
                    for (int i=0;i<e.getPointerCount();i++) {
                        Stroke s=active.get(e.getPointerId(i));
                        if (s!=null) addPoint(s,e.getHistoricalX(i,h),e.getHistoricalY(i,h),ht);
                    }
                }
                for (int i=0;i<e.getPointerCount();i++) {
                    Stroke s=active.get(e.getPointerId(i));
                    if (s!=null) addPoint(s,e.getX(i),e.getY(i),t);
                }
                return;
            }
            if (action==MotionEvent.ACTION_UP || action==MotionEvent.ACTION_POINTER_UP) {
                Stroke s=active.remove(e.getPointerId(index));
                if (s!=null) {
                    addPoint(s,e.getX(index),e.getY(index),t);
                    s.duration=Math.max(1,t-s.start);
                    if (!s.points.isEmpty()) completed.add(s);
                }
                return;
            }
            if (action==MotionEvent.ACTION_CANCEL) {
                for (Stroke s:active.values()) {
                    s.duration=Math.max(1,t-s.start);
                    if (!s.points.isEmpty()) completed.add(s);
                }
                active.clear();
            }
        }
    }
    private static long relative(long eventTime) { return Math.max(0,eventTime-recordingStart); }
    private static void addPoint(Stroke s,float rawX,float rawY,long t) {
        if (s.points.size()>=MAX_POINTS_PER_STROKE) return;
        float x=Math.max(0f,Math.min(1f,rawX/Math.max(1,displayW)));
        float y=Math.max(0f,Math.min(1f,rawY/Math.max(1,displayH)));
        if (!s.points.isEmpty()) {
            Point last=s.points.get(s.points.size()-1);
            if (t<last.t) t=last.t;
            if (Math.abs(last.x-x)<0.00001f && Math.abs(last.y-y)<0.00001f && t==last.t) return;
        }
        s.points.add(new Point(x,y,t)); s.duration=Math.max(s.duration,t-s.start);
    }
    private static File recordingsDir(Context c) { return new File(c.getFilesDir(),"recordings"); }

    public static ArrayList<RecordingInfo> listRecordings(Context c) {
        ArrayList<RecordingInfo> out=new ArrayList<>();
        File[] files=recordingsDir(c).listFiles((d,n)->n.endsWith(".json"));
        if (files==null) return out;
        java.util.Arrays.sort(files,(a,b)->Long.compare(b.lastModified(),a.lastModified()));
        for (File f:files) out.add(new RecordingInfo(f)); return out;
    }
    public static boolean rename(Context c,String oldName,String newName) {
        if (oldName==null||newName==null||oldName.contains("/")||oldName.indexOf('\\')>=0) return false;
        String clean=newName.trim().replaceAll("[^A-Za-z0-9_\\-\\u0600-\\u06FF ]","");
        if (clean.isEmpty()) return false; if (!clean.endsWith(".json")) clean+=".json";
        File dir=recordingsDir(c),from=new File(dir,oldName),to=new File(dir,clean);
        return from.isFile()&&!to.exists()&&from.renameTo(to);
    }
    public static boolean delete(Context c,String name) {
        if (name==null||name.contains("/")||name.indexOf('\\')>=0) return false;
        File f=new File(recordingsDir(c),name); return f.isFile()&&f.delete();
    }
    public static boolean playLatest(Context c,int w,int h) {
        ArrayList<RecordingInfo> all=listRecordings(c); return !all.isEmpty()&&play(c,all.get(0).name,w,h);
    }
    public static boolean play(Context c,String name,int width,int height) {
        if (name==null||name.contains("/")||name.indexOf('\\')>=0||width<=0||height<=0) return false;
        File f=new File(recordingsDir(c),name);
        if (!f.isFile()||f.length()<=0||f.length()>MAX_FILE_BYTES) return false;
        try (FileInputStream in=new FileInputStream(f)) {
            byte[] data=new byte[(int)f.length()]; int off=0,n;
            while (off<data.length&&(n=in.read(data,off,data.length-off))>0) off+=n;
            JSONObject root=new JSONObject(new String(data,0,off,StandardCharsets.UTF_8));
            JSONArray strokes=root.getJSONArray("strokes");
            GestureDescription.Builder builder=new GestureDescription.Builder(); int added=0;
            for (int i=0;i<strokes.length()&&added<MAX_STROKES;i++) {
                JSONObject s=strokes.getJSONObject(i); JSONArray pts=s.getJSONArray("points");
                if (pts.length()==0) continue;
                long start=Math.max(0,s.optLong("start",0));
                long duration=Math.max(1,s.optLong("duration",1));
                if (start>=MAX_GESTURE_MS) continue;
                duration=Math.min(duration,MAX_GESTURE_MS-start);
                Path path=new Path(); JSONObject first=pts.getJSONObject(0);
                path.moveTo(clamp(first.optDouble("x",0))*width,clamp(first.optDouble("y",0))*height);
                for (int j=1;j<pts.length();j++) {
                    JSONObject p=pts.getJSONObject(j);
                    path.lineTo(clamp(p.optDouble("x",0))*width,clamp(p.optDouble("y",0))*height);
                }
                builder.addStroke(new GestureDescription.StrokeDescription(path,start,duration)); added++;
            }
            return added>0&&GameControlService.dispatchRecorded(builder.build());
        } catch (Exception e) { return false; }
    }
    private static float clamp(double v) { return (float)Math.max(0,Math.min(1,v)); }
    public static String latestName(Context c) {
        ArrayList<RecordingInfo> all=listRecordings(c); return all.isEmpty()?"لا توجد تسجيلات محفوظة":all.get(0).name;
    }
}
