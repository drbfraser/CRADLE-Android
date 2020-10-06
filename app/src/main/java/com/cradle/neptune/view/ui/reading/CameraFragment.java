package com.cradle.neptune.view.ui.reading;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.hardware.Camera;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.SeekBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.cradle.neptune.R;
import com.cradle.neptune.ocr.CradleOverlay;
import com.cradle.neptune.view.ui.camera.CameraPreview;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Take a photo of a CRADLE VSA device after a currentReading has been taken.
 * REQUIRES: Add permissions for camera and disk access inside app.
 */
public class CameraFragment extends BaseFragment {

    private static final int FOCUS_PERIOD_ms = 1000;
    private Camera mCamera;
    private CameraPreview mPreview;
    // zoom
    private ScaleGestureDetector mScaleGestureDectector;
    private float mScaleFactor = 1.0f;
    private FrameLayout mScaleImageView;
    private boolean mIsTakingPhotoNow;
    private Handler focusTimerHandler = new Handler();
    private Runnable focusTimerRunnable;
    private SeekBar zoomSeekBar;

    private Camera.PictureCallback mPicture = new Camera.PictureCallback() {
        @Override
        public void onPictureTaken(byte[] data, Camera camera) {


            File pictureFile = getOutputMediaFile();

            String baseName = pictureFile.getAbsolutePath();
            baseName = baseName.substring(0, baseName.lastIndexOf('.'));
            File pictureFileFull = new File(baseName + "_0full.jpg");
            File pictureFileSys = new File(baseName + "_1sys.jpg");
            File pictureFileDia = new File(baseName + "_2dia.jpg");
            File pictureFileHr = new File(baseName + "_3hr.jpg");

            try {

                // save image
                FileOutputStream fos = new FileOutputStream(pictureFileFull);
                fos.write(data);
                fos.close();

                // rotate and resize image
                setRotationInImage(pictureFileFull, ExifInterface.ORIENTATION_ROTATE_90);
                Bitmap bmp = handleSamplingAndRotationBitmap(getContext(), Uri.fromFile(pictureFileFull));

                // DEBUG: draw bounding rectangles on the image
                //bmp = drawBreakpointsOnImage(bmp);

                // save full image
                writeBitMapToFile(bmp, pictureFileFull.getPath());

                // extract number images
                writePhotoRegionToFileRelativeToOverlay(bmp, pictureFile, CradleOverlay.OverlayRegion.OVERLAY_REGION_SCREEN);
                writePhotoRegionToFileRelativeToOverlay(bmp, pictureFileSys, CradleOverlay.OverlayRegion.OVERLAY_REGION_SYS);
                writePhotoRegionToFileRelativeToOverlay(bmp, pictureFileDia, CradleOverlay.OverlayRegion.OVERLAY_REGION_DIA);
                writePhotoRegionToFileRelativeToOverlay(bmp, pictureFileHr, CradleOverlay.OverlayRegion.OVERLAY_REGION_HR);


                // DEBUG: Test image recognition
//                OcrDigitDetector detector = new OcrDigitDetector(getActivity(), bmp.getWidth(), bmp.getHeight());
//                detector.processImage(bmp, null);

                // clear any current vitals we may have gotten from our previous image
                getViewModel().setBloodPressure(null);

//                currentReading.clearManualChangeOcrResultsFlags();
//                currentReading.bpSystolic = null;
//                currentReading.bpDiastolic = null;
//                currentReading.heartRateBPM = null;

                // done: advance to next tab
                getViewModel().getMetadata().setPhotoPath(pictureFile.getPath());
//                currentReading.pathToPhoto = pictureFile.getPath();
                Toast.makeText(getContext(), "Photo taken successfully!", Toast.LENGTH_SHORT).show();
                activityCallbackListener.advanceToNextPage();

            } catch (FileNotFoundException e) {
                Log.d(TAG, "File not found: " + e.getMessage());
            } catch (IOException e) {
                Log.e(TAG, "Error accessing file: " + e.getMessage());
            }
        }
    };

    public CameraFragment() {
        // Required empty public constructor
    }

    public static CameraFragment newInstance() {
        return new CameraFragment();
    }

    /**
     * Set the rotation of an image on file.
     *
     * @param image       what to rotate
     * @param orientation such as ExifInterface.ORIENTATION_ROTATE_90
     *                    source: https://stackoverflow.com/questions/19753912/set-image-orientation-using-exifinterface
     */
    public static void setRotationInImage(File image, int orientation) {

        try {
            ExifInterface exifi = new ExifInterface(image.getAbsolutePath());
            exifi.setAttribute(ExifInterface.TAG_ORIENTATION, String.valueOf(orientation));
            exifi.saveAttributes();
        } catch (IOException e) {
            Log.e(CameraFragment.class.getName(), "Unable to rotate image: Exif error");
        }
    }

    /**
     * This method is responsible for solving the rotation issue if exist. Also scale the images to
     * 1024x1024 resolution
     *
     * @param context       The current context
     * @param selectedImage The Image URI
     * @return Bitmap image results
     * @throws IOException
     */
    public static Bitmap handleSamplingAndRotationBitmap(Context context, Uri selectedImage)
            throws IOException {
        int MAX_HEIGHT = 1024;
        int MAX_WIDTH = 1024;

        // First decode with inJustDecodeBounds=true to check dimensions
        final BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        InputStream imageStream = context.getContentResolver().openInputStream(selectedImage);
        BitmapFactory.decodeStream(imageStream, null, options);
        imageStream.close();

        // Calculate inSampleSize
        options.inSampleSize = calculateInSampleSize(options, MAX_WIDTH, MAX_HEIGHT);
        // Decode bitmap with inSampleSize set
        options.inJustDecodeBounds = false;
        imageStream = context.getContentResolver().openInputStream(selectedImage);
        Bitmap img = BitmapFactory.decodeStream(imageStream, null, options);

        img = rotateImageIfRequired(img, selectedImage);
        return img;
    }

    /**
     * Calculate an inSampleSize for use in a {@link BitmapFactory.Options} object when decoding
     * bitmaps using the decode* methods from {@link BitmapFactory}. This implementation calculates
     * the closest inSampleSize that will result in the final decoded bitmap having a width and
     * height equal to or larger than the requested width and height. This implementation does not
     * ensure a power of 2 is returned for inSampleSize which can be faster when decoding but
     * results in a larger bitmap which isn't as useful for caching purposes.
     *
     * @param options   An options object with out* params already populated (run through a decode*
     *                  method with inJustDecodeBounds==true
     * @param reqWidth  The requested width of the resulting bitmap
     * @param reqHeight The requested height of the resulting bitmap
     * @return The value to be used for inSampleSize
     */
    private static int calculateInSampleSize(BitmapFactory.Options options,
                                             int reqWidth, int reqHeight) {
        // Raw height and width of image
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 2;

        if (height > reqHeight || width > reqWidth) {

            // Calculate ratios of height and width to requested height and width
            final int heightRatio = Math.round((float) height / (float) reqHeight);
            final int widthRatio = Math.round((float) width / (float) reqWidth);

            // Choose the smallest ratio as inSampleSize value, this will guarantee a final image
            // with both dimensions larger than or equal to the requested height and width.
            inSampleSize = heightRatio < widthRatio ? heightRatio : widthRatio;

            // This offers some additional logic in case the image has a strange
            // aspect ratio. For example, a panorama may have a much larger
            // width than height. In these cases the total pixels might still
            // end up being too large to fit comfortably in memory, so we should
            // be more aggressive with sample down the image (=larger inSampleSize).

            final float totalPixels = width * height;

            // Anything more than 2x the requested pixels we'll sample down further
            final float totalReqPixelsCap = reqWidth * reqHeight * 2;

            while (totalPixels / (inSampleSize * inSampleSize) > totalReqPixelsCap) {
                inSampleSize++;
            }
        }
        return inSampleSize;
    }

    /**
     * Rotate an image if required.
     *
     * @param img           The image bitmap
     * @param selectedImage Image URI
     * @return The resulted Bitmap after manipulation
     */
    private static Bitmap rotateImageIfRequired(Bitmap img, Uri selectedImage) throws IOException {

        ExifInterface ei = new ExifInterface(selectedImage.getPath());
        int orientation = ei.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);

        switch (orientation) {
            case ExifInterface.ORIENTATION_ROTATE_90:
                return rotateImage(img, 90);
            case ExifInterface.ORIENTATION_ROTATE_180:
                return rotateImage(img, 180);
            case ExifInterface.ORIENTATION_ROTATE_270:
                return rotateImage(img, 270);
            default:
                return img;
        }
    }

    private static Bitmap rotateImage(Bitmap img, int degree) {
        Matrix matrix = new Matrix();
        matrix.postRotate(degree);
        Bitmap rotatedImg = Bitmap.createBitmap(img, 0, 0, img.getWidth(), img.getHeight(), matrix, true);
        img.recycle();
        return rotatedImg;
    }

    private static void writeBitMapToFile(Bitmap img, String path) {
        try (FileOutputStream out = new FileOutputStream(path)) {
            img.compress(Bitmap.CompressFormat.JPEG, 80, out);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_camera, container, false);

    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        zoomSeekBar = view.findViewById(R.id.zoomSeekbar);

        setupCamera();
        setupZoom();
        setupFocusTimer();

        // Take Photo Button:
        mIsTakingPhotoNow = false;
        Button btnTakePhoto = view.findViewById(R.id.btnCapturePhoto);
        btnTakePhoto.setOnClickListener(view1 -> {
            try {
                mIsTakingPhotoNow = true;
                focusTimerHandler.removeCallbacks(focusTimerRunnable);

                // Ensure preview running:
                if (mCamera == null) {
                    setupCamera();
                }
                setPhotoSizeToMatchPreview();

                // May generate an exception, which we catch.
                mCamera.startPreview();

                // CALLBACK: Button pressed
                mCamera.autoFocus((success, camera) -> {
                    if (!success) {
                        // REVISIT: Handle this better?
                        Log.e(TAG, "Unable to auto focus; taking photo anyways!");
                        Toast.makeText(getContext(), "Trouble focusing well. Move further away?", Toast.LENGTH_LONG).show();
                    }
                    // CALLBACK: Auto-focus done
                    if (mIsTakingPhotoNow) {
                        mIsTakingPhotoNow = false;
                        mCamera.takePicture(null, null, mPicture);
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "Failed taking photo with exception.", e);
                Toast.makeText(getContext(), "Unable to access camera. Close other camera apps then try again.", Toast.LENGTH_LONG).show();
            }
        });
    }


    /* ***********************************************************
     *                      Camera Support
     * ***********************************************************/
    // Source: https://developer.android.com/guide/topics/media/camera#java

    private void setupFocusTimer() {
        // make it restarting
        focusTimerRunnable = new Runnable() {
            @Override
            public void run() {
                if (mCamera != null) {
                    try {
                        mCamera.autoFocus((b, camera) ->
                                // re-queue focus event
                                focusTimerHandler.postDelayed(this, FOCUS_PERIOD_ms)
                        );
                    } catch (RuntimeException ex) {
                        if (getActivity() != null) {
                            Log.w(TAG, "Auto focus failed; likely because camera destroyed while autofocusing.", ex);
                        }
                    }
                }
            }
        };

        // start it
        focusTimerHandler.postDelayed(focusTimerRunnable, 0);
    }

    private void setupCamera() {
        if (getView() == null) {
            return;
        }
        // Create an instance of Camera
        mCamera = getCameraInstance();
        if (mCamera != null) {

            // Create our Preview view and set it as the content of our activity.
            mPreview = new CameraPreview(this.getContext(), mCamera, zoomSeekBar);
            FrameLayout preview = getView().findViewById(R.id.camera_preview);
            preview.addView(mPreview);

            // don't set photo size here, because preview only sets its size
            // after it has been draw!
        }
    }

    private void setPhotoSizeToMatchPreview() {
        // Set camera options
        Camera.Parameters params = mCamera.getParameters();

        if (mPreview == null || mPreview.getPreviewSize() == null) {
            Log.e(TAG, "ERROR: Trying to set photo size to match preview before preview size set.");
            return;
        }

        Camera.Size pSize = mPreview.getPreviewSize();
        Log.d(TAG, "preview size: " + pSize.width + "x" + pSize.height + "   ratio: " + ((float) pSize.height / pSize.width));

        // find perfect match:
        Camera.Size bestSize = null;
        for (Camera.Size size : params.getSupportedPictureSizes()) {
            if (size.width == pSize.width && size.height == pSize.height) {
                bestSize = size;
                Log.d(TAG, "photo size matches preview:  " + size.width + "x" + size.height + "   ratio h/w: " + ((float) size.height / size.width));
            }
        }

        // if no perfect match, select the closet ratio:
        if (bestSize == null) {
            float perfectRatio = (float) pSize.height / pSize.width;
            float smallestError = 999999;
            for (Camera.Size size : params.getSupportedPictureSizes()) {
                float ratio = (float) size.height / size.width;
                float error = Math.abs(perfectRatio - ratio);
                if (error < smallestError) {
                    smallestError = error;
                    bestSize = size;
                    Log.d(TAG, "picking size with closer ratio:  " + size.width + "x" + size.height + "   ratio h/w: " + ((float) size.height / size.width));
                }
            }
        }

        if (bestSize != null) {
            params.setPictureSize(bestSize.width, bestSize.height);
        }

        mCamera.setParameters(params);
    }

    @Override
    public void onPause() {
        super.onPause();

        // release camera:
        // TODO: Test if we need to reacquire it too anywhere?
        if (mCamera != null) {
            mCamera.release();
            mCamera = null;
        }

        // stop timer
        focusTimerHandler.removeCallbacks(focusTimerRunnable);
    }






    /* *******************************************************************
        Image rotation
        source: https://www.samieltamawy.com/how-to-fix-the-camera-intent-rotated-image-in-android/
     ******************************************************************** */

    @Override
    public void onResume() {
        super.onResume();

        // reacquire camera
        if (mCamera == null) {
            setupCamera();
        }

        // start timer
        setupFocusTimer();
    }

    @Override
    public void onMyBeingDisplayed() {
        // may not have created view yet.
        if (getView() == null) {
            return;
        }
        hideKeyboard();

        // Call startPreview() to restart the preview.
        if (mCamera != null) {
            mCamera.startPreview();
        }
    }

    @Override
    public boolean onMyBeingHidden() {
        // may not have created view yet.
        if (getView() == null) {
            return true;
        }

        // Call stopPreview() to stop updating the preview surface.
        if (mCamera != null) {
            mCamera.stopPreview();
        }
        return true;
    }

    /**
     * A safe way to get an instance of the Camera object.
     */
    public Camera getCameraInstance() {
        Camera c = null;
        try {
            c = Camera.open(); // attempt to get a Camera instance
        } catch (Exception e) {
            // Camera is not available (in use or does not exist)
            Log.e(TAG, "Unable to open camera", e);
            if (getActivity() != null) {
                Toast.makeText(getActivity(), "Unable to access camera. Close other camera apps then try again.", Toast.LENGTH_LONG).show();
            }
        }
        return c; // returns null if camera is unavailable
    }

    /**
     * Create a file Uri for saving an image
     */
    private Uri getOutputMediaFileUri() {
        return Uri.fromFile(getOutputMediaFile());
    }

    /**
     * Create a File for saving an image
     */
    private File getOutputMediaFile() {
        // To be safe, you should check that the SDCard is mounted
        // using Environment.getExternalStorageState() before doing this.

        File mediaStorageDir = new File(
                // For public storage:
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),

                // For less public storage
//                getContext().getExternalFilesDir(Environment.DIRECTORY_PICTURES),

                // TODO: Store photos in a private protected directory.
//                getContext().getFilesDir()

                "CradleSupportApp");
        // This location works best if you want the created images to be shared
        // between applications and persist after your app has been uninstalled.

        // Create the storage directory if it does not exist
        if (!mediaStorageDir.exists()) {
            if (!mediaStorageDir.mkdirs()) {
                Log.d("MyCameraApp", "failed to create directory");
                return null;
            }
        }

        // Create a media file name
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        File mediaFile;
        mediaFile = new File(mediaStorageDir.getPath() + File.separator +
                "IMG_" + timeStamp + ".jpg");

        return mediaFile;
    }

    /* ***********************************************************
     *                      Zoom
     * ***********************************************************/
    private void setupZoom() {
        // Register touch events:
        getView().setOnTouchListener(new View.OnTouchListener() {
            public boolean onTouch(View v, MotionEvent event) {
                if (mScaleGestureDectector != null) {
                    mScaleGestureDectector.onTouchEvent(event);
                }
                return true;
            }
        });

        mScaleImageView = getView().findViewById(R.id.camera_preview);
        mScaleGestureDectector = new ScaleGestureDetector(getActivity(), new ScaleListener());
    }

    /* ***********************************************************
     *                      Segment Image
     * ***********************************************************/
    private void writePhotoRegionToFileRelativeToOverlay(Bitmap source, File target, CradleOverlay.OverlayRegion region) {
        // Extract
        Bitmap extracted = CradleOverlay.extractBitmapRegionFromCameraImage(source, region);

        // write to file
        writeBitMapToFile(extracted, target.getAbsolutePath());
    }

    private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
        @Override
        public boolean onScale(ScaleGestureDetector scaleGestureDetector) {
            mScaleFactor *= scaleGestureDetector.getScaleFactor();
            // REVISIT: Disable pinch to zoom until we also change the image too.
            mScaleFactor = Math.max(1.0f,
                    Math.min(mScaleFactor, 1.0f));
            mScaleImageView.setScaleX(mScaleFactor);
            mScaleImageView.setScaleY(mScaleFactor);
            return true;
        }
    }


}
