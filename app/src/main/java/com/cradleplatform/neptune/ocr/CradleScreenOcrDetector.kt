package com.cradleplatform.neptune.ocr

import android.content.Context
import android.graphics.Bitmap
import androidx.collection.ArrayMap
import androidx.collection.arrayMapOf
import com.cradleplatform.neptune.model.BloodPressure
import com.cradleplatform.neptune.model.Verifiable
import com.cradleplatform.neptune.ocr.tflite.Classifier
import com.cradleplatform.neptune.ocr.tflite.TFLiteObjectDetectionHelper
import java.io.Closeable
import kotlin.math.abs
import kotlin.math.sign

// For filtering
private const val MINIMUM_CONFIDENCE = 0.5f
private const val FILTER_RESULT_BY_CENTER_PERCENT = 0.15f

/**
 * Handles getting digits from running OCR on a picture of the CRADLE screen.
 */
class CradleScreenOcrDetector(ctx: Context) : Closeable {
    private val context = ctx.applicationContext

    private val classifier: Classifier = TFLiteObjectDetectionHelper(context)

    /**
     * Stores the previous [OcrResult]. This must be full of valid values.
     */
    private var previousValidOcrResult: OcrResult? = null

    /**
     * Runs OCR on an image of the entire CRADLE screen and returns the classified result. It is
     * expected that the [imageOfCradleScreen] has no rotation and taken from portrait mode.
     *
     * For example, if the CRADLE VSA device screen looks like this:
     *      134 (systolic)
     *       86 (diastolic)
     *       67 (heartrate / PUL)
     * then the best result will be OcrResult(134, 86, 67).
     *
     * This can return null if none of the readings are valid. The [OcrResult]s returned by this
     * function have their values validated using [BloodPressure.isValueValid].
     */
    fun getResultsFromImage(
        imageOfCradleScreen: Bitmap,
        outputBitmapBlock: ((Bitmap, Bitmap, Bitmap) -> Unit)?
    ): OcrResult? {
        val sysBitmap = CradleOverlay.extractBitmapRegionFromCameraImage(
            imageOfCradleScreen,
            CradleOverlay.OverlayRegion.SYS
        )
        val diaBitmap = CradleOverlay.extractBitmapRegionFromCameraImage(
            imageOfCradleScreen,
            CradleOverlay.OverlayRegion.DIA
        )
        val heartRateBitmap = CradleOverlay.extractBitmapRegionFromCameraImage(
            imageOfCradleScreen,
            CradleOverlay.OverlayRegion.HR
        )
        // Submit bitmaps to UI if needed.
        outputBitmapBlock?.invoke(sysBitmap, diaBitmap, heartRateBitmap)

        val bitmaps = arrayMapOf(
            CradleOverlay.OverlayRegion.SYS to sysBitmap,
            CradleOverlay.OverlayRegion.DIA to diaBitmap,
            CradleOverlay.OverlayRegion.HR to heartRateBitmap
        )
        @Suppress("MagicNumber")
        val allRecognitions = ArrayMap<CradleOverlay.OverlayRegion, String>(3)
        for ((region, croppedBitmap) in bitmaps) {
            val recognitions = classifier.recognizeItemsInImage(croppedBitmap)
            val text = extractTextFromResults(recognitions, croppedBitmap.height.toFloat())
            allRecognitions[region] = text
        }
        val newOcrResult = OcrResult(
            systolic = allRecognitions[CradleOverlay.OverlayRegion.SYS]!!,
            diastolic = allRecognitions[CradleOverlay.OverlayRegion.DIA]!!,
            heartRate = allRecognitions[CradleOverlay.OverlayRegion.HR]!!,
        )

        return getValidOcrResult(newOcrResult)
            ?.also { previousValidOcrResult = it }
    }

    private val sortByXComparator: Comparator<Classifier.Recognition> =
        Comparator { r1, r2 -> sign(r1.location!!.centerX() - r2.location!!.centerX()).toInt() }

    /**
     * Interprets the OCR recognition results for one row of the CRADLE screen into a String.
     * The list of [results] contains a digit in each element.
     */
    private fun extractTextFromResults(
        results: List<Classifier.Recognition>,
        imageHeight: Float
    ): String {
        // FUTURE: Filter by aspect ratio as well?
        // REVISIT: Anything with confidence 20-50% likely means something is messed up; warn user?
        // Filter list of results by:
        // .. confidence %
        val processed: List<Classifier.Recognition> = results.filter {
            it.confidence >= MINIMUM_CONFIDENCE
        }

        val filteredToleranceSequence: Sequence<Classifier.Recognition> = if (processed.size > 1) {
            val actualMiddle = imageHeight / 2
            val yClosestToCentre = processed
                .minByOrNull {
                    // Find the smallest error
                    abs(actualMiddle - it.location!!.centerY())
                }
                ?.location?.centerY()
                ?: error("not possible")
            val tolerance = imageHeight * FILTER_RESULT_BY_CENTER_PERCENT

            // Filter only those that are within the tolerance of the digit that's closest
            // to the centre
            processed.asSequence()
                .filter {
                    val centerY = it.location!!.centerY()
                    yClosestToCentre - tolerance <= centerY && centerY <= yClosestToCentre + tolerance
                }
        } else {
            processed.asSequence()
        }

        // Extract text from all that's left (left to right)
        // Present the digits in a natural format by sorting them by the x-coordinate first.
        return filteredToleranceSequence
            .sortedWith(sortByXComparator)
            .joinToString(separator = "") { it.title }
    }

    /**
     * Validates the [newOcrResult] and only returns an OcrResult with valid results. For any
     * fields that are invalid, it will use the [previousValidOcrResult].
     * Returns null if there are no previous valid results and [newOcrResult] is invalid.
     */
    private fun getValidOcrResult(newOcrResult: OcrResult): OcrResult? {
        val isNewSystolicValid = BloodPressure.isValueValid(
            BloodPressure::systolic,
            newOcrResult.systolic.toIntOrNull(),
            context
        ) is Verifiable.Valid
        val isNewDiastolicValid = BloodPressure.isValueValid(
            BloodPressure::diastolic,
            newOcrResult.diastolic.toIntOrNull(),
            context
        ) is Verifiable.Valid
        val isNewHeartRateValid = BloodPressure.isValueValid(
            BloodPressure::heartRate,
            newOcrResult.heartRate.toIntOrNull(),
            context
        ) is Verifiable.Valid

        val lastOcrResult = previousValidOcrResult
        return if (lastOcrResult == null) {
            // For the first OCR result, only return a non-null result if all the values are valid.
            if (isNewSystolicValid && isNewDiastolicValid && isNewHeartRateValid) {
                newOcrResult
            } else {
                null
            }
        } else {
            // Only return valid values. Use the previous value if not valid.
            OcrResult(
                if (isNewSystolicValid) newOcrResult.systolic else lastOcrResult.systolic,
                if (isNewDiastolicValid) newOcrResult.diastolic else lastOcrResult.diastolic,
                if (isNewHeartRateValid) newOcrResult.heartRate else lastOcrResult.heartRate,
            )
        }
    }

    override fun close() {
        classifier.close()
    }
}

data class OcrResult(val systolic: String, val diastolic: String, val heartRate: String)
