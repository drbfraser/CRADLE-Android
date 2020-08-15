package com.cradle.neptune.view.ui.reading;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import com.cradle.neptune.R;
import com.cradle.neptune.dagger.MyApp;
import com.cradle.neptune.model.*;
import com.cradle.neptune.ocr.BorderedText;
import com.cradle.neptune.ocr.CradleOverlay;
import com.cradle.neptune.ocr.OcrDigitDetector;
import com.cradle.neptune.ocr.tflite.Classifier;
import com.cradle.neptune.utilitiles.Util;
import com.wonderkiln.blurkit.BlurKit;

import java.util.List;

import javax.inject.Inject;

import static com.cradle.neptune.utilitiles.Util.mapNullable;

/**
 * Allow user to confirm data from the CRADLE photo.
 */
public class ConfirmDataFragment extends BaseFragment {

    public static final int MANUAL_USER_ENTRY_SYSTOLIC = 1;
    public static final int MANUAL_USER_ENTRY_DIASTOLIC = 2;
    public static final int MANUAL_USER_ENTRY_HEARTRATE = 4;
    /**
     * OCR
     */
    private static final int OCR_DEBUG_IDS_SCALED_IDX = 0;
    private static final int OCR_DEBUG_IDS_RAW_IDX = 1;
    private static final int OCR_DEBUG_IDS_TEXT_IDX = 2;
    private static final int[][] OCR_DEBUG_IDS = {
            {R.id.ivOcrScaled0, R.id.ivOcrRaw0, R.id.tvOcrText0},
            {R.id.ivOcrScaled1, R.id.ivOcrRaw1, R.id.tvOcrText1},
            {R.id.ivOcrScaled2, R.id.ivOcrRaw2, R.id.tvOcrText2},
    };
    @Inject
    Settings settings;
    private boolean makingProgramaticChangeToVitals = false;

    public ConfirmDataFragment() {
        // Required empty public constructor
    }

    public static ConfirmDataFragment newInstance() {
        ConfirmDataFragment fragment = new ConfirmDataFragment();
        return fragment;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // inject:
        ((MyApp) getActivity().getApplication()).getAppComponent().inject(this);

        Log.d(TAG, "OCR?       " + settings.isOcrEnabled());
        Log.d(TAG, "OCR Debug? " + settings.isOcrDebugEnabled());


        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_confrim_data, container, false);
    }

    @Override
    public void onMyBeingDisplayed() {
        // may not have created view yet.
        if (getView() == null) {
            return;
        }
        hideKeyboard();

        makingProgramaticChangeToVitals = true;
        setTextBox(R.id.etSystolic, mapNullable(viewModel.getBloodPressure(), BloodPressure::getSystolic));
        setTextBox(R.id.etDiastolic, mapNullable(viewModel.getBloodPressure(), BloodPressure::getDiastolic));
        setTextBox(R.id.etHeartRate, mapNullable(viewModel.getBloodPressure(), BloodPressure::getHeartRate));
//        setTextBox(R.id.etSystolic, currentReading.bpSystolic);
//        setTextBox(R.id.etDiastolic, currentReading.bpDiastolic);
//        setTextBox(R.id.etHeartRate, currentReading.heartRateBPM);
        makingProgramaticChangeToVitals = false;

        // display photo
        if (viewModel.getMetadata().getPhotoPath() != null) {
//        if (currentReading.pathToPhoto != null) {
            ImageView iv = getView().findViewById(R.id.imageViewPhoto);
            Bitmap bitmap = BitmapFactory.decodeFile(viewModel.getMetadata().getPhotoPath());
//            Bitmap bitmap = BitmapFactory.decodeFile(currentReading.pathToPhoto);
            iv.setImageBitmap(bitmap);
        }

        // display no-photo warning
        TextView tv = getView().findViewById(R.id.txtNoPhotoWarning);
        tv.setVisibility(viewModel.getMetadata().getPhotoPath() == null ? View.VISIBLE : View.GONE);
//        tv.setVisibility(currentReading.pathToPhoto == null ? View.VISIBLE : View.GONE);

        setupTextEdits();
        setupTagPhotonFiles();
        setupOcr();
        doOcrOnCurrentImage();
    }

    private void setupTextEdits() {
        watchForUserTextEntry(R.id.etSystolic, MANUAL_USER_ENTRY_SYSTOLIC);
        watchForUserTextEntry(R.id.etDiastolic, MANUAL_USER_ENTRY_DIASTOLIC);
        watchForUserTextEntry(R.id.etHeartRate, MANUAL_USER_ENTRY_HEARTRATE);
    }

    private void watchForUserTextEntry(int id, int mask) {
        EditText et = getView().findViewById(id);
        et.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                if (!makingProgramaticChangeToVitals) {
//                    currentReading.setAManualChangeOcrResultsFlags(mask);
                }
            }

            @Override
            public void afterTextChanged(Editable editable) {
            }
        });
    }

    private void setupTagPhotonFiles() {
        // TESTING ONLY!
        Button btn = getView().findViewById(R.id.btnSetBlurSize);
        btn.setOnClickListener(view -> {
            doOcrOnCurrentImage();
        });
    }

    @Override
    public boolean onMyBeingHidden() {
        // may not have created view yet.
        if (getView() == null) {
            return true;
        }
        Integer bpSystolic = getEditTextValue(R.id.etSystolic);
        Integer bpDiastolic = getEditTextValue(R.id.etDiastolic);
        Integer heartRateBPM = getEditTextValue(R.id.etHeartRate);
        if (bpSystolic == null || bpDiastolic == null || heartRateBPM == null) {
            viewModel.setBloodPressure(null);
        } else {
            viewModel.setBloodPressure(new BloodPressure(bpSystolic, bpDiastolic, heartRateBPM));
        }
//        currentReading.bpSystolic = getEditTextValue(R.id.etSystolic);
//        currentReading.bpDiastolic = getEditTextValue(R.id.etDiastolic);
//        currentReading.heartRateBPM = getEditTextValue(R.id.etHeartRate);
        return true;
    }

    private void setTextBox(int id, Integer value) {
        String msg = "";
        if (value != null) {
            msg = Integer.toString(value);
        }
        EditText et = getView().findViewById(id);
        et.setText(msg);
    }

    private Integer getEditTextValue(int id) {
        EditText et = getView().findViewById(id);
        String contents = et.getText().toString().trim();
        if (contents.length() > 0) {
            return Util.stringToIntOr0(contents);
        } else {
            return null;
        }
    }

    private void setupOcr() {
        if (getView() == null) {
            return;
        }

        // show / hide OCR turned off warning
        TextView tvNoOcrWarning = getView().findViewById(R.id.tvOcrDisabled);
        tvNoOcrWarning.setVisibility(settings.isOcrEnabled() ? View.GONE : View.VISIBLE);

        // show / hide OCR debugging content
        View vDebug = getView().findViewById(R.id.groupDebugOcr);
        vDebug.setVisibility(settings.isOcrDebugEnabled() ? View.VISIBLE : View.GONE);
    }

    private void doOcrOnCurrentImage() {
        if (!settings.isOcrEnabled()) {
            Log.i(TAG, "OCR Disabled; skipping OCR");
            return;
        }

        Bitmap savedImage = BitmapFactory.decodeFile(viewModel.getMetadata().getPhotoPath());
//        Bitmap savedImage = BitmapFactory.decodeFile(currentReading.pathToPhoto);
        if (savedImage != null) {
            ocrOneLine(0, savedImage, CradleOverlay.OverlayRegion.OVERLAY_REGION_SYS);
            ocrOneLine(1, savedImage, CradleOverlay.OverlayRegion.OVERLAY_REGION_DIA);
            ocrOneLine(2, savedImage, CradleOverlay.OverlayRegion.OVERLAY_REGION_HR);
        }
    }

    // REVISIT: make more lifetime aware: null view, ...
    private void ocrOneLine(int rowNumber, Bitmap cradleScreenImage, CradleOverlay.OverlayRegion region) {
        // crop image
        Bitmap savedImage = CradleOverlay.extractBitmapRegionFromScreenImage(cradleScreenImage, region);

        // ocr
        OcrDigitDetector detector = new OcrDigitDetector(
                getActivity(),
                savedImage.getWidth(),
                savedImage.getHeight());

        TextView tv = getActivity().findViewById(R.id.etBlurRadius);
        OcrDigitDetector.g_blurRadiusREVISIT = Integer.parseInt(tv.getText().toString());

        detector.processImage(savedImage, new OcrDigitDetector.OnProcessImageDone() {
            @Override
            public void notifyOfBoundingBoxes(List<Classifier.Recognition> recognitions) {
                // ensure OCR debug enabled
                if (!settings.isOcrDebugEnabled()) {
                    return;
                }

                ImageView iv = getView().findViewById(OCR_DEBUG_IDS[rowNumber][OCR_DEBUG_IDS_SCALED_IDX]);
                Bitmap copyBmp = Bitmap.createBitmap(savedImage);

                boolean disableBlur = ((MyApp) getActivity().getApplication()).isDisableBlurKit();
                if (OcrDigitDetector.g_blurRadiusREVISIT > 0 && !disableBlur) {
                    BlurKit.getInstance().blur(copyBmp, OcrDigitDetector.g_blurRadiusREVISIT);
                }

                drawBoundingBoxesOnBitmap(copyBmp, recognitions);
                iv.setImageBitmap(copyBmp);
            }

            @Override
            public void notifyOfRawBoundingBoxes(Bitmap inputToNeuralNetBmp, List<Classifier.Recognition> recognitions) {
                // ensure OCR debug enabled
                if (!settings.isOcrDebugEnabled()) {
                    return;
                }

                ImageView iv = getView().findViewById(OCR_DEBUG_IDS[rowNumber][OCR_DEBUG_IDS_RAW_IDX]);
                Bitmap copyBmp = Bitmap.createBitmap(inputToNeuralNetBmp);

                boolean disableBlur = ((MyApp) getActivity().getApplication()).isDisableBlurKit();
                if (OcrDigitDetector.g_blurRadiusREVISIT > 0 && !disableBlur) {
                    BlurKit.getInstance().blur(copyBmp, OcrDigitDetector.g_blurRadiusREVISIT);
                }

                // drawBoundingBoxesOnBitmap(copyBmp, recognitions);
                iv.setImageBitmap(copyBmp);
            }

            @Override
            public void notifyOfExtractedText(String extractedText) {
                // guard against not no view.
                if (getView() == null) {
                    return;
                }

                // display text for debug
                // do this even if debug disabled because the widget is hidden.
                TextView tv = getView().findViewById(OCR_DEBUG_IDS[rowNumber][OCR_DEBUG_IDS_TEXT_IDX]);
                tv.setText(extractedText);

                // if ensure it's a number and within range (min to max)
                String displayText = extractedText;
                int[] minValues = {ReadingKt.MIN_SYSTOLIC, ReadingKt.MIN_DIASTOLIC, ReadingKt.MIN_HEART_RATE};
                int[] maxValues = {ReadingKt.MAX_SYSTOLIC, ReadingKt.MAX_DIASTOLIC, ReadingKt.MAX_HEART_RATE};
                try {
                    int extractedInt = Integer.parseInt(extractedText);
                    if (extractedInt < minValues[rowNumber] || extractedInt > maxValues[rowNumber]) {
                        displayText = "";
                    }
                } catch (NumberFormatException e) {
                    // it's not a # (its likely "")
                    displayText = "";
                }

                // put text into UI edit text
                makingProgramaticChangeToVitals = true;
                Util.ensure(settings.isOcrEnabled());
                int[] textIds = {R.id.etSystolic, R.id.etDiastolic, R.id.etHeartRate};
                int etId = textIds[rowNumber];
                EditText et = getView().findViewById(etId);
                if (et.getText().toString().length() == 0) {
                    // only set text if nothing there:
                    // done to prevent poor OCR from overwriting the user's manual change.
                    et.setText(displayText);
                }
                makingProgramaticChangeToVitals = false;
            }
        });
    }

    private void drawBoundingBoxesOnBitmap(Bitmap bmp, List<Classifier.Recognition> recognitions) {
        final double MIN_CONFIDENCE_TO_SHOW = 0.1;
        final float TEXT_SIZE_DIP = 10;

        // setup canvas to paint into
        final Canvas canvas = new Canvas(bmp);

        // setup paint style
        final Paint paint = new Paint();
        int[] colors = {Color.RED, Color.CYAN, Color.BLUE, Color.GREEN};
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2.0f);

        // setup text style
        float textSizePx = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, TEXT_SIZE_DIP, getResources().getDisplayMetrics());
        BorderedText bt = new BorderedText(textSizePx);
        bt.setTypeface(Typeface.MONOSPACE);

        int colorIdx = 0;
        for (Classifier.Recognition result : recognitions) {
            RectF location = result.getLocation();
            if (location != null && result.getConfidence() >= MIN_CONFIDENCE_TO_SHOW) {
                paint.setColor(colors[colorIdx]);
                colorIdx = (colorIdx + 1) % colors.length;

                // draw rect
                canvas.drawRect(location, paint);

                // add text
                String message = String.format("%s:%.0f%%", result.getTitle(), result.getConfidence() * 100);
                bt.drawText(canvas, result.getLocation().left, result.getLocation().top, message, paint);
            }
        }
    }

}
