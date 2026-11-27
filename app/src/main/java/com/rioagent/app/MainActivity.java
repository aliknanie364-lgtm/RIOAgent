package com.rioagent.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import android.net.Uri;
import android.provider.Settings;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private static final int REQUEST_CAPTURE=4102;
    private static final int BG=0xFF081321,PANEL=0xFF122337,PANEL2=0xFF192F46,CYAN=0xFF32D6E5,WHITE=0xFFF2F7FC,MUTED=0xFF9DB1C5;
    private TextView statusText,controlText,autoText,recorderStatus;
    private Button stopRecordingButton;
    private boolean ignoringStopButtonTouch;
    private boolean receiverRegistered=false,autoRunning=false,emergencyReceiverRegistered=false,pendingAutoStart=false;
    private final BroadcastReceiver emergencyReceiver=new BroadcastReceiver(){
        @Override public void onReceive(Context c,Intent i){ if(OverlayService.ACTION_EMERGENCY_STOP.equals(i.getAction())) stopAutomation("إيقاف طوارئ من اللوحة العائمة"); }
    };

    private final BroadcastReceiver captureReceiver=new BroadcastReceiver(){
        @Override public void onReceive(Context c,Intent i){
            if(i==null||!CaptureService.ACTION_UPDATE.equals(i.getAction()))return;
            String status=i.getStringExtra("status"),analysis=i.getStringExtra("analysis");
            if(statusText!=null)statusText.setText("تحليل الشاشة: "+(status==null?"":status)+(analysis==null||analysis.isEmpty()?"":"\n"+analysis));
        }
    };

    @Override public void onCreate(Bundle b){
        super.onCreate(b);buildUi();
        IntentFilter emergency=new IntentFilter(OverlayService.ACTION_EMERGENCY_STOP);
        if(Build.VERSION.SDK_INT>=33)registerReceiver(emergencyReceiver,emergency,Context.RECEIVER_NOT_EXPORTED);else registerReceiver(emergencyReceiver,emergency);
        emergencyReceiverRegistered=true;
    }
    @Override protected void onStart(){
        super.onStart();
        if(!receiverRegistered){
            IntentFilter f=new IntentFilter(CaptureService.ACTION_UPDATE);
            if(Build.VERSION.SDK_INT>=33)registerReceiver(captureReceiver,f,Context.RECEIVER_NOT_EXPORTED);else registerReceiver(captureReceiver,f);
            receiverRegistered=true;
        }
    }
    @Override protected void onStop(){
        // Do not stop the main-thread automation loop merely because the user switched to the game.
        if(receiverRegistered){unregisterReceiver(captureReceiver);receiverRegistered=false;}
        super.onStop();
    }
    @Override protected void onResume(){super.onResume();if(controlText!=null)updateControlStatus();}
    @Override protected void onDestroy(){
        if(isFinishing())stopAutomation("تم إغلاق التطبيق");
        if(emergencyReceiverRegistered){try{unregisterReceiver(emergencyReceiver);}catch(Exception ignored){}emergencyReceiverRegistered=false;}
        super.onDestroy();
    }
    @Override public void onBackPressed(){stopAutomation("تم إيقاف الأتمتة");super.onBackPressed();}

    private void buildUi(){
        getWindow().setStatusBarColor(0xFF07111D);
        getWindow().setNavigationBarColor(0xFF07111D);
        LinearLayout page=new LinearLayout(this);page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(18),dp(16),dp(18),dp(28));page.setBackgroundColor(0xFF07111D);

        LinearLayout hero=new LinearLayout(this);hero.setOrientation(LinearLayout.VERTICAL);
        hero.setPadding(dp(18),dp(18),dp(18),dp(18));
        GradientDrawable heroBg=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{0xFF12334A,0xFF0B1D2D,0xFF102638});
        heroBg.setCornerRadius(dp(22));heroBg.setStroke(dp(1),0xFF286078);hero.setBackground(heroBg);
        LinearLayout brandRow=new LinearLayout(this);brandRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView brand=label("RIO",32,WHITE,true);brandRow.addView(brand,new LinearLayout.LayoutParams(0,-2,1));
        TextView version=label("AGENT 2.3",11,CYAN,true);version.setPadding(dp(10),dp(6),dp(10),dp(6));version.setBackground(round(0xFF173D50,dp(18)));brandRow.addView(version);hero.addView(brandRow);
        TextView sub=label("AUTONOMOUS FIELD OPERATIONS",10,0xFF8CEAF0,true);sub.setPadding(0,dp(2),0,dp(10));hero.addView(sub);
        hero.addView(label("مركز القيادة والتحكم",22,WHITE,true));
        TextView desc=label("رصد الأهداف • تحليل الشاشة • تحكم باللمس",13,0xFFB6C9D8,false);desc.setPadding(0,dp(5),0,dp(12));hero.addView(desc);
        TextView live=label("●  SYSTEM READY  •  RIO RISE / LANDSCAPE",10,0xFF7FE5B2,true);live.setPadding(dp(10),dp(7),dp(10),dp(7));live.setBackground(round(0xFF123B38,dp(18)));hero.addView(live);
        page.addView(hero,match());

        page.addView(section("لوحة الحالة المباشرة"));
        LinearLayout metrics=new LinearLayout(this);metrics.setOrientation(LinearLayout.HORIZONTAL);
        metrics.addView(metric("RADAR","LIVE"),new LinearLayout.LayoutParams(0,-2,1));
        LinearLayout.LayoutParams mid=new LinearLayout.LayoutParams(0,-2,1);mid.leftMargin=dp(7);mid.rightMargin=dp(7);
        metrics.addView(metric("CONTROL","TOUCH"),mid);metrics.addView(metric("MODE","ADAPTIVE"),new LinearLayout.LayoutParams(0,-2,1));
        page.addView(metrics,match());
        statusText=card("تحليل الشاشة: متوقف\nابدأ الالتقاط لعرض الإطارات والأهداف واتجاه الحركة.");
        controlText=card("التحكم: جارٍ التحقق من صلاحية إمكانية الوصول");page.addView(statusText,match());page.addView(controlText,match());
        addButton(page,"تفعيل صلاحية التحكم",()->startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)),true);
        addButton(page,"إظهار لوحة الرادار العائمة",this::showOverlay,false);

        page.addView(section("مسجّل الحركات"));recorderStatus=card("جاهز للتسجيل. فعّل خدمة إمكانية الوصول أولاً.");page.addView(recorderStatus,match());
        addButton(page,"●  بدء تسجيل حركاتي",this::startRecording,true);
        stopRecordingButton=addButton(page,"■  إيقاف التسجيل وحفظه",()->{String saved=TouchRecorder.stopAndSave(this);recorderStatus.setText(saved.isEmpty()?"لم تُحفظ حركات.":"تم حفظ التسجيل: "+saved);},false);
        addButton(page,"مكتبة التسجيلات وإدارتها",this::showRecordings,false);
        addButton(page,"تشغيل آخر تسجيل",()->{if(!GameControlService.isReady()){toast("فعّل خدمة التحكم أولاً");return;}boolean ok=TouchRecorder.playLatest(this,screenWidth(),screenHeight());recorderStatus.setText(ok?"تم إرسال التسجيل الأخير للتشغيل.":"تعذر التشغيل؛ تحقق من وجود تسجيل محفوظ وخدمة التحكم.");},false);

        page.addView(section("الرصد والرادار"));
        page.addView(card("يحلّل التطبيق إطارات الشاشة بحثاً عن العلامات الحمراء، ثم يرسل إحداثياتها واتجاه التوجيه إلى الرادار العائم."),match());
        addButton(page,"بدء تحليل الشاشة",()->{MediaProjectionManager m=(MediaProjectionManager)getSystemService(MEDIA_PROJECTION_SERVICE);startActivityForResult(m.createScreenCaptureIntent(),REQUEST_CAPTURE);},true);
        addButton(page,"إيقاف تحليل الشاشة",()->{stopService(new Intent(this,CaptureService.class));statusText.setText("تحليل الشاشة: تم طلب الإيقاف");},false);

        page.addView(section("اختبار اللمس"));
        addButton(page,"نقرة تجريبية بمنتصف الشاشة",()->{boolean ok=GameControlService.tap(screenWidth()/2f,screenHeight()/2f,90);statusText.setText(ok?"تم إرسال النقرة التجريبية":"تعذر إرسال النقرة؛ تحقق من الصلاحية");},false);
        addButton(page,"سحب تجريبي",()->{boolean ok=GameControlService.swipe(screenWidth()*.25f,screenHeight()*.7f,screenWidth()*.75f,screenHeight()*.7f,350);statusText.setText(ok?"تم إرسال السحب التجريبي":"تعذر إرسال السحب؛ تحقق من الصلاحية");},false);

        page.addView(section("اللعب التلقائي التكيفي"));
        page.addView(card("يختار المحرك أقرب شريط صحة أحمر مرصود، ينعّم اتجاه المطاردة، ويعدّل الحركة والضرب والركض. يتوقف الهجوم عند فقدان الهدف."),match());
        page.addView(card("ملف التحكم الأفقي: عصا الحركة 37.1% / 94.7% • الضرب 83.6% / 67.4% • الركض 90.2% / 81.4%."),match());
        autoText=card("القيادة متوقفة — ابدأ تحليل الشاشة أولاً");page.addView(autoText,match());
        addButton(page,"تشغيل اللعب التلقائي التكيفي",this::startAutomation,true);
        addButton(page,"إيقاف فوري للطوارئ",()->stopAutomation("تم الإيقاف يدوياً"),false);

        TextView note=label("ملاحظة: تسجيل اللمس الحالي يلتقط أحداث اللمس داخل واجهة RIOAgent فقط؛ لا يدّعي تسجيل لمسات اللعبة الخارجية.",12,MUTED,false);
        note.setPadding(dp(4),dp(14),dp(4),0);page.addView(note,match());
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.addView(page);setContentView(scroll);updateControlStatus();
    }

    private LinearLayout metric(String name,String value){
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(10),dp(12),dp(8),dp(12));box.setBackground(round(PANEL,dp(14)));
        box.addView(label(name,9,MUTED,true));TextView val=label(value,12,CYAN,true);val.setPadding(0,dp(5),0,0);box.addView(val);return box;
    }

    private boolean isInsideStopButton(float x,float y){
        if(stopRecordingButton==null||stopRecordingButton.getWidth()==0)return false;
        int[] p=new int[2];stopRecordingButton.getLocationOnScreen(p);
        return x>=p[0]&&x<=p[0]+stopRecordingButton.getWidth()&&y>=p[1]&&y<=p[1]+stopRecordingButton.getHeight();
    }
    @Override public boolean dispatchTouchEvent(android.view.MotionEvent e){
        if(e==null)return super.dispatchTouchEvent(e);
        int action=e.getActionMasked();
        if(action==android.view.MotionEvent.ACTION_DOWN)ignoringStopButtonTouch=isInsideStopButton(e.getRawX(),e.getRawY());
        if(!ignoringStopButtonTouch&&TouchRecorder.isRecording()){
            android.view.MotionEvent copy=android.view.MotionEvent.obtain(e);
            copy.offsetLocation(e.getRawX()-e.getX(),e.getRawY()-e.getY());
            TouchRecorder.onMotionEvent(copy);copy.recycle();
        }
        boolean handled=super.dispatchTouchEvent(e);
        if(action==android.view.MotionEvent.ACTION_UP||action==android.view.MotionEvent.ACTION_CANCEL)ignoringStopButtonTouch=false;
        return handled;
    }

    private void showOverlay(){
        if(Build.VERSION.SDK_INT>=23 && !Settings.canDrawOverlays(this)){
            Intent permission=new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:"+getPackageName()));
            startActivity(permission);
            toast("اسمح لـ RIOAgent بالظهور فوق التطبيقات ثم اضغط الزر مرة أخرى");
            return;
        }
        Intent overlay=new Intent(this,OverlayService.class);
        if(Build.VERSION.SDK_INT>=26)startForegroundService(overlay);else startService(overlay);
        toast("تم إظهار لوحة الرادار العائمة");
    }

    private void startRecording(){
        if(TouchRecorder.isRecording()){recorderStatus.setText("التسجيل يعمل بالفعل.");return;}
        TouchRecorder.start(screenWidth(),screenHeight());
        recorderStatus.setText("● التسجيل جارٍ داخل RIOAgent فقط. لا يستطيع Android تسجيل لمس التطبيقات الأخرى بهذه الصلاحيات.");
    }
    private void showRecordings(){
        java.util.ArrayList<TouchRecorder.RecordingInfo> list=TouchRecorder.listRecordings(this);
        if(list.isEmpty()){new AlertDialog.Builder(this).setTitle("مكتبة التسجيلات").setMessage("لا توجد تسجيلات محفوظة بعد. ابدأ تسجيلاً ثم احفظه.").setPositiveButton("حسناً",null).show();return;}
        String[] names=new String[list.size()];
        for(int i=0;i<list.size();i++)names[i]=list.get(i).name+" • "+android.text.format.Formatter.formatShortFileSize(this,list.get(i).bytes);
        new AlertDialog.Builder(this).setTitle("التسجيلات المحفوظة").setItems(names,(d,w)->recordingActions(list.get(w).name)).setNegativeButton("إغلاق",null).show();
    }
    private void recordingActions(String name){
        String[] actions={"تشغيل","إعادة تسمية","حذف"};
        new AlertDialog.Builder(this).setTitle(name).setItems(actions,(d,w)->{
            if(w==0){
                boolean ok=GameControlService.isReady()&&TouchRecorder.play(this,name,screenWidth(),screenHeight());
                recorderStatus.setText(ok?"تم إرسال التسجيل للتشغيل.":"تعذر التشغيل؛ تحقق من الخدمة وصحة التسجيل.");
            }else if(w==1)renameRecording(name);
            else new AlertDialog.Builder(this).setTitle("حذف التسجيل؟").setMessage("سيتم حذف "+name+" نهائياً.")
                .setNegativeButton("إلغاء",null).setPositiveButton("حذف",(x,y)->toast(TouchRecorder.delete(this,name)?"تم حذف التسجيل":"تعذر حذف التسجيل")).show();
        }).show();
    }
    private void renameRecording(String oldName){
        EditText input=new EditText(this);input.setSingleLine(true);
        input.setText(oldName.endsWith(".json")?oldName.substring(0,oldName.length()-5):oldName);
        new AlertDialog.Builder(this).setTitle("إعادة تسمية التسجيل").setView(input).setNegativeButton("إلغاء",null)
            .setPositiveButton("حفظ",(d,w)->toast(TouchRecorder.rename(this,oldName,input.getText().toString())?"تم تغيير الاسم":"تعذر تغيير الاسم؛ الاسم مستخدم أو غير صالح")).show();
    }

    private void startAutomation(){
        if(!GameControlService.isReady()){autoText.setText("فعّل خدمة إمكانية الوصول أولاً");return;}
        float aspect=screenWidth()/(float)Math.max(1,screenHeight());
        if(aspect<1.75f){autoText.setText("أدر الهاتف إلى الوضع الأفقي قبل تشغيل القيادة.");return;}
        if(autoRunning)return;
        AgentConfig.controlsCalibrated=true;
        if(CaptureService.isActive()){
            AgentConfig.autoEnabled=true;autoRunning=true;
            autoText.setText("القيادة التكيفية نشطة: تتبع الهدف وتغيّر الحركة والضرب والركض حسب الرصد.");
            return;
        }
        pendingAutoStart=true;
        MediaProjectionManager manager=(MediaProjectionManager)getSystemService(MEDIA_PROJECTION_SERVICE);
        startActivityForResult(manager.createScreenCaptureIntent(),REQUEST_CAPTURE);
        autoText.setText("بانتظار موافقتك على مشاركة الشاشة لبدء اللعب التلقائي.");
    }
    private void stopAutomation(String reason){
        autoRunning=false;pendingAutoStart=false;AgentConfig.autoEnabled=false;
        GameControlService.stopContinuous();if(autoText!=null)autoText.setText(reason);
    }
    private void updateControlStatus(){controlText.setText(GameControlService.isReady()?"التحكم: مفعّل وجاهز":"التحكم: غير مفعّل — افتح إعدادات إمكانية الوصول");}
    private int screenWidth(){return getResources().getDisplayMetrics().widthPixels;}
    private int screenHeight(){return getResources().getDisplayMetrics().heightPixels;}
    private TextView section(String s){TextView v=label(s,18,CYAN,true);v.setPadding(0,dp(20),0,dp(8));return v;}
    private TextView card(String s){TextView v=label(s,14,WHITE,false);v.setPadding(dp(14),dp(14),dp(14),dp(14));v.setBackground(round(PANEL,dp(14)));return v;}
    private TextView label(String s,int size,int color,boolean bold){TextView v=new TextView(this);v.setText(s);v.setTextSize(size);v.setTextColor(color);if(bold)v.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return v;}
    private Button addButton(LinearLayout page,String text,Runnable action,boolean primary){
        Button b=new Button(this);b.setText(text);b.setAllCaps(false);b.setTextSize(14);b.setTextColor(primary?0xFF06151F:WHITE);
        b.setBackground(round(primary?CYAN:PANEL2,dp(12)));b.setOnClickListener(v->action.run());
        LinearLayout.LayoutParams p=match();p.height=dp(52);page.addView(b,p);return b;
    }
    private GradientDrawable round(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(radius);if(color==PANEL2)d.setStroke(dp(1),0xFF29445D);return d;}
    private LinearLayout.LayoutParams match(){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(8);return p;}
    private int dp(int n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_SHORT).show();}

    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);if(request!=REQUEST_CAPTURE)return;
        if(result==RESULT_OK&&data!=null){
            Intent service=new Intent(this,CaptureService.class);service.putExtra(CaptureService.EXTRA_RESULT_CODE,result);service.putExtra(CaptureService.EXTRA_RESULT_DATA,data);
            if(Build.VERSION.SDK_INT>=26)startForegroundService(service);else startService(service);
            if(Build.VERSION.SDK_INT<23 || Settings.canDrawOverlays(this)) showOverlay();
            if(pendingAutoStart){AgentConfig.autoEnabled=true;autoRunning=true;pendingAutoStart=false;if(autoText!=null)autoText.setText("القيادة التكيفية نشطة — انتقل إلى اللعبة، وسيستمر الرادار والتحكم بالخلفية.");}
            statusText.setText("تحليل الشاشة: تم منح الإذن، بانتظار أول إطار...");
        }else{pendingAutoStart=false;statusText.setText("لم تتم الموافقة على التقاط الشاشة");if(autoText!=null)autoText.setText("تم إلغاء تشغيل اللعب التلقائي.");}
    }
}
