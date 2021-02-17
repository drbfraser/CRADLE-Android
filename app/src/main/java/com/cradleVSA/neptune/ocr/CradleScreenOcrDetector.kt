package com.cradleVSA.neptune.ocr

import android.content.Context
import android.graphics.Bitmap
import com.cradleVSA.neptune.ocr.tflite.Classifier
import com.cradleVSA.neptune.ocr.tflite.TFLiteObjectDetectionHelper
import java.util.ArrayList
import kotlin.math.abs
import kotlin.math.sign

// For filtering
private const val MINIMUM_CONFIDENCE = 0.5f
private const val FILTER_RESULT_BY_CENTER_PERCENT = 0.15f

/**
 * Handles getting digits from running OCR on a picture of the CRADLE screen.
 */
class CradleScreenOcrDetector(context: Context) {
    private val classifier: Classifier = TFLiteObjectDetectionHelper(context)

    /**
     * Runs OCR on an image of the entire CRADLE screen. It is expected that the
     * [imageOfCradleScreen] has no rotation and taken from portrait mode. The returned [Map]
     * contains a mapping of the row type to the predicted String of the row.
     *
     * For example, if the CRADLE VSA device screen looks like this:
     *      134 (systolic)
     *       86 (diastolic)
     *       67 (heartrate / PUL)
     * then the best result is that the returned map will be
     *      OverlayRegion.SYS -> "134"
     *      OverlayRegion.DIA -> "86"
     *      OverlayRegion.HR -> "67"
     */
    fun getResultsFromImage(imageOfCradleScreen: Bitmap): Map<CradleOverlay.OverlayRegion, String> {
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

        val bitmaps = mapOf(
            CradleOverlay.OverlayRegion.SYS to sysBitmap,
            CradleOverlay.OverlayRegion.DIA to diaBitmap,
            CradleOverlay.OverlayRegion.HR to heartRateBitmap
        )
        val allRecognitions = mutableMapOf<CradleOverlay.OverlayRegion, String>()
        for ((region, croppedBitmap) in bitmaps) {
            val recognitions = classifier.recognizeImage(croppedBitmap)
            val text = extractTextFromResults(recognitions, croppedBitmap.height.toFloat())
            allRecognitions[region] = text
        }
        return allRecognitions
    }

    private val sortByXComparator: java.util.Comparator<Classifier.Recognition> =
        Comparator { r1, r2 -> sign(r1.location!!.centerX() - r2.location!!.centerX()).toInt() }

    /**
     * Interprets the OCR recognition results for one row of the CRADLE screen into a String.
     */
    private fun extractTextFromResults(
        results: List<Classifier.Recognition>,
        imageHeight: Float
    ): String {
        // FUTURE: Filter by aspect ratio as well?
        // REVISIT: Anything with confidence 20-50% likely means something is messed up; warn user?
        val processed: MutableList<Classifier.Recognition> = ArrayList(results)

        // Filter list of results by:
        // .. confidence %
        processed.removeIf { it.confidence < MINIMUM_CONFIDENCE }
        if (processed.size > 1) {
            val actualMiddle = imageHeight / 2
            val yClosestToCentre = processed
                .minByOrNull {
                    // Find the smallest error
                    abs(actualMiddle - it.location!!.centerY())
                }
                ?.location?.centerY()
                ?: error("not possible")
            val tolerance = imageHeight * FILTER_RESULT_BY_CENTER_PERCENT

            // Filter only those that are within the tolerance of the smallest error
            processed.filter {
                val centerY = it.location!!.centerY()
                yClosestToCentre - tolerance <= centerY && centerY <= yClosestToCentre + tolerance
            }
        }

        // Extract text from all that's left (left to right)
        processed.sortWith(sortByXComparator)
        return processed.joinToString(separator = "") { it.title }
    }
}
