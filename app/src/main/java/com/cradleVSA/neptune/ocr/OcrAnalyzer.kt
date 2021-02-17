package com.cradleVSA.neptune.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.view.Surface
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.cradleVSA.neptune.model.BloodPressure
import com.cradleVSA.neptune.utilitiles.TFImageUtils

class OcrAnalyzer constructor(
    someContext: Context,
    private val onAnalysisFinished: (OcrResult) -> Unit,
    private val debugBitmapBlock: (Bitmap, Bitmap, Bitmap) -> Unit,
    private val debugPrintBlock: (String) -> Unit,
) : ImageAnalysis.Analyzer {
    private val context = someContext.applicationContext

    private val cradleOcrDetector = CradleScreenOcrDetector(context)

    private var previousOcrResult: OcrResult? = null

    override fun analyze(image: ImageProxy) {
        image.use { imageProxy ->
            // ImageAnalysis uses YUV_420_888 format
            val rgbBytes = convertYUVPlanesToARGB8888(imageProxy)
            val readyImage = createNonRotatedImage(imageProxy, rgbBytes)

            getValidOcrResult(
                cradleOcrDetector.getResultsFromImage(readyImage, debugBitmapBlock)
            )?.let {
                onAnalysisFinished(it)
                previousOcrResult = it
            }
        }
    }

    /**
     * Validates the [newOcrResult] and only returns an OcrResult with valid results. For any
     * fields that are invalid, it will use the previous field value for the [OcrResult].
     */
    private fun getValidOcrResult(newOcrResult: OcrResult): OcrResult? {
        val isNewSystolicValid = BloodPressure.isValueValid(
            BloodPressure::systolic,
            newOcrResult.systolic.toIntOrNull(),
            context
        ).first
        val isNewDiastolicValid = BloodPressure.isValueValid(
            BloodPressure::diastolic,
            newOcrResult.diastolic.toIntOrNull(),
            context
        ).first
        val isNewHeartRateValid = BloodPressure.isValueValid(
            BloodPressure::heartRate,
            newOcrResult.heartRate.toIntOrNull(),
            context
        ).first

        val lastOcrResult = previousOcrResult
        return if (lastOcrResult == null) {
            if (isNewSystolicValid && isNewDiastolicValid && isNewHeartRateValid) {
                newOcrResult
            } else {
                null
            }
        } else {
            OcrResult(
                if (isNewSystolicValid) newOcrResult.systolic else lastOcrResult.systolic,
                if (isNewDiastolicValid) newOcrResult.diastolic else lastOcrResult.diastolic,
                if (isNewHeartRateValid) newOcrResult.heartRate else lastOcrResult.heartRate,
            )
        }
    }

    private fun createNonRotatedImage(
        imageProxy: ImageProxy,
        rgbBytes: IntArray
    ): Bitmap {
        val preBitmap = Bitmap.createBitmap(
            imageProxy.width,
            imageProxy.height,
            Bitmap.Config.ARGB_8888
        )
        preBitmap.setPixels(
            rgbBytes,
            0,
            imageProxy.width,
            0,
            0,
            imageProxy.width,
            imageProxy.height
        )

        // This is the rotation needed to transform the image to the correct orientation.
        // It is a clockwise rotation in degrees that needs to be applied to the image buffer.
        val rotationDegrees = imageProxy.imageInfo.rotationDegrees
        return if (rotationDegrees == Surface.ROTATION_0) {
            preBitmap
        } else {
            preBitmap.rotate(rotationDegrees).also { preBitmap.recycle() }
        }
    }

    private fun Bitmap.rotate(degrees: Number): Bitmap {
        return Bitmap.createBitmap(
            this,
            0,
            0,
            width,
            height,
            Matrix().apply { postRotate(degrees.toFloat()) },
            true
        )
    }

    private fun convertYUVPlanesToARGB8888(
        image: ImageProxy
    ): IntArray {
        val planes = image.planes
        @Suppress("MagicNumber")
        val yuvBytes = arrayOfNulls<ByteArray>(3)
        fillBytes(planes, yuvBytes)
        val yRowStride = planes[0].rowStride
        val uvRowStride = planes[1].rowStride
        val uvPixelStride = planes[1].pixelStride

        val rgbBytes = IntArray(image.height * image.width)
        TFImageUtils.convertYUV420ToARGB8888(
            yuvBytes[0],
            yuvBytes[1],
            yuvBytes[2],
            image.width,
            image.height,
            yRowStride,
            uvRowStride,
            uvPixelStride,
            rgbBytes
        )
        return rgbBytes
    }

    /**
     * Fills into [yuvBytes] the bytes from the [planes] (YUV_420_888 format).
     * Ref: TFLite Image Classification Demo app: https://github.com/tensorflow/examples
     */
    private fun fillBytes(planes: Array<ImageProxy.PlaneProxy>, yuvBytes: Array<ByteArray?>) {
        // Because of the variable row stride it's not possible to know in
        // advance the actual necessary dimensions of the YUV planes.
        for (i in planes.indices) {
            val buffer = planes[i].buffer
            val currentYuvBytes = yuvBytes[i]
                ?: ByteArray(buffer.capacity()).also { yuvBytes[i] = it }
            buffer.get(currentYuvBytes)
        }
    }
}
