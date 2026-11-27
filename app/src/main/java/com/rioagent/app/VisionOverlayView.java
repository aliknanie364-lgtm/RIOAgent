package com.rioagent.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

/** Transparent visualization of actual detections from the current detector. */
public final class VisionOverlayView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private volatile float[] boxes = new float[0];

    public VisionOverlayView(Context context) {
        super(context);
        setLayerType(View.LAYER_TYPE_HARDWARE, null);
    }

    public void setTargets(float[] values) {
        boxes = values == null ? new float[0] : values.clone();
        postInvalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;
        float[] data = boxes;
        int count = data.length / 4;

        paint.setStyle(Paint.Style.FILL);
        paint.setTextSize(dp(12));
        paint.setFakeBoldText(true);
        paint.setColor(0xff32e6d0);
        canvas.drawText("RIO VISION | RED CONTACTS: " + count, dp(14), dp(24), paint);

        for (int i = 0; i < count; i++) {
            int p = i * 4;
            float left = clamp(data[p]) * w;
            float top = clamp(data[p + 1]) * h;
            float right = clamp(data[p + 2]) * w;
            float bottom = clamp(data[p + 3]) * h;
            if (right <= left || bottom <= top) continue;

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(2));
            paint.setColor(0xffff4655);
            canvas.drawRect(new RectF(left, top, right, bottom), paint);

            float cx = (left + right) * .5f;
            float cy = (top + bottom) * .5f;
            float arm = dp(7);
            canvas.drawLine(cx-arm, cy, cx+arm, cy, paint);
            canvas.drawLine(cx, cy-arm, cx, cy+arm, paint);

            float corner = Math.min(dp(12), Math.min(right-left, bottom-top) * .3f);
            paint.setStrokeWidth(dp(3));
            canvas.drawLine(left, top, left+corner, top, paint);
            canvas.drawLine(left, top, left, top+corner, paint);
            canvas.drawLine(right-corner, top, right, top, paint);
            canvas.drawLine(right, top, right, top+corner, paint);
            canvas.drawLine(left, bottom-corner, left, bottom, paint);
            canvas.drawLine(left, bottom, left+corner, bottom, paint);
            canvas.drawLine(right-corner, bottom, right, bottom, paint);
            canvas.drawLine(right, bottom-corner, right, bottom, paint);

            paint.setStyle(Paint.Style.FILL);
            paint.setTextSize(dp(11));
            String label = "RED TARGET " + (i + 1);
            float tw = paint.measureText(label);
            float labelTop = Math.max(dp(30), top-dp(20));
            paint.setColor(0xdd101923);
            canvas.drawRect(left, labelTop, left+tw+dp(10), labelTop+dp(18), paint);
            paint.setColor(0xffff7b84);
            canvas.drawText(label, left+dp(5), labelTop+dp(13), paint);
        }
    }

    private float clamp(float value) { return Math.max(0f, Math.min(1f, value)); }
    private float dp(float value) { return value * getResources().getDisplayMetrics().density; }
}
