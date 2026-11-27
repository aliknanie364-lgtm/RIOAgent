package com.rioagent.app;

import android.graphics.Bitmap;
import java.util.ArrayList;
import java.util.List;

public final class RedMarkerDetector {
    public static final class Marker {
        public final float x, y;
        public final int area;
        public final int left, top, right, bottom;

        Marker(float x, float y, int area,
               int left, int top, int right, int bottom) {
            this.x = x;
            this.y = y;
            this.area = area;
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
        }
    }

    public List<Marker> detect(Bitmap bitmap) {
        List<Marker> result = new ArrayList<>();
        if (bitmap == null || bitmap.isRecycled()) return result;

        int w = bitmap.getWidth(), h = bitmap.getHeight();
        if (w < 2 || h < 2) return result;

        int[] pixels = new int[w * h];
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h);
        boolean[] seen = new boolean[pixels.length];
        int[] queue = new int[pixels.length];

        int x0 = Math.max(0, (int)(w * AgentConfig.roiLeft));
        int y0 = Math.max(0, (int)(h * AgentConfig.roiTop));
        int x1 = Math.min(w, (int)(w * AgentConfig.roiRight));
        int y1 = Math.min(h, (int)(h * AgentConfig.roiBottom));

        int minArea = Math.max(3, (w * h) / 120000);
        int maxArea = Math.max(minArea + 1, (w * h) / 5);

        for (int y = y0; y < y1; y++) {
            for (int x = x0; x < x1; x++) {
                int start = y * w + x;
                if (seen[start] || !isRed(pixels[start])) continue;

                int head = 0, tail = 0;
                queue[tail++] = start;
                seen[start] = true;

                long sumX = 0, sumY = 0;
                int left = x, right = x, top = y, bottom = y;

                while (head < tail) {
                    int index = queue[head++];
                    int px = index % w, py = index / w;
                    sumX += px;
                    sumY += py;
                    left = Math.min(left, px);
                    right = Math.max(right, px);
                    top = Math.min(top, py);
                    bottom = Math.max(bottom, py);

                    if (px > x0) tail = add(index - 1, pixels, seen, queue, tail);
                    if (px + 1 < x1) tail = add(index + 1, pixels, seen, queue, tail);
                    if (py > y0) tail = add(index - w, pixels, seen, queue, tail);
                    if (py + 1 < y1) tail = add(index + w, pixels, seen, queue, tail);
                }

                int area = tail;
                if (area < minArea || area > maxArea) continue;

                float cx = (sumX / (float)area) / Math.max(1, w - 1);
                float cy = (sumY / (float)area) / Math.max(1, h - 1);
                result.add(new Marker(cx, cy, area, left, top, right, bottom));
            }
        }
        return result;
    }

    private int add(int index, int[] pixels, boolean[] seen,
                    int[] queue, int tail) {
        if (!seen[index] && isRed(pixels[index])) {
            seen[index] = true;
            queue[tail++] = index;
        }
        return tail;
    }

    private boolean isRed(int color) {
        int r = (color >>> 16) & 255;
        int g = (color >>> 8) & 255;
        int b = color & 255;
        return r >= AgentConfig.redMin
                && g <= AgentConfig.greenMax
                && b <= AgentConfig.blueMax
                && r > g * 1.35f
                && r > b * 1.25f;
    }
}
