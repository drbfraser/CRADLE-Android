package com.cradleVSA.neptune.ocr

import android.content.Context
import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.RectF
import android.util.Log
import android.view.Surface
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.cradleVSA.neptune.ocr.tflite.Classifier.Recognition
import com.cradleVSA.neptune.utilitiles.TFImageUtils
import org.tensorflow.lite.Interpreter
import java.io.BufferedReader
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStreamReader
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.util.ArrayList
import java.util.Collections
import java.util.Comparator
import java.util.Vector
import kotlin.math.abs
import kotlin.math.sign

// Float model
private const val IMAGE_MEAN = 128.0f
private const val IMAGE_STD = 128.0f
private const val NN_INPUT_SIZE = 200

// Only return this many results.
private const val NUM_DETECTIONS = 6 //10;

private const val QUANTIZED_MODEL = true

private const val TAG = "OcrAnalyzer"
private const val LABEL_FILE_NAME = "seven_seg_labelmap.txt"

private const val MINIMUM_CONFIDENCE = 0.5f

class OcrAnalyzer @Throws(OcrNotAvailableException::class) constructor(
    context: Context,
    modelFileName: String,
    private val onAnalysisFinished: (Map<CradleOverlay.OverlayRegion, String>) -> Unit,
    private val debugBitmapBlock: (Bitmap, Bitmap, Bitmap) -> Unit,
    private val debugPrintBlock: (String) -> Unit
) : ImageAnalysis.Analyzer {
    private val interpreter: Interpreter
    init {
        interpreter = Interpreter(loadModelFile(context.assets, modelFileName))
    }

    /**
     * Memory-map the model file in Assets.
     */
    @Throws(IOException::class)
    private fun loadModelFile(assets: AssetManager, modelFilename: String): ByteBuffer {
        val fileDescriptor = assets.openFd(modelFilename)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
            .asReadOnlyBuffer()
    }

    private val labels = Vector<String>()
    init {
        val inputStreamForLabels = context.assets.open(LABEL_FILE_NAME, AssetManager.ACCESS_BUFFER)
        BufferedReader(InputStreamReader(inputStreamForLabels)).use { bufferedReader ->
            var line: String? = bufferedReader.readLine()
            while (line != null) {
                labels.add(line)
                line = bufferedReader.readLine()
            }
        }
    }

    override fun analyze(image: ImageProxy) {
        image.use { imageProxy ->
            // ImageAnalysis uses YUV_420_888 format

            val planes = image.planes
            val yuvBytes = arrayOfNulls<ByteArray>(3)
            fillBytes(planes, yuvBytes)
            val yRowStride = planes[0].rowStride
            val uvRowStride = planes[1].rowStride
            val uvPixelStride = planes[1].pixelStride

            val rgbBytes = IntArray(imageProxy.height * imageProxy.width)
            TFImageUtils.convertYUV420ToARGB8888(
                yuvBytes[0],
                yuvBytes[1],
                yuvBytes[2],
                imageProxy.width,
                imageProxy.height,
                yRowStride,
                uvRowStride,
                uvPixelStride,
                rgbBytes
            )

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
            val bitmap = imageProxy.imageInfo.rotationDegrees.let { rotationDegrees ->
                if (rotationDegrees != Surface.ROTATION_0) {
                    preBitmap.rotate(imageProxy.imageInfo.rotationDegrees)
                        .also { preBitmap.recycle() }
                } else {
                    preBitmap
                }
            }

            val sysBitmap = CradleOverlay.extractBitmapRegionFromCameraImage(
                bitmap,
                CradleOverlay.OverlayRegion.OVERLAY_REGION_SYS
            )
            debugPrintBlock("Rotation degrees is ${imageProxy.imageInfo.rotationDegrees}")

            val diaBitmap = CradleOverlay.extractBitmapRegionFromCameraImage(
                bitmap,
                CradleOverlay.OverlayRegion.OVERLAY_REGION_DIA
            )
            val heartRateBitmap = CradleOverlay.extractBitmapRegionFromCameraImage(
                bitmap,
                CradleOverlay.OverlayRegion.OVERLAY_REGION_HR
            )
            bitmap.recycle()

            debugBitmapBlock(sysBitmap, diaBitmap, heartRateBitmap)

            // Pre-allocate buffers.
            val numBytesPerChannel = if (QUANTIZED_MODEL) {
                1 // Quantized
            } else {
                4 // Floating point
            }

            val bitmaps = mapOf(
                CradleOverlay.OverlayRegion.OVERLAY_REGION_SYS to sysBitmap,
                CradleOverlay.OverlayRegion.OVERLAY_REGION_DIA to diaBitmap,
                CradleOverlay.OverlayRegion.OVERLAY_REGION_HR to heartRateBitmap
            )
            val allRecognitions = mutableMapOf<CradleOverlay.OverlayRegion, String>()
            for ((region, croppedBitmap) in bitmaps) {
                val inputImageData = normalizeRGBData(numBytesPerChannel, croppedBitmap)

                // Trace.endSection(); // preprocessBitmap

                // Copy the input data into TensorFlow.
                // Trace.beginSection("feed");
                val outputLocations = Array(1) { Array(NUM_DETECTIONS) { FloatArray(4) } }
                val outputClasses = Array(1) { FloatArray(NUM_DETECTIONS) }
                val outputScores = Array(1) { FloatArray(NUM_DETECTIONS) }
                val numDetections = FloatArray(1)

                val inputArray = arrayOf<Any>(inputImageData)
                val outputMap: MutableMap<Int, Any> = hashMapOf(
                    0 to outputLocations,
                    1 to outputClasses,
                    2 to outputScores,
                    3 to numDetections
                )

                // Run TFLite
                interpreter.runForMultipleInputsOutputs(inputArray, outputMap)

                //    Trace.endSection();

                // Show the best detections.
                // after scaling them back to the input size.
                val recognitions = ArrayList<Recognition>(NUM_DETECTIONS)
                for (i in 0 until NUM_DETECTIONS) {
                    val detection = RectF(
                        outputLocations[0][i][1] * NN_INPUT_SIZE,
                        outputLocations[0][i][0] * NN_INPUT_SIZE,
                        outputLocations[0][i][3] * NN_INPUT_SIZE,
                        outputLocations[0][i][2] * NN_INPUT_SIZE
                    )
                    // SSD Mobilenet V1 Model assumes class 0 is background class
                    // in label file and class labels start from 1 to number_of_classes+1,
                    // while outputClasses correspond to class index from 0 to number_of_classes
                    val labelOffset = 1
                    val title = try {
                        labels[outputClasses[0][i].toInt() + labelOffset]
                    } catch (e: IndexOutOfBoundsException) {
                        ""
                    }
                    recognitions.add(Recognition("$i", title, outputScores[0][i], detection))
                }

                debugPrintBlock("Recognitions for $region: $recognitions")
                val text = extractTextFromResults(recognitions, croppedBitmap.height.toFloat())
                allRecognitions[region] = text
                //    Trace.endSection(); // "recognizeImage"
                //    Trace.endSection(); // "recognizeImage"
            }

            onAnalysisFinished(allRecognitions)
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

    private fun normalizeRGBData(
        numBytesPerChannel: Int,
        bitmap: Bitmap,
    ): ByteBuffer {

        val frameToCropTransform = TFImageUtils.getTransformationMatrix(
            bitmap.width, bitmap.height,
            NN_INPUT_SIZE, NN_INPUT_SIZE,
            0,
            true
        )

        val resizedImage = Bitmap.createBitmap(
            NN_INPUT_SIZE,
            NN_INPUT_SIZE,
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(resizedImage)
        canvas.drawBitmap(bitmap, frameToCropTransform, null)

        Log.d(
            TAG,
            "Resized image has dimensions (wxh) ${resizedImage.width} x ${resizedImage.height}"
        )

        val imageData = IntArray(NN_INPUT_SIZE * NN_INPUT_SIZE).also {
            resizedImage.getPixels(
                it,
                0,
                resizedImage.width,
                0,
                0,
                resizedImage.width,
                resizedImage.height
            )
        }

        // Pre-process the image data from 0-255 int to normalized float based
        // on the provided parameters.
        val inputImageData = ByteBuffer.allocateDirect(
            NN_INPUT_SIZE * NN_INPUT_SIZE * 3 * numBytesPerChannel
        )
        for (i in 0 until NN_INPUT_SIZE) {
            for (j in 0 until NN_INPUT_SIZE) {
                val pixelValue: Int = imageData[i * NN_INPUT_SIZE + j]
                inputImageData.apply {
                    if (QUANTIZED_MODEL) {
                        // Quantized model
                        put((pixelValue shr 16 and 0xFF).toByte())
                        put((pixelValue shr 8 and 0xFF).toByte())
                        put((pixelValue and 0xFF).toByte())
                    } else {
                        // Float model
                        putFloat(((pixelValue shr 16 and 0xFF) - IMAGE_MEAN) / IMAGE_STD)
                        putFloat(((pixelValue shr 8 and 0xFF) - IMAGE_MEAN) / IMAGE_STD)
                        putFloat(((pixelValue and 0xFF) - IMAGE_MEAN) / IMAGE_STD)
                    }
                }
            }
        }

        return inputImageData
    }

    /*
        Results to Text
     */
    private fun extractTextFromResults(results: List<Recognition>, imageHeight: Float): String {
        // FUTURE: Filter by aspect ratio as well?
        // REVISIT: Anything with confidence 20-50% likely means something is messed up; warn user?
        val sortByX: Comparator<Recognition> =
            Comparator { r1, r2 -> sign(r1.location.centerX() - r2.location.centerX()).toInt() }
        val processed: MutableList<Recognition> = ArrayList(results)
        debugPrintBlock("Original size of processed is ${processed.size}")

        // Filter list of results by:
        // .. confidence %
        for (i in processed.indices.reversed()) {
            val result = processed[i]
            if (result.confidence < MINIMUM_CONFIDENCE) {
                processed.removeAt(i)
            }
        }
        debugPrintBlock("Size of processed now is ${processed.size}")

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
                val error = abs(actualMiddle - result.location.centerY())
                if (error < smallestError) {
                    yClosestToCentre = result.location.centerY()
                    smallestError = error
                }
            }
            val tolerance = imageHeight * OcrDigitDetector.FILTER_RESULT_BY_CENTER_PERCENT
            for (i in processed.indices.reversed()) {
                val result = processed[i]
                val centerY = result.location.centerY()
                if (centerY > yClosestToCentre + tolerance || centerY < yClosestToCentre - tolerance) {
                    processed.removeAt(i)
                }
            }
        }

        // Extract text from all that's left (left to right)
        Collections.sort(processed, sortByX)
        return processed.joinToString { it.title }
    }
}

class OcrNotAvailableException(override val message: String?): Exception(message)
