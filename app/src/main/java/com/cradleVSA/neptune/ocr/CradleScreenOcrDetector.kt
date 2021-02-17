package com.cradleVSA.neptune.ocr

import android.content.Context
import android.graphics.Bitmap
import com.cradleVSA.neptune.ocr.tflite.Classifier
import com.cradleVSA.neptune.ocr.tflite.TFLiteObjectDetectionHelper
import java.io.Closeable
import java.util.ArrayList
import java.util.Collections
import kotlin.math.abs
import kotlin.math.sign

// For filtering
private const val MINIMUM_CONFIDENCE = 0.5f
private const val FILTER_RESULT_BY_CENTER_PERCENT = 0.15f

/**
 * Handles getting digits from running OCR on a picture of the CRADLE screen.
 */
class CradleScreenOcrDetector(context: Context) : Closeable {
    private val classifier: Classifier = TFLiteObjectDetectionHelper(context)

    /**
     * Runs OCR on an image of the entire CRADLE screen and returns the classified result. It is
     * expected that the [imageOfCradleScreen] has no rotation and taken from portrait mode.
     *
     * For example, if the CRADLE VSA device screen looks like this:
     *      134 (systolic)
     *       86 (diastolic)
     *       67 (heartrate / PUL)
     * then the bes0t result is that the returned map will be
     *      OverlayRegion.SYS -> "134"
     *      OverlayRegion.DIA -> "86"
     *      OverlayRegion.HR -> "67"
     */
    fun getResultsFromImage(
        imageOfCradleScreen: Bitmap,
        debugBitmapBlock: (Bitmap, Bitmap, Bitmap) -> Unit
    ): OcrResult {
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
        debugBitmapBlock(sysBitmap, diaBitmap, heartRateBitmap)

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
        return OcrResult(
            systolic = allRecognitions[CradleOverlay.OverlayRegion.SYS]!!,
            diastolic = allRecognitions[CradleOverlay.OverlayRegion.DIA]!!,
            heartRate = allRecognitions[CradleOverlay.OverlayRegion.HR]!!,
        )
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

    @Suppress("MagicNumber")
    private fun extractTextFromResultsOld(
        results: List<Classifier.Recognition>,
        imageHeight: Float
    ): String {
        // FUTURE: Filter by aspect ratio as well?
        // REVISIT: Anything with confidence 20-50% likely means something is messed up; warn user?
        val sortByX: java.util.Comparator<in Classifier.Recognition?> =
            java.util.Comparator<Classifier.Recognition?> { r1, r2 ->
                Math.signum(r1.location!!.centerX() - r2.location!!.centerX())
                    .toInt()
            }
        val processed: ArrayList<Classifier.Recognition> = ArrayList(results)

        // Filter list of results by:
        // .. confidence %
        for (i in processed.indices.reversed()) {
            val result = processed[i]
            if (result.confidence < MINIMUM_CONFIDENCE) {
                processed.removeAt(i)
            }
        }

        // .. height of regions must be "on same line"
        if (processed.size > 1) {
//            Collections.sort(processed, sortByY);
//            Classifier.Recognition middleResult = processed.get(processed.size() / 2);
//            float medianY = middleResult.getLocation().centerY();
//
            var yClosestToCentre = 0f
            var smallestError = 9999f
            val actualMiddle = imageHeight / 2
            for (result in processed) {
                val error: Float = Math.abs(actualMiddle - result.location!!.centerY())
                if (error < smallestError) {
                    yClosestToCentre = result.location!!.centerY()
                    smallestError = error
                }
            }
            val tolerance: Float = imageHeight * FILTER_RESULT_BY_CENTER_PERCENT
            for (i in processed.indices.reversed()) {
                val result = processed[i]
                val centerY: Float = result.location!!.centerY()
                if (centerY > yClosestToCentre + tolerance || centerY < yClosestToCentre - tolerance) {
                    processed.removeAt(i)
                }
            }
        }

        // Extract text from all that's left (left to right)
        Collections.sort(processed, sortByX)
        var text = ""
        for (result in processed) {
            text += result.title
        }
        return text
    }

    override fun close() {
        classifier.close()
    }
}

data class OcrResult(val systolic: String, val diastolic: String, val heartRate: String)
