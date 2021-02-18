package com.cradleVSA.neptune.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.view.Surface
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.cradleVSA.neptune.utilitiles.TFImageUtils
import java.io.Closeable

/**
 * For use with the CameraX API to analyze images for OCR extraction of CRADLE device VSA readings.
 *
 */
class OcrAnalyzer constructor(
    someContext: Context,
    private val onAnalysisFinished: (OcrResult) -> Unit,
    private val submitSysDiaPulImagesBlock: (Bitmap, Bitmap, Bitmap) -> Unit,
) : ImageAnalysis.Analyzer, Closeable {
    private val context = someContext.applicationContext

    private val cradleOcrDetector = CradleScreenOcrDetector(context)

    private var previousOcrResult: OcrResult? = null

    override fun analyze(image: ImageProxy) {
        image.use { imageProxy ->
            // ImageAnalysis uses YUV_420_888 format
            val rgbBytes = convertYUVPlanesToARGB8888(imageProxy)
            val readyImage = createNonRotatedImage(imageProxy, rgbBytes)

            cradleOcrDetector.getResultsFromImage(readyImage, submitSysDiaPulImagesBlock)?.let {
                onAnalysisFinished(it)
            }
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

    @Suppress("MagicNumber")
    private fun convertYUVPlanesToARGB8888(
        image: ImageProxy
    ): IntArray {
        val planes = image.planes
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

    override fun close() {
        cradleOcrDetector.close()
    }
}
