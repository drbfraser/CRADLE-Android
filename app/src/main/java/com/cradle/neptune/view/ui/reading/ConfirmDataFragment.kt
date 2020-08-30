package com.cradle.neptune.view.ui.reading

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import com.cradle.neptune.R
import com.cradle.neptune.dagger.MyApp
import com.cradle.neptune.model.BloodPressure
import com.cradle.neptune.model.MAX_DIASTOLIC
import com.cradle.neptune.model.MAX_HEART_RATE
import com.cradle.neptune.model.MAX_SYSTOLIC
import com.cradle.neptune.model.MIN_DIASTOLIC
import com.cradle.neptune.model.MIN_HEART_RATE
import com.cradle.neptune.model.MIN_SYSTOLIC
import com.cradle.neptune.model.Settings
import com.cradle.neptune.ocr.BorderedText
import com.cradle.neptune.ocr.CradleOverlay
import com.cradle.neptune.ocr.CradleOverlay.OverlayRegion
import com.cradle.neptune.ocr.OcrDigitDetector
import com.cradle.neptune.ocr.OcrDigitDetector.OnProcessImageDone
import com.cradle.neptune.ocr.tflite.Classifier.Recognition
import com.cradle.neptune.utilitiles.Util
import com.wonderkiln.blurkit.BlurKit
import javax.inject.Inject

/**
 * Allow user to confirm data from the CRADLE photo.
 */
class ConfirmDataFragment : BaseFragment() {
    @JvmField
    @Inject
    var settings: Settings? = null
    private var makingProgramaticChangeToVitals = false
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // inject:
        (requireActivity().application as MyApp).appComponent.inject(this)
        Log.d(TAG, "OCR?       " + settings!!.isOcrEnabled)
        Log.d(TAG, "OCR Debug? " + settings!!.isOcrDebugEnabled)

        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_confrim_data, container, false)
    }

    override fun onMyBeingDisplayed() {
        // may not have created view yet.
        if (view == null) {
            return
        }
        hideKeyboard()
        makingProgramaticChangeToVitals = true
        setTextBox(
            R.id.etSystolic,viewModel?.bloodPressure?.systolic
        )
        setTextBox(
            R.id.etDiastolic,viewModel?.bloodPressure?.diastolic
        )
        setTextBox(
            R.id.etHeartRate,
            viewModel?.bloodPressure?.heartRate
        )
        //        setTextBox(R.id.etSystolic, currentReading.bpSystolic);
//        setTextBox(R.id.etDiastolic, currentReading.bpDiastolic);
//        setTextBox(R.id.etHeartRate, currentReading.heartRateBPM);
        makingProgramaticChangeToVitals = false

        // display photo
        if (viewModel?.metadata?.photoPath != null) {
//        if (currentReading.pathToPhoto != null) {
            val iv =
                requireView().findViewById<ImageView>(R.id.imageViewPhoto)
            val bitmap = BitmapFactory.decodeFile(viewModel!!.metadata.photoPath)
            //            Bitmap bitmap = BitmapFactory.decodeFile(currentReading.pathToPhoto);
            iv.setImageBitmap(bitmap)
        }

        // display no-photo warning
        val tv = requireView().findViewById<TextView>(R.id.txtNoPhotoWarning)
        tv.visibility = if (viewModel?.metadata?.photoPath == null) View.VISIBLE else View.GONE
        //        tv.setVisibility(currentReading.pathToPhoto == null ? View.VISIBLE : View.GONE);
        setupTextEdits()
        setupTagPhotonFiles()
        setupOcr()
        doOcrOnCurrentImage()
    }

    private fun setupTextEdits() {
        watchForUserTextEntry(
            R.id.etSystolic,
            MANUAL_USER_ENTRY_SYSTOLIC
        )
        watchForUserTextEntry(
            R.id.etDiastolic,
            MANUAL_USER_ENTRY_DIASTOLIC
        )
        watchForUserTextEntry(
            R.id.etHeartRate,
            MANUAL_USER_ENTRY_HEARTRATE
        )
    }

    private fun watchForUserTextEntry(id: Int, mask: Int) {
        val et = requireView().findViewById<EditText>(id)
        et.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(
                charSequence: CharSequence,
                i: Int,
                i1: Int,
                i2: Int
            ) {
            }

            override fun onTextChanged(
                charSequence: CharSequence,
                i: Int,
                i1: Int,
                i2: Int
            ) {
                if (!makingProgramaticChangeToVitals) {
//                    currentReading.setAManualChangeOcrResultsFlags(mask);
                }
            }

            override fun afterTextChanged(editable: Editable) {}
        })
    }

    private fun setupTagPhotonFiles() {
        // TESTING ONLY!
        val btn =
            requireView().findViewById<Button>(R.id.btnSetBlurSize)
        btn.setOnClickListener { view: View? -> doOcrOnCurrentImage() }
    }

    override fun onMyBeingHidden(): Boolean {
        // may not have created view yet.
        if (view == null) {
            return true
        }
        val bpSystolic = getEditTextValue(R.id.etSystolic)
        val bpDiastolic = getEditTextValue(R.id.etDiastolic)
        val heartRateBPM = getEditTextValue(R.id.etHeartRate)
        if (bpSystolic == null || bpDiastolic == null || heartRateBPM == null) {
            viewModel?.bloodPressure = null
        } else {
            viewModel?.bloodPressure = BloodPressure(bpSystolic, bpDiastolic, heartRateBPM)
        }
        //        currentReading.bpSystolic = getEditTextValue(R.id.etSystolic);
//        currentReading.bpDiastolic = getEditTextValue(R.id.etDiastolic);
//        currentReading.heartRateBPM = getEditTextValue(R.id.etHeartRate);
        return true
    }

    private fun setTextBox(id: Int, value: Int?) {
        var msg = ""
        if (value != null) {
            msg = Integer.toString(value)
        }
        val et = requireView().findViewById<EditText>(id)
        et.setText(msg)
    }

    private fun getEditTextValue(id: Int): Int? {
        val et = requireView().findViewById<EditText>(id)
        val contents = et.text.toString().trim { it <= ' ' }
        return if (contents.length > 0) {
            Util.stringToIntOr0(contents)
        } else {
            null
        }
    }

    private fun setupOcr() {
        if (view == null) {
            return
        }

        // show / hide OCR turned off warning
        val tvNoOcrWarning = requireView().findViewById<TextView>(R.id.tvOcrDisabled)
        tvNoOcrWarning.visibility = if (settings!!.isOcrEnabled) View.GONE else View.VISIBLE

        // show / hide OCR debugging content
        val vDebug =
            requireView().findViewById<View>(R.id.groupDebugOcr)
        vDebug.visibility = if (settings!!.isOcrDebugEnabled) View.VISIBLE else View.GONE
    }

    private fun doOcrOnCurrentImage() {
        if (!settings!!.isOcrEnabled) {
            Log.i(TAG, "OCR Disabled; skipping OCR")
            return
        }
        val savedImage = BitmapFactory.decodeFile(viewModel!!.metadata.photoPath)
        //        Bitmap savedImage = BitmapFactory.decodeFile(currentReading.pathToPhoto);
        if (savedImage != null) {
            ocrOneLine(0, savedImage, OverlayRegion.OVERLAY_REGION_SYS)
            ocrOneLine(1, savedImage, OverlayRegion.OVERLAY_REGION_DIA)
            ocrOneLine(2, savedImage, OverlayRegion.OVERLAY_REGION_HR)
        }
    }

    // REVISIT: make more lifetime aware: null view, ...
    private fun ocrOneLine(
        rowNumber: Int,
        cradleScreenImage: Bitmap,
        region: OverlayRegion
    ) {
        // crop image
        val savedImage =
            CradleOverlay.extractBitmapRegionFromScreenImage(cradleScreenImage, region)

        // ocr
        val detector = OcrDigitDetector(
            activity,
            savedImage.width,
            savedImage.height
        )
        val tv = requireActivity().findViewById<TextView>(R.id.etBlurRadius)
        OcrDigitDetector.g_blurRadiusREVISIT = tv.text.toString().toInt()
        detector.processImage(savedImage, object : OnProcessImageDone {
            override fun notifyOfBoundingBoxes(recognitions: List<Recognition>) {
                // ensure OCR debug enabled
                if (!settings!!.isOcrDebugEnabled) {
                    return
                }
                val iv = view!!.findViewById<ImageView>(
                    OCR_DEBUG_IDS[rowNumber][OCR_DEBUG_IDS_SCALED_IDX]
                )
                val copyBmp = Bitmap.createBitmap(savedImage)
                val disableBlur =
                    (activity!!.application as MyApp).isDisableBlurKit
                if (OcrDigitDetector.g_blurRadiusREVISIT > 0 && !disableBlur) {
                    BlurKit.getInstance().blur(copyBmp, OcrDigitDetector.g_blurRadiusREVISIT)
                }
                drawBoundingBoxesOnBitmap(copyBmp, recognitions)
                iv.setImageBitmap(copyBmp)
            }

            override fun notifyOfRawBoundingBoxes(
                inputToNeuralNetBmp: Bitmap,
                recognitions: List<Recognition>
            ) {
                // ensure OCR debug enabled
                if (!settings!!.isOcrDebugEnabled) {
                    return
                }
                val iv = view!!.findViewById<ImageView>(
                    OCR_DEBUG_IDS[rowNumber][OCR_DEBUG_IDS_RAW_IDX]
                )
                val copyBmp = Bitmap.createBitmap(inputToNeuralNetBmp)
                val disableBlur =
                    (activity!!.application as MyApp).isDisableBlurKit
                if (OcrDigitDetector.g_blurRadiusREVISIT > 0 && !disableBlur) {
                    BlurKit.getInstance().blur(copyBmp, OcrDigitDetector.g_blurRadiusREVISIT)
                }

                // drawBoundingBoxesOnBitmap(copyBmp, recognitions);
                iv.setImageBitmap(copyBmp)
            }

            override fun notifyOfExtractedText(extractedText: String) {
                // guard against not no view.
                if (view == null) {
                    return
                }

                // display text for debug
                // do this even if debug disabled because the widget is hidden.
                val tv = view!!.findViewById<TextView>(
                    OCR_DEBUG_IDS[rowNumber][OCR_DEBUG_IDS_TEXT_IDX]
                )
                tv.text = extractedText

                // if ensure it's a number and within range (min to max)
                var displayText = extractedText
                val minValues = intArrayOf(MIN_SYSTOLIC, MIN_DIASTOLIC, MIN_HEART_RATE)
                val maxValues = intArrayOf(MAX_SYSTOLIC, MAX_DIASTOLIC, MAX_HEART_RATE)
                try {
                    val extractedInt = extractedText.toInt()
                    if (extractedInt < minValues[rowNumber] || extractedInt > maxValues[rowNumber]
                    ) {
                        displayText = ""
                    }
                } catch (e: NumberFormatException) {
                    // it's not a # (its likely "")
                    displayText = ""
                }

                // put text into UI edit text
                makingProgramaticChangeToVitals = true
                Util.ensure(settings!!.isOcrEnabled)
                val textIds =
                    intArrayOf(R.id.etSystolic, R.id.etDiastolic, R.id.etHeartRate)
                val etId = textIds[rowNumber]
                val et = view!!.findViewById<EditText>(etId)
                if (et.text.toString().length == 0) {
                    // only set text if nothing there:
                    // done to prevent poor OCR from overwriting the user's manual change.
                    et.setText(displayText)
                }
                makingProgramaticChangeToVitals = false
            }
        })
    }

    private fun drawBoundingBoxesOnBitmap(
        bmp: Bitmap,
        recognitions: List<Recognition>
    ) {
        val MIN_CONFIDENCE_TO_SHOW = 0.1
        val TEXT_SIZE_DIP = 10f

        // setup canvas to paint into
        val canvas = Canvas(bmp)

        // setup paint style
        val paint = Paint()
        val colors = intArrayOf(
            Color.RED,
            Color.CYAN,
            Color.BLUE,
            Color.GREEN
        )
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2.0f

        // setup text style
        val textSizePx = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, TEXT_SIZE_DIP, resources.displayMetrics
        )
        val bt = BorderedText(textSizePx)
        bt.setTypeface(Typeface.MONOSPACE)
        var colorIdx = 0
        for (result in recognitions) {
            val location = result.location
            if (location != null && result.confidence >= MIN_CONFIDENCE_TO_SHOW) {
                paint.color = colors[colorIdx]
                colorIdx = (colorIdx + 1) % colors.size

                // draw rect
                canvas.drawRect(location, paint)

                // add text
                val message = String.format(
                    "%s:%.0f%%",
                    result.title,
                    result.confidence * 100
                )
                bt.drawText(
                    canvas,
                    result.location.left,
                    result.location.top,
                    message,
                    paint
                )
            }
        }
    }

    companion object {
        const val MANUAL_USER_ENTRY_SYSTOLIC = 1
        const val MANUAL_USER_ENTRY_DIASTOLIC = 2
        const val MANUAL_USER_ENTRY_HEARTRATE = 4

        /**
         * OCR
         */
        private const val OCR_DEBUG_IDS_SCALED_IDX = 0
        private const val OCR_DEBUG_IDS_RAW_IDX = 1
        private const val OCR_DEBUG_IDS_TEXT_IDX = 2
        private val OCR_DEBUG_IDS = arrayOf(
            intArrayOf(R.id.ivOcrScaled0, R.id.ivOcrRaw0, R.id.tvOcrText0),
            intArrayOf(R.id.ivOcrScaled1, R.id.ivOcrRaw1, R.id.tvOcrText1),
            intArrayOf(R.id.ivOcrScaled2, R.id.ivOcrRaw2, R.id.tvOcrText2)
        )

        fun newInstance(): ConfirmDataFragment {
            return ConfirmDataFragment()
        }
    }
}