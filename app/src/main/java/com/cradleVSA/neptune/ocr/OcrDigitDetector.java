package com.cradleVSA.neptune.ocr;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.os.Handler;
import android.os.SystemClock;
import android.util.Log;
import android.widget.Toast;

import com.cradleVSA.neptune.MyApp;
import com.cradleVSA.neptune.ocr.tflite.Classifier;
import com.cradleVSA.neptune.ocr.tflite.TFLiteObjectDetectionAPIModel;
import com.cradleVSA.neptune.utilitiles.TFImageUtils;
import com.wonderkiln.blurkit.BlurKit;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;

/**
 * Perform object detection (7-Seg Digits) on a background thread.
 * Offers functions to:
 * - put bounding boxes on an image overlay
 * - return the number found in the 7-seg display
 */
public class OcrDigitDetector {

    public static final float FILTER_RESULT_BY_CENTER_PERCENT = 0.15f;
    private static final String TAG = "OcrDigitDetector";
    // Configuration values for the SSD model.
    private static final int NN_INPUT_SIZE = 200; //300;
    private static final boolean TF_OD_API_IS_QUANTIZED = true; // false; // true
    private static final String TF_OD_API_MODEL_FILE = "seven_seg_ssd.tflite";
    private static final String TF_OD_API_LABELS_FILE = "file:///android_asset/seven_seg_labelmap.txt";
    // Minimum detection confidence to track a detection.
    private static final float MINIMUM_CONFIDENCE = 0.5f;
    private static final boolean MAINTAIN_ASPECT = true;        // false
    public static int g_blurRadiusREVISIT = 1;
    private Activity activity;
    private Classifier detector;
    // multi-threaded support:
    private Handler handler;
    // Resizing
    private Matrix frameToCropTransform;
    private Matrix cropToFrameTransform;


    public OcrDigitDetector(Activity activity, int inputImageWidth, int inputImageHeight) {
        this.activity = activity;
        try {
            detector =
                    TFLiteObjectDetectionAPIModel.create(
                            activity.getAssets(),
                            TF_OD_API_MODEL_FILE,
                            TF_OD_API_LABELS_FILE,
                            NN_INPUT_SIZE,
                            TF_OD_API_IS_QUANTIZED);
        } catch (final IOException e) {
            e.printStackTrace();
            Log.e(TAG, "Exception initializing classifier!", e);
            Toast toast =
                    Toast.makeText(
                            activity.getApplicationContext(), "Classifier could not be initialized", Toast.LENGTH_SHORT);
            toast.show();
            throw new RuntimeException(e);
        }

        // initialize conversions
        frameToCropTransform =
                TFImageUtils.getTransformationMatrix(
                        inputImageWidth, inputImageHeight,
                        NN_INPUT_SIZE, NN_INPUT_SIZE,
                        0,
                        MAINTAIN_ASPECT);

        cropToFrameTransform = new Matrix();
        frameToCropTransform.invert(cropToFrameTransform);
    }

    public void processImage(Bitmap image, OnProcessImageDone callback) {

        Bitmap resizedImage = Bitmap.createBitmap(NN_INPUT_SIZE, NN_INPUT_SIZE, Bitmap.Config.ARGB_8888);
        final Canvas canvas = new Canvas(resizedImage);
        canvas.drawBitmap(image, frameToCropTransform, null);

        // blur image before NN
        boolean disableBlur = ((MyApp) activity.getApplication()).isDisableBlurKit();
        if (g_blurRadiusREVISIT > 0 && !disableBlur) {
            BlurKit.getInstance().blur(resizedImage, g_blurRadiusREVISIT);
        }

//        activity.runInBackground(
//                new Runnable() {
//                    @Override
//                    public void run() {
        // object detection
        Log.i(TAG, "Running detection on image " + SystemClock.uptimeMillis());
        final long startTime = SystemClock.uptimeMillis();
        final List<Classifier.Recognition> results = detector.recognizeImage(resizedImage);
        long lastProcessingTimeMs = SystemClock.uptimeMillis() - startTime;
        Log.i(TAG, "   -> done recognizing in " + lastProcessingTimeMs + "mS.");

        // log results
        for (Classifier.Recognition result : results) {
            RectF location = result.getLocation();
            if (location != null) {
                Log.i(TAG, String.format("   %s @ %.2f%% -> %3.0f %3.0f %3.0f %3.0f",
                        result.getTitle(),
                        result.getConfidence(),
                        result.getLocation().left,
                        result.getLocation().top,
                        result.getLocation().right,
                        result.getLocation().bottom));
            }
        }


        // callbacks
        if (callback != null) {
            // notify: raw bitmap and results
            callback.notifyOfRawBoundingBoxes(resizedImage, results);

            // notify: map recognized co-ords back to real image.
            final List<Classifier.Recognition> mappedRecognitions = new LinkedList<>();
            for (Classifier.Recognition result : results) {
                RectF location = result.getLocation();
                if (location != null) { //&& result.getConfidence() >= MINIMUM_CONFIDENCE) {
                    cropToFrameTransform.mapRect(location);
                    result.setLocation(location);
                    mappedRecognitions.add(result);
                }
            }
            callback.notifyOfBoundingBoxes(mappedRecognitions);

            // notify: extracted text
            String extractedText = extractTextFromResults(results, image.getHeight());
            callback.notifyOfExtractedText(extractedText);
        }

        // Process results
//        cropCopyBitmap = Bitmap.createBitmap(resizedImage);
//        final Canvas canvas = new Canvas(cropCopyBitmap);
//        final Paint paint = new Paint();
//        paint.setColor(Color.RED);
//        paint.setStyle(Paint.Style.STROKE);
//        paint.setStrokeWidth(2.0f);
//
//        // Map recognized co-ords back to real image.
//        final List<Classifier.Recognition> mappedRecognitions = new LinkedList<>();
//
//        for (Classifier.Recognition result : results) {
//            RectF location = result.getLocation();
//            if (location != null && result.getConfidence() >= MINIMUM_CONFIDENCE) {
//                canvas.drawRect(location, paint);
//
//                cropToFrameTransform.mapRect(location);
//
//                result.setLocation(location);
//                mappedRecognitions.add(result);
//            }
//        }
//


        // Fire notifications
//        computingDetection = false;
//
//        runOnUiThread(
//                new Runnable() {
//                    @Override
//                    public void run() {
//                        showFrameInfo(previewWidth + "x" + previewHeight);
//                        showCropInfo(cropCopyBitmap.getWidth() + "x" + cropCopyBitmap.getHeight());
//                    }
//                });
//                    }
//                });
    }

    /*
        Results to Text
     */
    private String extractTextFromResults(List<Classifier.Recognition> results, float imageHeight) {
        // FUTURE: Filter by aspect ratio as well?
        // REVISIT: Anything with confidence 20-50% likely means something is messed up; warn user?

        Comparator<? super Classifier.Recognition> sortByX =
                (r1, r2) -> (int) Math.signum(r1.getLocation().centerX() - r2.getLocation().centerX());
        Comparator<? super Classifier.Recognition> sortByY =
                (r1, r2) -> (int) Math.signum(r1.getLocation().centerY() - r2.getLocation().centerY());

        List<Classifier.Recognition> processed = new ArrayList<>(results);

        // Filter list of results by:
        // .. confidence %
        for (int i = processed.size() - 1; i >= 0; i--) {
            Classifier.Recognition result = processed.get(i);
            if (result.getConfidence() < MINIMUM_CONFIDENCE) {
                processed.remove(i);
            }
        }

        // .. height of regions must be "on same line"
        if (processed.size() > 1) {
//            Collections.sort(processed, sortByY);
//            Classifier.Recognition middleResult = processed.get(processed.size() / 2);
//            float medianY = middleResult.getLocation().centerY();
//
            float yClosestToCentre = 0;
            float smallestError = 9999;
            float actualMiddle = imageHeight / 2;
            for (Classifier.Recognition result : processed) {
                float error = Math.abs(actualMiddle - result.getLocation().centerY());
                if (error < smallestError) {
                    yClosestToCentre = result.getLocation().centerY();
                    smallestError = error;
                }
            }

            float tolerance = imageHeight * FILTER_RESULT_BY_CENTER_PERCENT;
            for (int i = processed.size() - 1; i >= 0; i--) {
                Classifier.Recognition result = processed.get(i);
                float centerY = result.getLocation().centerY();
                if (centerY > yClosestToCentre + tolerance || centerY < yClosestToCentre - tolerance) {
                    processed.remove(i);
                }
            }
        }

        // Extract text from all that's left (left to right)
        Collections.sort(processed, sortByX);
        String text = "";
        for (Classifier.Recognition result : processed) {
            text += result.getTitle();
        }

        return text;
    }

    protected synchronized void runInBackground(final Runnable r) {
        if (handler != null) {
            handler.post(r);
        }
    }


    public interface OnProcessImageDone {
        void notifyOfExtractedText(String extractedText);

        void notifyOfBoundingBoxes(List<Classifier.Recognition> recognitions);

        // DEBUG ONLY
        void notifyOfRawBoundingBoxes(Bitmap inputToNeuralNetBmp, List<Classifier.Recognition> recognitions);
    }

}
