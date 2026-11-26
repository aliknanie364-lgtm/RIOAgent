package com.rioagent.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.objects.DetectedObject;
import com.google.mlkit.vision.objects.ObjectDetection;
import com.google.mlkit.vision.objects.ObjectDetector;
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class CaptureService extends Service {
    public static final String ACTION_UPDATE="com.rioagent.app.CAPTURE_UPDATE";
    public static final String EXTRA_RESULT_CODE="result_code", EXTRA_RESULT_DATA="result_data";
    private static final String CHANNEL="rioagent_capture";
    private MediaProjection projection;
    private VirtualDisplay display;
    private ImageReader reader;
    private HandlerThread thread;
    private Handler handler;
    private ObjectDetector detector;
    private final AtomicBoolean busy=new AtomicBoolean(false);
    private volatile boolean stopped=false;
    private int frames=0;

    @Override public int onStartCommand(Intent intent,int flags,int id) {
        if(intent==null || !intent.hasExtra(EXTRA_RESULT_DATA)){stopSelf();return START_NOT_STICKY;}
        createChannel();
        Notification n=notification("RIOAgent يحلل الشاشة");
        if(Build.VERSION.SDK_INT>=29) startForeground(71,n,ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
        else startForeground(71,n);
        try {
            int code=intent.getIntExtra(EXTRA_RESULT_CODE,0);
            Intent data=Build.VERSION.SDK_INT>=33?intent.getParcelableExtra(EXTRA_RESULT_DATA,Intent.class):intent.getParcelableExtra(EXTRA_RESULT_DATA);
            projection=((MediaProjectionManager)getSystemService(Context.MEDIA_PROJECTION_SERVICE)).getMediaProjection(code,data);
            ObjectDetectorOptions opts=new ObjectDetectorOptions.Builder().setDetectorMode(ObjectDetectorOptions.STREAM_MODE).enableClassification().enableMultipleObjects().build();
            detector=ObjectDetection.getClient(opts);
            thread=new HandlerThread("RIOFrames");thread.start();handler=new Handler(thread.getLooper());
            android.util.DisplayMetrics m=getResources().getDisplayMetrics();
            reader=ImageReader.newInstance(m.widthPixels,m.heightPixels,PixelFormat.RGBA_8888,3);
            reader.setOnImageAvailableListener(this::analyze,handler);
            projection.registerCallback(new MediaProjection.Callback(){@Override public void onStop(){stopCapture();}},handler);
            display=projection.createVirtualDisplay("RIOAgent",m.widthPixels,m.heightPixels,m.densityDpi,DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,reader.getSurface(),null,handler);
            update("التقاط الشاشة نشط","جارٍ تحليل الأجسام...");
        } catch(Exception e){update("فشل بدء الالتقاط",String.valueOf(e.getMessage()));stopCapture();}
        return START_NOT_STICKY;
    }
    private void analyze(ImageReader r) {
        Image im=null;
        try {
            im=r.acquireLatestImage(); if(im==null||stopped)return;
            frames++; if(frames%12!=0||!busy.compareAndSet(false,true))return;
            Image.Plane p=im.getPlanes()[0]; ByteBuffer b=p.getBuffer().duplicate();
            int w=im.getWidth(),h=im.getHeight(),ps=p.getPixelStride(),rs=p.getRowStride();
            int scale=Math.max(1,(int)Math.ceil(Math.max(w,h)/640.0)),ow=Math.max(1,w/scale),oh=Math.max(1,h/scale);
            int[] px=new int[ow*oh];
            for(int y=0;y<oh;y++)for(int x=0;x<ow;x++){
                int i=Math.min(h-1,y*scale)*rs+Math.min(w-1,x*scale)*ps;
                int red=b.get(i)&255,green=b.get(i+1)&255,blue=b.get(i+2)&255;
                px[y*ow+x]=0xff000000|(red<<16)|(green<<8)|blue;
            }
            Bitmap bmp=Bitmap.createBitmap(ow,oh,Bitmap.Config.ARGB_8888);bmp.setPixels(px,0,ow,0,0,ow,oh);
            detector.process(InputImage.fromBitmap(bmp,0))
                .addOnSuccessListener(this::publish)
                .addOnFailureListener(e->update("تعذر تحليل الإطار",String.valueOf(e.getMessage())))
                .addOnCompleteListener(t->{bmp.recycle();busy.set(false);});
        }catch(Exception e){busy.set(false);update("خطأ في الإطار",String.valueOf(e.getMessage()));}
        finally{if(im!=null)im.close();}
    }
    private void publish(List<DetectedObject> objects){
        StringBuilder s=new StringBuilder("الأجسام المكتشفة: ").append(objects.size());
        int count=0;
        for(DetectedObject o:objects){if(count++==8)break;
            s.append("\n• ");
            if(!o.getLabels().isEmpty())s.append(o.getLabels().get(0).getText()).append(" (").append(Math.round(o.getLabels().get(0).getConfidence()*100)).append("%)");
            else s.append("جسم غير مصنف");
            s.append(" — ").append(o.getBoundingBox().centerX()).append(",").append(o.getBoundingBox().centerY());
        }
        s.append("\nالإطارات: ").append(frames).append("\nالكشف عام وليس مخصصاً للعبة.");
        update("الكشف البصري يعمل",s.toString());
    }
    private void update(String status,String analysis){
        Intent i=new Intent(ACTION_UPDATE).setPackage(getPackageName()).putExtra("status",status).putExtra("analysis",analysis);sendBroadcast(i);
        ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).notify(71,notification(status));
    }
    private void createChannel(){if(Build.VERSION.SDK_INT>=26)((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(new NotificationChannel(CHANNEL,"RIOAgent",NotificationManager.IMPORTANCE_LOW));}
    private Notification notification(String s){Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(this,CHANNEL):new Notification.Builder(this);return b.setContentTitle("RIOAgent").setContentText(s).setSmallIcon(android.R.drawable.ic_menu_view).setOngoing(true).build();}
    private void stopCapture(){
        if(stopped)return;stopped=true;
        try{if(display!=null)display.release();}catch(Exception ignored){} display=null;
        try{if(reader!=null)reader.close();}catch(Exception ignored){} reader=null;
        try{if(projection!=null)projection.stop();}catch(Exception ignored){} projection=null;
        try{if(detector!=null)detector.close();}catch(Exception ignored){} detector=null;
        if(thread!=null){thread.quitSafely();thread=null;}
        stopForeground(true);stopSelf();
    }
    @Override public void onDestroy(){stopCapture();super.onDestroy();}
    @Override public IBinder onBind(Intent i){return null;}
}
