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
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/** Captures frames, detects red targets and publishes decisions. It deliberately does not drive until controls are calibrated. */
public final class CaptureService extends Service {
    public static final String ACTION_UPDATE="com.rioagent.app.CAPTURE_UPDATE";
    public static final String EXTRA_RESULT_CODE="result_code", EXTRA_RESULT_DATA="result_data";
    private static final String CHANNEL="rioagent_capture";
    private MediaProjection projection; private VirtualDisplay display; private ImageReader reader;
    private HandlerThread thread; private Handler handler;
    private final AtomicBoolean busy=new AtomicBoolean(false);
    private final RedMarkerDetector markerDetector=new RedMarkerDetector();
    private final DecisionEngine engine=new DecisionEngine();
    private final AgentState state=new AgentState();
    private volatile boolean stopped; private int frames,analyzed; private volatile float[] markerCoordinates=new float[0];
    private static volatile boolean serviceActive;
    private long lastDriveAt=0L,lastSearchTurnAt=0L;
    private float searchSteering=0.28f;

    public static boolean isActive(){return serviceActive;}

    @Override public int onStartCommand(Intent intent,int flags,int id){
        serviceActive=true;
        if(projection!=null){update("الالتقاط يعمل بالفعل",state.getMessage());return START_NOT_STICKY;}
        if(intent==null||!intent.hasExtra(EXTRA_RESULT_DATA)){serviceActive=false;AgentConfig.autoEnabled=false;update("إذن الشاشة غير موجود", "ابدأ التحليل ووافق على نافذة أندرويد.");stopSelf();return START_NOT_STICKY;}
        createChannel();
        Notification n=notification("تهيئة الرادار");
        if(Build.VERSION.SDK_INT>=29)startForeground(71,n,ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);else startForeground(71,n);
        stopped=false; state.setMode(AgentState.Mode.INITIALIZING,"تهيئة الالتقاط");
        try{
            int code=intent.getIntExtra(EXTRA_RESULT_CODE,0);
            Intent data=Build.VERSION.SDK_INT>=33?intent.getParcelableExtra(EXTRA_RESULT_DATA,Intent.class):intent.getParcelableExtra(EXTRA_RESULT_DATA);
            if(data==null)throw new IllegalStateException("لم يصل رمز موافقة التقاط الشاشة");
            projection=((MediaProjectionManager)getSystemService(Context.MEDIA_PROJECTION_SERVICE)).getMediaProjection(code,data);
            if(projection==null)throw new IllegalStateException("تعذر إنشاء MediaProjection");
            thread=new HandlerThread("RIOAgentFrames");thread.start();handler=new Handler(thread.getLooper());
            android.util.DisplayMetrics m=getResources().getDisplayMetrics();
            reader=ImageReader.newInstance(m.widthPixels,m.heightPixels,PixelFormat.RGBA_8888,3);
            reader.setOnImageAvailableListener(this::analyze,handler);
            projection.registerCallback(new MediaProjection.Callback(){@Override public void onStop(){update("توقف إذن مشاركة الشاشة","أعد بدء التحليل وامنح الإذن مجدداً.");stopCapture();}},handler);
            display=projection.createVirtualDisplay("RIOAgentRadar",m.widthPixels,m.heightPixels,m.densityDpi,DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,reader.getSurface(),null,handler);
            if(display==null)throw new IllegalStateException("فشل إنشاء شاشة الالتقاط");
            state.setMode(AgentState.Mode.SCANNING,"الرادار يعمل — ينتظر العلامة الحمراء");update("الرادار نشط","جارٍ انتظار أول إطار...");
        }catch(Exception e){state.setMode(AgentState.Mode.ERROR,"فشل بدء الالتقاط");update("فشل بدء الالتقاط",readable(e));stopCapture();}
        return START_NOT_STICKY;
    }

    private void analyze(ImageReader source){
        Image image=null;
        try{
            image=source.acquireLatestImage();if(image==null||stopped)return;
            frames++;if(frames%4!=0||!busy.compareAndSet(false,true))return;
            Image.Plane plane=image.getPlanes()[0];ByteBuffer b=plane.getBuffer().duplicate();
            int width=image.getWidth(),height=image.getHeight(),pixel=plane.getPixelStride(),row=plane.getRowStride();
            int scale=Math.max(1,(int)Math.ceil(Math.max(width,height)/(double)AgentConfig.PROCESS_WIDTH));
            int outW=Math.max(1,width/scale),outH=Math.max(1,height/scale);int[] colors=new int[outW*outH];
            int limit=b.limit();
            for(int y=0;y<outH;y++)for(int x=0;x<outW;x++){
                int sx=Math.min(width-1,x*scale),sy=Math.min(height-1,y*scale),idx=sy*row+sx*pixel;
                if(idx+2>=limit)continue;
                int r=b.get(idx)&255,g=b.get(idx+1)&255,bl=b.get(idx+2)&255;
                colors[y*outW+x]=0xff000000|(r<<16)|(g<<8)|bl;
            }
            Bitmap bitmap=Bitmap.createBitmap(outW,outH,Bitmap.Config.ARGB_8888);bitmap.setPixels(colors,0,outW,0,0,outW,outH);
            boolean queued = handler != null && handler.post(()->{
                try{
                    if(stopped)return;
                    List<RedMarkerDetector.Marker> markers=markerDetector.detect(bitmap);
                    DecisionEngine.Command command=engine.decide(markers);
                    float[] xy=new float[markers.size()*4];for(int k=0;k<markers.size();k++){RedMarkerDetector.Marker m=markers.get(k);int p=k*4;xy[p]=m.left/(float)Math.max(1,outW-1);xy[p+1]=m.top/(float)Math.max(1,outH-1);xy[p+2]=m.right/(float)Math.max(1,outW-1);xy[p+3]=m.bottom/(float)Math.max(1,outH-1);}markerCoordinates=xy;
                    analyzed++;state.onFrame(markers.size());state.setControls(command.steering,command.throttle);
                    runAdaptiveControl(command);
                    String mode=AgentConfig.autoEnabled?"القيادة التكيفية تعمل": "وضع الرادار — القيادة متوقفة";
                    state.setMode(command.hasTarget?AgentState.Mode.TRACKING:AgentState.Mode.SCANNING,mode);
                    String msg="الإطارات: "+frames+" • التحليلات: "+analyzed+"\nالأهداف الحمراء: "+markers.size()+"\nاتجاه التوجيه: "+Math.round(command.steering*100)+"%\nالهدف: "+(command.hasTarget?Math.round(command.targetX*100)+"%, "+Math.round(command.targetY*100)+"%":"جارٍ البحث")+"\nالقرار: "+(command.hasTarget?(command.attack?"اقتراب/ضرب":(command.run?"مطاردة سريعة":"توجيه")):"مسح حذر")+"\nالتحكم: "+(AgentConfig.autoEnabled?"نشط":"متوقف");
                    update(command.hasTarget?"تم رصد هدف":"جارٍ البحث",msg);
                }catch(Exception e){update("خطأ تحليل","تعذر تحليل الإطار: "+readable(e));}
                finally{if(!bitmap.isRecycled())bitmap.recycle();busy.set(false);}
            });
            // If the frame handler is shutting down, release the frame and unlock analysis.
            if(!queued){if(!bitmap.isRecycled())bitmap.recycle();busy.set(false);}
        }catch(Exception e){busy.set(false);update("خطأ قراءة الشاشة",readable(e));}
        finally{if(image!=null)image.close();}
    }
    private void runAdaptiveControl(DecisionEngine.Command command){
        if(!AgentConfig.autoEnabled||!AgentConfig.controlsCalibrated||!GameControlService.isReady())return;
        long now=android.os.SystemClock.uptimeMillis();
        if(now-lastDriveAt<420)return;
        float steering=command.steering,throttle=command.throttle;
        boolean attack=command.attack,run=command.run;
        if(!command.hasTarget){
            // Low-speed search sweep; never attack without a detected enemy health bar.
            if(now-lastSearchTurnAt>1800){searchSteering=-searchSteering;lastSearchTurnAt=now;}
            steering=searchSteering;throttle=0.22f;attack=false;run=false;
        }
        boolean accepted=GameControlService.executeAgentCommand(steering,throttle,attack,run);
        if(accepted)lastDriveAt=now;
    }

    private String readable(Exception e){return e.getMessage()==null?e.getClass().getSimpleName():e.getMessage();}
    private void update(String status,String analysis){
        Intent i=new Intent(ACTION_UPDATE).setPackage(getPackageName());i.putExtra("status",status);i.putExtra("analysis",analysis);i.putExtra("frames",frames);i.putExtra("markers",state.getMarkers());i.putExtra("steering",state.getSteering());i.putExtra("marker_xy",markerCoordinates);sendBroadcast(i);
        NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);if(nm!=null)nm.notify(71,notification(status));
    }
    private void createChannel(){if(Build.VERSION.SDK_INT>=26){NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);nm.createNotificationChannel(new NotificationChannel(CHANNEL,"RIOAgent radar",NotificationManager.IMPORTANCE_LOW));}}
    private Notification notification(String text){Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(this,CHANNEL):new Notification.Builder(this);return b.setContentTitle("RIOAgent").setContentText(text).setSmallIcon(android.R.drawable.ic_menu_view).setOngoing(true).build();}
    private void stopCapture(){if(stopped)return;stopped=true;serviceActive=false;AgentConfig.autoEnabled=false;GameControlService.stopContinuous();try{if(display!=null)display.release();}catch(Exception ignored){}display=null;try{if(reader!=null){reader.setOnImageAvailableListener(null,null);reader.close();}}catch(Exception ignored){}reader=null;try{if(projection!=null)projection.stop();}catch(Exception ignored){}projection=null;engine.reset();if(thread!=null){thread.quitSafely();thread=null;}stopForeground(true);stopSelf();}
    @Override public void onDestroy(){serviceActive=false;AgentConfig.autoEnabled=false;stopCapture();super.onDestroy();}
    @Override public IBinder onBind(Intent intent){return null;}
}
