package com.cradletrial.cradlevhtapp.ocr;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.RectF;
import android.util.Log;

import com.cradletrial.cradlevhtapp.utilitiles.ImageUtils;
import com.cradletrial.cradlevhtapp.utilitiles.Util;

/**
 * Supports extracting regions from an image.
 * - source image may be from camera (with extra parts)
 * - source image may be just cropped screen (OVERLAY_REGION_SCREEN)
 */
public class CradleOverlay {
    private static final String TAG = "CradleOverlay";
    public enum OverlayRegion {
        OVERLAY_REGION_SCREEN,
        OVERLAY_REGION_SYS,
        OVERLAY_REGION_DIA,
        OVERLAY_REGION_HR,
    }

    // from photoshop
    private static final int RAW_OVERLAY_WIDTH_px = 800;
    private static final int RAW_OVERLAY_HEIGHT_px = 1305;
    private static final int RAW_OVERLAY_MARGIN_TOP = 243;
    private static final int RAW_OVERLAY_MARGIN_LEFT = 216;
    private static final int RAW_OVERLAY_MARGIN_RIGHT = 217;
    private static final int RAW_OVERLAY_MARGIN_BOTTOM = 566;
    private static final int RAW_OVERLAY_SYS_HEIGHT = 174;
    private static final int RAW_OVERLAY_DIA_HEIGHT = 168;
    private static final int RAW_OVERLAY_HR_HEIGHT = 160;


    private static final int OVERLAY_OUTSIDE_ERROR_DELTA =
            (int) (RAW_OVERLAY_WIDTH_px * 0.05F);
    private static final int OVERLAY_INSIDE_ERROR_DELTA =
            (int) (RAW_OVERLAY_WIDTH_px * 0.05F);

    // calculate break points relative to full image
    private static final int OVERLAY_X_LEFT_px
            = RAW_OVERLAY_MARGIN_LEFT - OVERLAY_OUTSIDE_ERROR_DELTA;
    private static final int OVERLAY_X_RIGHT_px
            = RAW_OVERLAY_WIDTH_px - RAW_OVERLAY_MARGIN_RIGHT  + OVERLAY_OUTSIDE_ERROR_DELTA;
    private static final int OVERLAY_Y_TOP_px
            = RAW_OVERLAY_MARGIN_TOP  - OVERLAY_OUTSIDE_ERROR_DELTA;
    private static final int OVERLAY_Y_BOTTOM_px
            = RAW_OVERLAY_HEIGHT_px - RAW_OVERLAY_MARGIN_BOTTOM  + OVERLAY_OUTSIDE_ERROR_DELTA;

    private static final int OVERLAY_SCREEN_WIDTH
            = OVERLAY_X_RIGHT_px - OVERLAY_X_LEFT_px;
    private static final int OVERLAY_X_DIGITS_LEFT_px
//            = OVERLAY_X_LEFT_px + (OVERLAY_SCREEN_WIDTH / 4);
            = OVERLAY_X_LEFT_px;

    private static final int OVERLAY_Y_SYS_TOP_px
            = OVERLAY_Y_TOP_px;
    private static final int OVERLAY_Y_SYS_BOTTOM_px
            = RAW_OVERLAY_MARGIN_TOP + RAW_OVERLAY_SYS_HEIGHT + OVERLAY_INSIDE_ERROR_DELTA;
    private static final int OVERLAY_Y_DIA_TOP_px
            = RAW_OVERLAY_MARGIN_TOP + RAW_OVERLAY_SYS_HEIGHT - OVERLAY_INSIDE_ERROR_DELTA;
    private static final int OVERLAY_Y_DIA_BOTTOM_px
            = RAW_OVERLAY_MARGIN_TOP + RAW_OVERLAY_SYS_HEIGHT + RAW_OVERLAY_DIA_HEIGHT + OVERLAY_INSIDE_ERROR_DELTA;
    private static final int OVERLAY_Y_HR_TOP_px
            = RAW_OVERLAY_MARGIN_TOP + RAW_OVERLAY_SYS_HEIGHT + RAW_OVERLAY_DIA_HEIGHT - OVERLAY_INSIDE_ERROR_DELTA;
    private static final int OVERLAY_Y_HR_BOTTOM_px
            = OVERLAY_Y_BOTTOM_px;




    public static Bitmap extractBitmapRegionFromCameraImage(Bitmap source, OverlayRegion region) {
        RectF rect = getRegionCoordsRelativeToFullImage(region);
        RectF scaledRect = getScaledCoordsToBitmapRelativeToFullImage(source, rect);
        return extractBitmapToRect(source, scaledRect);
    }
    public static Bitmap extractBitmapRegionFromScreenImage(Bitmap source, OverlayRegion region) {
        RectF rect = getRegionCoordsRelativeToScreen(region);
        RectF scaledRect = getScaledCoordsToBitmapRelativeToScreen(source, rect);
        return extractBitmapToRect(source, scaledRect);

    }

    private static RectF getRegionCoordsRelativeToFullImage(OverlayRegion region) {
        switch (region) {
            case OVERLAY_REGION_SCREEN:
                return new RectF(OVERLAY_X_LEFT_px, OVERLAY_Y_TOP_px, OVERLAY_X_RIGHT_px, OVERLAY_Y_BOTTOM_px);
            case OVERLAY_REGION_SYS:
                return new RectF(OVERLAY_X_DIGITS_LEFT_px, OVERLAY_Y_SYS_TOP_px, OVERLAY_X_RIGHT_px, OVERLAY_Y_SYS_BOTTOM_px);
            case OVERLAY_REGION_DIA:
                return new RectF(OVERLAY_X_DIGITS_LEFT_px, OVERLAY_Y_DIA_TOP_px, OVERLAY_X_RIGHT_px, OVERLAY_Y_DIA_BOTTOM_px);
            case OVERLAY_REGION_HR:
                return new RectF(OVERLAY_X_DIGITS_LEFT_px, OVERLAY_Y_HR_TOP_px, OVERLAY_X_RIGHT_px, OVERLAY_Y_HR_BOTTOM_px);
            default:
                Util.ensure(false);
                return null;
        }
    }

    private static RectF getRegionCoordsRelativeToScreen(OverlayRegion region) {
        RectF rect = getRegionCoordsRelativeToFullImage(region);
        // translate the coords by the amount that was trimmed
        // so when scaled by original overlay width, we scale correctly.
        rect.left -= OVERLAY_X_LEFT_px;
        rect.right -= OVERLAY_X_LEFT_px;

        // top & bottom just slide up by amount trimmed from overlay top
        rect.top -= OVERLAY_Y_TOP_px;
        rect.bottom -= OVERLAY_Y_TOP_px;

        return rect;
    }


    private static RectF getScaledCoordsToBitmapRelativeToFullImage(Bitmap bmp, RectF rectSource) {
        RectF rect = new RectF(rectSource);

        // compute the photo coordinates
        float scale = (float)(bmp.getWidth()) / RAW_OVERLAY_WIDTH_px;
        rect.left *= scale;
        rect.top *= scale;
        rect.right *= scale;
        rect.bottom *= scale;

        return rect;
    }

    private static RectF getScaledCoordsToBitmapRelativeToScreen(Bitmap bmp, RectF rectSource) {
        RectF rect = new RectF(rectSource);

        // compute the photo coordinates
        float scale = (float)(bmp.getWidth()) / OVERLAY_SCREEN_WIDTH;
        rect.left *= scale;
        rect.top *= scale;
        rect.right *= scale;
        rect.bottom *= scale;

        return rect;
    }

    private static Bitmap extractBitmapToRect(Bitmap bmp, RectF rect) {
        Log.d(TAG, String.format("Extracting region p1: %.1f,%.1f;  p2:%.1f/%d, %.1f/%d   (/width and /height)",
                rect.left, rect.top, rect.right, bmp.getWidth(), rect.bottom, bmp.getHeight()));

        // extract region, managing bounds on image
        return ImageUtils.extractBitmapRegion(bmp,
                Math.max(Math.round(rect.left), 0),
                Math.max(Math.round(rect.top), 0),
                Math.min(Math.round(rect.right), bmp.getWidth()),
                Math.min(Math.round(rect.bottom), bmp.getHeight()));
    }


//    private static Bitmap extractBitmapRegionRelativeToOverlay(
//            Bitmap source, int ovlLeft, int ovlTop, int ovlRight, int ovlBottom)
//    {
//        // compute the photo coordinates
//        float scale = (float)(source.getWidth()) / RAW_OVERLAY_WIDTH_px;
//        int photoLeft = (int) (ovlLeft * scale);
//        int photoTop = (int) (ovlTop * scale);
//        int photoRight = (int) (ovlRight * scale);
//        int photoBottom = (int) (ovlBottom * scale);
//
//        // extract
//        return ImageUtils.extractBitmapRegion(source, photoLeft, photoTop, photoRight, photoBottom);
//    }




    // TODO: TESTING ONLY
    private interface ScaleI { int apply(int x);}
    private Bitmap drawBreakpointsOnImage(Bitmap source) {

        Bitmap bmp = Bitmap.createBitmap(source);

        // compute the photo coordinates
        float scaleFactor = (float)(source.getWidth()) / RAW_OVERLAY_WIDTH_px;
        ScaleI s = x -> (int) (x * scaleFactor);

        // screen
        ImageUtils.drawRectInBitmap(bmp, s.apply(OVERLAY_X_LEFT_px), s.apply(OVERLAY_Y_TOP_px), s.apply(OVERLAY_X_RIGHT_px), s.apply(OVERLAY_Y_BOTTOM_px), Color.argb(0, 255, 255, 0));

        // sys/dia/hr
        ImageUtils.drawRectInBitmap(bmp, s.apply(OVERLAY_X_LEFT_px), s.apply(OVERLAY_Y_SYS_TOP_px), s.apply(OVERLAY_X_RIGHT_px), s.apply(OVERLAY_Y_SYS_BOTTOM_px), Color.RED);
        ImageUtils.drawRectInBitmap(bmp, s.apply(OVERLAY_X_LEFT_px), s.apply(OVERLAY_Y_DIA_TOP_px), s.apply(OVERLAY_X_RIGHT_px), s.apply(OVERLAY_Y_DIA_BOTTOM_px), Color.YELLOW);
        ImageUtils.drawRectInBitmap(bmp, s.apply(OVERLAY_X_LEFT_px), s.apply(OVERLAY_Y_HR_TOP_px), s.apply(OVERLAY_X_RIGHT_px), s.apply(OVERLAY_Y_HR_BOTTOM_px), Color.GREEN);

        return bmp;
    }


}
