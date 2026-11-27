package com.rioagent.app;

import android.app.Service;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.pm.ServiceInfo;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.IBinder;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Small draggable status overlay; it never injects game input. */
public final class OverlayService extends Service {
    public static final String ACTION_EMERGENCY_STOP="com.rioagent.app.EMERGENCY_STOP";
    private WindowManager wm; private View panel; private TextView status,details; private RadarView radar; private boolean receiverRegistered;
    private final BroadcastReceiver updates=new BroadcastReceiver(){@Override public void onReceive(Context c,Intent i){if(!CaptureService.ACTION_UPDATE.equals(i.getAction()))return;String s=i.getStringExtra("status"),a=i.getStringExtra("analysis");if(status!=null)status.setText(s==null?"RIOAgent":s);if(details!=null)details.setText(a==null?"بانتظار بيانات الرادار":a);if(radar!=null)radar.setTargets(i.getFloatArrayExtra("marker_xy"),i.getFloatExtra("steering",0f));}};
    @Override public void onCreate(){super.onCreate();startPersistentNotification();wm=(WindowManager)getSystemService(WINDOW_SERVICE);buildPanel();IntentFilter f=new IntentFilter(CaptureService.ACTION_UPDATE);if(Build.VERSION.SDK_INT>=33)registerReceiver(updates,f,Context.RECEIVER_NOT_EXPORTED);else registerReceiver(updates,f);receiverRegistered=true;}
    private void startPersistentNotification(){
        String channel="rioagent_overlay";
        if(Build.VERSION.SDK_INT>=26){NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);nm.createNotificationChannel(new NotificationChannel(channel,"RIOAgent floating panel",NotificationManager.IMPORTANCE_LOW));}
        Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(this,channel):new Notification.Builder(this);
        Notification n=b.setContentTitle("RIOAgent يعمل").setContentText("لوحة الرادار العائمة نشطة").setSmallIcon(android.R.drawable.ic_menu_view).setOngoing(true).build();
        if(Build.VERSION.SDK_INT>=34)startForeground(72,n,ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);else startForeground(72,n);
    }
    private void buildPanel(){
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(12),dp(10),dp(12),dp(10));GradientDrawable bg=new GradientDrawable();bg.setColor(0xF20A1725);bg.setCornerRadius(dp(16));bg.setStroke(dp(1),0xff32d6e5);root.setBackground(bg);
        LinearLayout head=new LinearLayout(this);head.setGravity(Gravity.CENTER_VERTICAL);TextView title=text("RIO AGENT  •  RADAR",14,0xff32d6e5,true);head.addView(title,new LinearLayout.LayoutParams(0,-2,1));Button close=button("×");head.addView(close,new LinearLayout.LayoutParams(dp(38),dp(34)));root.addView(head);status=text("الرادار متوقف",14,Color.WHITE,true);status.setPadding(0,dp(8),0,dp(5));root.addView(status);details=text("ابدأ تحليل الشاشة من التطبيق الرئيسي.",11,0xffc2d0dc,false);root.addView(details);radar=new RadarView(this);LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,dp(150));rp.topMargin=dp(8);root.addView(radar,rp);
        Button emergency=button("إيقاف فوري للطوارئ");LinearLayout.LayoutParams ep=new LinearLayout.LayoutParams(-1,dp(40));ep.topMargin=dp(8);root.addView(emergency,ep);
        Button stop=button("إيقاف تحليل الشاشة");LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-1,dp(36));bp.topMargin=dp(6);root.addView(stop,bp);
        close.setOnClickListener(v->stopSelf());
        emergency.setOnClickListener(v->{Intent i=new Intent(ACTION_EMERGENCY_STOP).setPackage(getPackageName());sendBroadcast(i);stopService(new Intent(this,CaptureService.class));status.setText("تم تنفيذ إيقاف الطوارئ");});
        stop.setOnClickListener(v->stopService(new Intent(this,CaptureService.class)));
        root.setOnTouchListener(new View.OnTouchListener(){float sx,sy;int ix,iy;boolean moving;@Override public boolean onTouch(View v,MotionEvent e){if(e.getAction()==MotionEvent.ACTION_DOWN){sx=e.getRawX();sy=e.getRawY();WindowManager.LayoutParams lp=(WindowManager.LayoutParams)panel.getLayoutParams();ix=lp.x;iy=lp.y;moving=true;return true;}if(e.getAction()==MotionEvent.ACTION_MOVE&&moving){WindowManager.LayoutParams lp=(WindowManager.LayoutParams)panel.getLayoutParams();lp.x=ix+(int)(e.getRawX()-sx);lp.y=iy+(int)(e.getRawY()-sy);wm.updateViewLayout(panel,lp);return true;}if(e.getAction()==MotionEvent.ACTION_UP){moving=false;return true;}return false;}});
        int type=Build.VERSION.SDK_INT>=26?WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY:WindowManager.LayoutParams.TYPE_PHONE;
        WindowManager.LayoutParams lp=new WindowManager.LayoutParams(dp(270),-2,type,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,PixelFormat.TRANSLUCENT);lp.gravity=Gravity.TOP|Gravity.END;lp.x=dp(12);lp.y=dp(90);panel=root;wm.addView(panel,lp);
    }
    private TextView text(String s,int size,int color,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);if(bold)t.setTypeface(null,1);return t;}
    private Button button(String s){Button b=new Button(this);b.setText(s);b.setTextSize(12);b.setAllCaps(false);b.setTextColor(Color.WHITE);b.setBackgroundColor(0xff1b3a50);return b;}
    private int dp(int v){return (int)(v*getResources().getDisplayMetrics().density+.5f);}
    @Override public void onDestroy(){if(receiverRegistered){try{unregisterReceiver(updates);}catch(Exception ignored){}receiverRegistered=false;}if(panel!=null){try{wm.removeView(panel);}catch(Exception ignored){}panel=null;}super.onDestroy();}
    @Override public IBinder onBind(Intent i){return null;}
}
