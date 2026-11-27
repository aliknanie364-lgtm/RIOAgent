package com.rioagent.app;

import android.graphics.Bitmap;
import java.util.ArrayList;
import java.util.List;

public final class RedMarkerDetector {
    public static final class Marker {
        public final float x, y;
        public final int area, left, top, right, bottom;
        Marker(float x,float y,int area,int left,int top,int right,int bottom) {
            this.x=x; this.y=y; this.area=area; this.left=left; this.top=top; this.right=right; this.bottom=bottom;
        }
    }
    public List<Marker> detect(Bitmap bitmap) {
        List<Marker> result = new ArrayList<>();
        if (bitmap == null || bitmap.isRecycled()) return result;
        int w=bitmap.getWidth(), h=bitmap.getHeight();
        if (w<2 || h<2 || (long)w*h>1_000_000L) return result;
        int[] pixels=new int[w*h]; bitmap.getPixels(pixels,0,w,0,0,w,h);
        boolean[] seen=new boolean[pixels.length]; int[] queue=new int[pixels.length];
        int x0=Math.max(0,(int)(w*AgentConfig.roiLeft)), y0=Math.max(0,(int)(h*AgentConfig.roiTop));
        int x1=Math.min(w,(int)(w*AgentConfig.roiRight)), y1=Math.min(h,(int)(h*AgentConfig.roiBottom));
        int minArea=Math.max(3,w*h/120000), maxArea=Math.max(minArea+1,w*h/5);
        for(int y=y0;y<y1;y++) for(int x=x0;x<x1;x++) {
            int start=y*w+x; if(seen[start] || !isRed(pixels[start])) continue;
            int head=0,tail=0; queue[tail++]=start; seen[start]=true;
            long sx=0,sy=0; int l=x,r=x,t=y,b=y;
            while(head<tail) {
                int index=queue[head++], px=index%w, py=index/w; sx+=px; sy+=py;
                l=Math.min(l,px); r=Math.max(r,px); t=Math.min(t,py); b=Math.max(b,py);
                if(px>x0) tail=add(index-1,pixels,seen,queue,tail);
                if(px+1<x1) tail=add(index+1,pixels,seen,queue,tail);
                if(py>y0) tail=add(index-w,pixels,seen,queue,tail);
                if(py+1<y1) tail=add(index+w,pixels,seen,queue,tail);
            }
            int bw=r-l+1,bh=b-t+1;
            // Enemy health bars in RIO RISE are compact horizontal red components; ignore isolated red UI dots.
            float aspect=bw/(float)Math.max(1,bh);
            if(tail>=minArea && tail<=maxArea && bw>=5 && aspect>=1.45f && bh<=Math.max(12,h/18))
                result.add(new Marker((sx/(float)tail)/Math.max(1,w-1),(sy/(float)tail)/Math.max(1,h-1),tail,l,t,r,b));
        }
        return result;
    }
    private int add(int i,int[] p,boolean[] s,int[] q,int tail) {
        if(!s[i] && isRed(p[i])) { s[i]=true; q[tail++]=i; } return tail;
    }
    private boolean isRed(int c) {
        int r=(c>>>16)&255,g=(c>>>8)&255,b=c&255;
        return r>=AgentConfig.redMin && g<=AgentConfig.greenMax && b<=AgentConfig.blueMax && r>g*1.35f && r>b*1.25f;
    }
}
