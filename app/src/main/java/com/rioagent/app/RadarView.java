package com.rioagent.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Color;
import android.util.AttributeSet;
import android.view.View;

/** Lightweight live radar preview; coordinates are normalized to the captured frame. */
public final class RadarView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private volatile float[] points = new float[0];
    private volatile float steering;
    public RadarView(Context c) { super(c); init(); }
    public RadarView(Context c, AttributeSet a) { super(c,a); init(); }
    private void init() { setLayerType(View.LAYER_TYPE_SOFTWARE,null); }
    public void setTargets(float[] xy,float steer) { points=xy==null?new float[0]:xy.clone(); steering=Math.max(-1f,Math.min(1f,steer)); postInvalidate(); }
    @Override protected void onDraw(Canvas c) {
        super.onDraw(c); float w=getWidth(),h=getHeight(),cx=w/2f,cy=h/2f,r=Math.max(1f,Math.min(w,h)*.43f);
        paint.setStyle(Paint.Style.FILL);paint.setColor(0xff07131f);c.drawRoundRect(0,0,w,h,dp(12),dp(12),paint);
        paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(1));paint.setColor(0xff245064);
        c.drawCircle(cx,cy,r,paint);c.drawCircle(cx,cy,r*.66f,paint);c.drawCircle(cx,cy,r*.33f,paint);
        c.drawLine(cx-r,cy,cx+r,cy,paint);c.drawLine(cx,cy-r,cx,cy+r,paint);
        paint.setStyle(Paint.Style.FILL);paint.setColor(0xff32d6e5);c.drawCircle(cx,cy,dp(4),paint);
        float[] xy=points;paint.setColor(Color.RED);for(int i=0;i+1<xy.length;i+=2){float x=cx+(xy[i]-.5f)*2*r,y=cy+(xy[i+1]-.5f)*2*r;c.drawCircle(x,y,dp(4),paint);}
        paint.setColor(0xff32d6e5);paint.setStrokeWidth(dp(2));paint.setStyle(Paint.Style.STROKE);float angle=(float)(-Math.PI/2+steering*.85);c.drawLine(cx,cy,cx+(float)Math.cos(angle)*r*.8f,cy+(float)Math.sin(angle)*r*.8f,paint);
        paint.setStyle(Paint.Style.FILL);paint.setTextSize(dp(9));paint.setColor(0xffa8c3d2);c.drawText("RADAR  •  PREVIEW",dp(8),dp(14),paint);
    }
    private float dp(float v){return v*getResources().getDisplayMetrics().density;}
}
