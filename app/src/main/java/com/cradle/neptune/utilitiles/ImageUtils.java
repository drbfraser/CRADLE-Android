package com.cradle.neptune.utilitiles;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;

public class ImageUtils {


    public static Bitmap extractBitmapRegion(Bitmap source, int left, int top, int right, int bottom) {
        int width = right - left;
        int height = bottom - top;
        return Bitmap.createBitmap(source, left, top, width, height);
    }


    public static void drawRectInBitmap(Bitmap bitmap, float x1, float y1, float x2, float y2, int color) {
        Canvas c = new Canvas(bitmap);
        Paint p = new Paint();
        p.setColor(color);
        p.setStrokeWidth(6);
        p.setStyle(Paint.Style.STROKE);
        c.drawRect(x1, y1, x2, y2, p);
    }

}
