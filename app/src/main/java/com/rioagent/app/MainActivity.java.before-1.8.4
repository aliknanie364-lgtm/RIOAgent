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
import android.os.Handler;
import android.os.Looper;
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
    private EditText moveX,moveY,moveToX,moveToY,punchX,punchY,jumpX,jumpY;
    private final Handler automationHandler=new Handler(Looper.getMainLooper());
    private boolean receiverRegistered=false,autoRunning=false;
    private int autoCycles=0;

    private final BroadcastReceiver captureReceiver=new BroadcastReceiver(){
        @Override public void onReceive(Context c,Intent i){
            if(i==null||!CaptureService.ACTION_UPDATE.equals(i.getAction()))return;
            String status=i.getStringExtra("status"),analysis=i.getStringExtra("analysis");
            if(statusText!=null)statusText.setText("تحليل الشاشة: "+(status==null?"":status)+(analysis==null||analysis.isEmpty()?"":"\n"+analysis));
        }
    };
    private final Runnable autoStep=new Runnable(){
        @Override public void run(){
            if(!autoRunning)return;
            if(!GameControlService.isReady()){stopAutomation("خدمة التحكم غير متاحة");return;}
            try{
                float[] a=point(moveX,moveY),b=point(moveToX,moveToY),p=point(punchX,punchY),j=point(jumpX,jumpY);
                GameControlService.swipe(a[0],a[1],b[0],b[1],650);
                automationHandler.postDelayed(()->{
                    if(!autoRunning)return;
                    GameControlService.tap(p[0],p[1],65);
                    automationHandler.postDelayed(()->{
                        if(!autoRunning)return;
                        GameControlService.tap(j[0],j[1],65);autoCycles++;
                        if(autoText!=null)autoText.setText("التسلسل يعمل • الدورات: "+autoCycles);
                        automationHandler.postDelayed(this,450);
                    },180);
                },180);
            }catch(Exception e){stopAutomation("إعداد غير صالح: "+e.getMessage());}
        }
    };

    @Override public void onCreate(Bundle b){super.onCreate(b);buildUi();}
    @Override protected void onStart(){
        super.onStart();
        if(!receiverRegistered){
            IntentFilter f=new IntentFilter(CaptureService.ACTION_UPDATE);
            if(Build.VERSION.SDK_INT>=33)registerReceiver(captureReceiver,f,Context.RECEIVER_NOT_EXPORTED);else registerReceiver(captureReceiver,f);
            receiverRegistered=true;
        }
    }
    @Override protected void onStop(){
        stopAutomation("الأتمتة متوقفة");
        if(receiverRegistered){unregisterReceiver(captureReceiver);receiverRegistered=false;}
        super.onStop();
    }
    @Override protected void onResume(){
        super.onResume();
        if(controlText!=null){
            updateControlStatus();
            automationHandler.postDelayed(this::updateControlStatus, 700);
        }
    }
    @Override public void onBackPressed(){stopAutomation("تم إيقاف الأتمتة");super.onBackPressed();}

    private void buildUi(){
        LinearLayout page=new LinearLayout(this);page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(18),dp(18),dp(18),dp(26));page.setBackgroundColor(BG);
        TextView brand=label("RIO",34,WHITE,true);brand.setGravity(Gravity.CENTER);page.addView(brand,match());
        TextView sub=label("AGENT  •  TOUCH AUTOMATION",11,CYAN,true);sub.setGravity(Gravity.CENTER);page.addView(sub,match());
        TextView tagline=label("تحكّم. سجّل. أعد التشغيل.",19,WHITE,true);tagline.setGravity(Gravity.CENTER);tagline.setPadding(0,dp(10),0,dp(14));page.addView(tagline,match());

        page.addView(section("حالة النظام"));
        statusText=card("تحليل الشاشة: متوقف");controlText=card("التحكم: جارٍ التحقق من الصلاحية");
        page.addView(statusText,match());page.addView(controlText,match());
        addButton(page,"تفعيل صلاحية التحكم",()->startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)),true);

        page.addView(section("مسجّل الحركات"));
        recorderStatus=card("جاهز للتسجيل. فعّل خدمة إمكانية الوصول أولاً.");page.addView(recorderStatus,match());
        addButton(page,"●  بدء تسجيل حركاتي",this::startRecording,true);
        addButton(page,"■  إيقاف التسجيل وحفظه",()->{
            String saved=TouchRecorder.stopAndSave(this);
            recorderStatus.setText(saved.isEmpty()?"لم تُحفظ حركات. تحقق من وصول أحداث اللمس أثناء التسجيل.":"تم حفظ التسجيل: "+saved);
        },false);
        addButton(page,"مكتبة التسجيلات وإدارتها",this::showRecordings,false);
        addButton(page,"تشغيل آخر تسجيل",()->{
            if(!GameControlService.isReady()){toast("فعّل خدمة التحكم أولاً");return;}
            boolean ok=TouchRecorder.playLatest(this,screenWidth(),screenHeight());
            recorderStatus.setText(ok?"تم إرسال التسجيل الأخير للتشغيل.":"تعذر التشغيل؛ تحقق من وجود تسجيل محفوظ وخدمة التحكم.");
        },false);

        page.addView(section("تحليل الشاشة"));
        addButton(page,"بدء تحليل الشاشة",()->{
            MediaProjectionManager m=(MediaProjectionManager)getSystemService(MEDIA_PROJECTION_SERVICE);
            startActivityForResult(m.createScreenCaptureIntent(),REQUEST_CAPTURE);
        },false);
        addButton(page,"إيقاف تحليل الشاشة",()->{
            stopService(new Intent(this,CaptureService.class));statusText.setText("تحليل الشاشة: تم طلب الإيقاف");
        },false);

        page.addView(section("اختبار التحكم"));
        addButton(page,"نقرة تجريبية بمنتصف الشاشة",()->{
            boolean ok=GameControlService.tap(screenWidth()/2f,screenHeight()/2f,90);
            statusText.setText(ok?"تم إرسال النقرة التجريبية":"تعذر إرسال النقرة؛ تحقق من الصلاحية");
        },false);
        addButton(page,"سحب تجريبي",()->{
            boolean ok=GameControlService.swipe(screenWidth()*.25f,screenHeight()*.7f,screenWidth()*.75f,screenHeight()*.7f,350);
            statusText.setText(ok?"تم إرسال السحب التجريبي":"تعذر إرسال السحب؛ تحقق من الصلاحية");
        },false);

        page.addView(section("التسلسل التلقائي"));
        page.addView(card("الإحداثيات كنسبة مئوية من الشاشة (0–100). اضبطها حسب مواضع أزرار لعبتك."),match());
        moveX=addNumber(page,"مركز عصا الحركة X","10");moveY=addNumber(page,"مركز عصا الحركة Y","82");
        moveToX=addNumber(page,"اتجاه الحركة X","18");moveToY=addNumber(page,"اتجاه الحركة Y","82");
        punchX=addNumber(page,"زر الضرب X","84");punchY=addNumber(page,"زر الضرب Y","65");
        jumpX=addNumber(page,"زر القفز X","91");jumpY=addNumber(page,"زر القفز Y","78");
        autoText=card("الأتمتة متوقفة");page.addView(autoText,match());
        addButton(page,"تشغيل التسلسل التلقائي",this::startAutomation,true);
        addButton(page,"إيقاف فوري للطوارئ",()->stopAutomation("تم الإيقاف يدوياً"),false);

        TextView note=label("ملاحظة: دقة التسجيل تعتمد على إصدار أندرويد والشركة المصنّعة وإمكانية وصول أحداث اللمس. قد لا تُلتقط بعض الإيماءات أو اللمسات المتعددة.",12,MUTED,false);
        note.setPadding(dp(4),dp(12),dp(4),0);page.addView(note,match());
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.addView(page);setContentView(scroll);updateControlStatus();
    }

    private void startRecording(){
        recorderStatus.setText("تم إيقاف التسجيل مؤقتاً في إصدار Safe Touch لحماية استجابة شاشة الهاتف. ستعود الميزة بعد اعتماد طريقة تسجيل آمنة.");
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

    private EditText addNumber(LinearLayout page,String title,String value){
        page.addView(label(title,13,MUTED,true),match());
        EditText field=new EditText(this);field.setSingleLine(true);
        field.setInputType(android.text.InputType.TYPE_CLASS_NUMBER|android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        field.setText(value);field.setTextColor(WHITE);field.setTextSize(16);field.setPadding(dp(12),0,dp(12),0);
        field.setBackground(round(PANEL2,dp(10)));LinearLayout.LayoutParams p=match();p.height=dp(48);page.addView(field,p);return field;
    }
    private float[] point(EditText x,EditText y){
        float a=Float.parseFloat(x.getText().toString().trim()),b=Float.parseFloat(y.getText().toString().trim());
        if(a<0||a>100||b<0||b>100)throw new IllegalArgumentException("القيم يجب أن تكون بين 0 و100");
        return new float[]{screenWidth()*a/100f,screenHeight()*b/100f};
    }
    private void startAutomation(){
        if(!GameControlService.isReady()){autoText.setText("فعّل خدمة إمكانية الوصول أولاً");return;}
        if(autoRunning)return;
        try{point(moveX,moveY);point(moveToX,moveToY);point(punchX,punchY);point(jumpX,jumpY);
            autoRunning=true;autoCycles=0;autoText.setText("بدأ التسلسل — راقب الشاشة واضغط إيقاف للطوارئ");automationHandler.post(autoStep);
        }catch(Exception e){autoText.setText("تحقق من الإحداثيات: "+e.getMessage());}
    }
    private void stopAutomation(String reason){autoRunning=false;automationHandler.removeCallbacksAndMessages(null);if(autoText!=null)autoText.setText(reason);}
    private void updateControlStatus(){
        if(GameControlService.isReady()){
            controlText.setText("التحكم: الخدمة متصلة وجاهزة");
        }else if(GameControlService.isAccessibilityEnabled(this)){
            controlText.setText("الصلاحية مفعّلة في Android، لكن الخدمة لم تتصل بعد. انتظر قليلاً أو أعد فتح التطبيق.");
        }else{
            controlText.setText("التحكم: صلاحية إمكانية الوصول غير مفعّلة");
        }
    }
    private int screenWidth(){return getResources().getDisplayMetrics().widthPixels;}
    private int screenHeight(){return getResources().getDisplayMetrics().heightPixels;}
    private TextView section(String s){TextView v=label(s,18,CYAN,true);v.setPadding(0,dp(20),0,dp(8));return v;}
    private TextView card(String s){TextView v=label(s,14,WHITE,false);v.setPadding(dp(14),dp(14),dp(14),dp(14));v.setBackground(round(PANEL,dp(14)));return v;}
    private TextView label(String s,int size,int color,boolean bold){TextView v=new TextView(this);v.setText(s);v.setTextSize(size);v.setTextColor(color);if(bold)v.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return v;}
    private void addButton(LinearLayout page,String text,Runnable action,boolean primary){
        Button b=new Button(this);b.setText(text);b.setAllCaps(false);b.setTextSize(14);b.setTextColor(primary?0xFF06151F:WHITE);
        b.setBackground(round(primary?CYAN:PANEL2,dp(12)));b.setOnClickListener(v->action.run());
        LinearLayout.LayoutParams p=match();p.height=dp(52);page.addView(b,p);
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
            statusText.setText("تحليل الشاشة: تم منح الإذن، بانتظار أول إطار...");
        }else statusText.setText("لم تتم الموافقة على التقاط الشاشة");
    }
}
