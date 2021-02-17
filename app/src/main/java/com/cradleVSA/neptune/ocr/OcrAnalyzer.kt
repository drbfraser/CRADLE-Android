package com.cradleVSA.neptune.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.RectF
import android.view.Surface
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.cradleVSA.neptune.ocr.tflite.Classifier.Recognition
import com.cradleVSA.neptune.utilitiles.TFImageUtils
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.nnapi.NnApiDelegate
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.common.ops.NormalizeOp
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import java.util.ArrayList
import java.util.Comparator
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
private const val FILTER_RESULT_BY_CENTER_PERCENT = 0.15f

class OcrAnalyzer @Throws(OcrNotAvailableException::class) constructor(
    context: Context,
    modelFileName: String,
    private val onAnalysisFinished: (Map<CradleOverlay.OverlayRegion, String>) -> Unit,
    private val debugBitmapBlock: (Bitmap, Bitmap, Bitmap) -> Unit,
    private val debugPrintBlock: (String) -> Unit
) : ImageAnalysis.Analyzer {
    private val interpreter: Interpreter = Interpreter(
        FileUtil.loadMappedFile(context, modelFileName),
        Interpreter.Options().addDelegate(NnApiDelegate())
    )

    private val labels: List<String> = FileUtil.loadLabels(context, LABEL_FILE_NAME)

    private val imageProcessor: ImageProcessor by lazy {
        ImageProcessor.Builder()
            .add(
                ResizeOp(
                    NN_INPUT_SIZE,
                    NN_INPUT_SIZE,
                    ResizeOp.ResizeMethod.NEAREST_NEIGHBOR
                )
            )
            .add(NormalizeOp(0f, 1f))
            .build()
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
            debugBitmapBlock(sysBitmap, diaBitmap, heartRateBitmap)

            val bitmaps = mapOf(
                CradleOverlay.OverlayRegion.OVERLAY_REGION_SYS to sysBitmap,
                CradleOverlay.OverlayRegion.OVERLAY_REGION_DIA to diaBitmap,
                CradleOverlay.OverlayRegion.OVERLAY_REGION_HR to heartRateBitmap
            )
            val allRecognitions = mutableMapOf<CradleOverlay.OverlayRegion, String>()
            for ((region, croppedBitmap) in bitmaps) {
                // https://www.tensorflow.org/lite/inference_with_metadata/lite_support
                val tfImageBuffer = TensorImage(DataType.UINT8)
                val tfImage = imageProcessor.process(tfImageBuffer.apply { load(croppedBitmap) })

                // Copy the input data into TensorFlow.
                val outputLocations = arrayOf(Array(NUM_DETECTIONS) { FloatArray(4) })
                val outputClasses = arrayOf(FloatArray(NUM_DETECTIONS))
                val outputScores = arrayOf(FloatArray(NUM_DETECTIONS))
                val numDetections = FloatArray(1)

                val inputArray = arrayOf<Any>(tfImage.buffer)
                val outputMap = mapOf(
                    0 to outputLocations,
                    1 to outputClasses,
                    2 to outputScores,
                    3 to numDetections
                )

                // Run TFLite
                interpreter.runForMultipleInputsOutputs(inputArray, outputMap)

                // Show the best detections.
                // after scaling them back to the input size.
                val recognitions = (0 until NUM_DETECTIONS).map { i ->
                    val detection = outputLocations[0][i].let { locations ->
                        // The locations are an array of [0, 1] floats for
                        // [top, left, bottom, right]
                        RectF(
                            locations[1] * NN_INPUT_SIZE,
                            locations[0] * NN_INPUT_SIZE,
                            locations[3] * NN_INPUT_SIZE,
                            locations[2] * NN_INPUT_SIZE
                        )
                    }
                    // SSD Mobilenet V1 Model assumes class 0 is background class
                    // in label file and class labels start from 1 to number_of_classes+1,
                    // while outputClasses correspond to class index from 0 to number_of_classes
                    val labelOffset = 1
                    val title = try {
                        labels[outputClasses[0][i].toInt() + labelOffset]
                    } catch (e: IndexOutOfBoundsException) {
                        ""
                    }

                    Recognition(
                        "$i",
                        title,
                        outputScores[0][i],
                        detection
                    )
                }

                debugPrintBlock("Recognitions for $region: $recognitions")
                val text = extractTextFromResults(recognitions, croppedBitmap.height.toFloat())
                allRecognitions[region] = text
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

    private val sortByXComparator: Comparator<Recognition> =
        Comparator { r1, r2 -> sign(r1.location!!.centerX() - r2.location!!.centerX()).toInt() }

    /*
        Results to Text
     */
    private fun extractTextFromResults(results: List<Recognition>, imageHeight: Float): String {
        // FUTURE: Filter by aspect ratio as well?
        // REVISIT: Anything with confidence 20-50% likely means something is messed up; warn user?
        val processed: MutableList<Recognition> = ArrayList(results)

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

class OcrNotAvailableException(override val message: String?): Exception(message)
