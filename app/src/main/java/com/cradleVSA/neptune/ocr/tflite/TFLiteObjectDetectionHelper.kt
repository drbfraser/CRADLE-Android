package com.cradleVSA.neptune.ocr.tflite

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.RectF
import android.os.Build
import androidx.annotation.GuardedBy
import com.cradleVSA.neptune.utilitiles.TFImageUtils
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.nnapi.NnApiDelegate
import org.tensorflow.lite.support.common.FileUtil
import java.nio.ByteBuffer
import java.nio.ByteOrder

private const val TAG = "TFLiteHelper"
private const val LABEL_FILE_NAME = "seven_seg_labelmap.txt"
private const val MODEL_FILENAME = "seven_seg_ssd.tflite"

// Only return this many results.
private const val NUM_DETECTIONS = 6 // 10;

private const val NN_INPUT_SIZE = 200
private const val QUANTIZED_MODEL = true

// Constants for float model
private const val IMAGE_MEAN = 128.0f
private const val IMAGE_STD = 128.0f

/**
 * Helper for using TFLite
 *
 * ref:
 * - github.com/tensorflow/models/tree/master/research/object_detection
 * - https://github.com/android/camera-samples/blob/main/CameraXTfLite
 * - https://github.com/tensorflow/examples/tree/master/lite/examples/image_classification/android
 */
class TFLiteObjectDetectionHelper(context: Context) : Classifier {
    /**
     * Use Android Neural Network API if available. TensorFlow recommends using this for Android
     * Pie or above. https://www.tensorflow.org/lite/performance/nnapi
     */
    private val nnApiDelegate: NnApiDelegate? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            NnApiDelegate()
        } else {
            null
        }

    private val interpreterLock = Object()

    @GuardedBy("interpreterLock")
    private var interpreter: Interpreter? =
        Interpreter(
            FileUtil.loadMappedFile(context, MODEL_FILENAME),
            Interpreter.Options().apply {
                nnApiDelegate?.let { addDelegate(it) }
            }
        )


    private val labels: List<String> = FileUtil.loadLabels(context, LABEL_FILE_NAME)

    /**
     * Creates an input tensor from the given [bitmap] for use in an [Interpreter], which uses
     * tensors in the form of [ByteBuffer]s.
     */
    @Suppress("MagicNumber")
    private fun createTensorFromImage(
        bitmap: Bitmap,
    ): ByteBuffer {
        val frameToCropTransform = TFImageUtils.getTransformationMatrix(
            bitmap.width,
            bitmap.height,
            NN_INPUT_SIZE,
            NN_INPUT_SIZE,
            0,
            true
        )

        val resizedImage = Bitmap.createBitmap(
            NN_INPUT_SIZE,
            NN_INPUT_SIZE,
            Bitmap.Config.ARGB_8888
        )
        Canvas(resizedImage).drawBitmap(bitmap, frameToCropTransform, null)

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

        // Quantized uses 1 byte per channel; floating point uses 4 bytes per channel
        val numBytesPerChannel = if (QUANTIZED_MODEL) 1 else 4

        val inputImageData = ByteBuffer.allocateDirect(
            NN_INPUT_SIZE * NN_INPUT_SIZE * 3 * numBytesPerChannel
        ).apply {
            order(ByteOrder.nativeOrder())
        }
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
                        // Pre-process the image data from 0-255 int to normalized float based
                        // on the provided parameters.
                        putFloat(((pixelValue shr 16 and 0xFF) - IMAGE_MEAN) / IMAGE_STD)
                        putFloat(((pixelValue shr 8 and 0xFF) - IMAGE_MEAN) / IMAGE_STD)
                        putFloat(((pixelValue and 0xFF) - IMAGE_MEAN) / IMAGE_STD)
                    }
                }
            }
        }

        return inputImageData
    }

    @Suppress("MagicNumber")
    override fun recognizeImage(bitmap: Bitmap): List<Classifier.Recognition> {
        val tfImageBuffer: ByteBuffer = createTensorFromImage(bitmap)

        // Copy the input data into TensorFlow.
        // Array of shape [Batchsize, NUM_DETECTIONS,4]. Contains the location of detected boxes
        val outputLocations = arrayOf(Array(NUM_DETECTIONS) { FloatArray(4) })
        // Array of shape [Batchsize, NUM_DETECTIONS]
        // contains the classes of detected boxes
        val outputClasses = arrayOf(FloatArray(NUM_DETECTIONS))
        // Array of shape [Batchsize, NUM_DETECTIONS]. Contains the scores / confidence of detected
        // boxes
        val outputScores = arrayOf(FloatArray(NUM_DETECTIONS))
        // numDetections: array of shape [Batchsize]. This contains the number of detected boxes
        val numDetections = FloatArray(1)

        val inputArray = arrayOf<Any>(tfImageBuffer)
        val outputMap = mapOf(
            0 to outputLocations,
            1 to outputClasses,
            2 to outputScores,
            3 to numDetections
        )

        // Run TFLite, but don't do anything if it's closed
        synchronized(interpreterLock) {
            interpreter?.runForMultipleInputsOutputs(inputArray, outputMap) ?: return emptyList()
        }

        // Show the best detections after scaling them back to the input size.
        return (0 until NUM_DETECTIONS).map { i ->
            // SSD Mobilenet V1 Model assumes class 0 is background class
            // in label file and class labels start from 1 to number_of_classes+1,
            // while outputClasses correspond to class index from 0 to number_of_classes
            val labelOffset = 1
            val title = try {
                labels[outputClasses[0][i].toInt() + labelOffset]
            } catch (e: IndexOutOfBoundsException) {
                ""
            }

            val scores = outputScores[0][i]

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

            Classifier.Recognition("$i", title, scores, detection)
        }
    }

    override fun close() {
        synchronized(interpreterLock) {
            interpreter?.close()
            interpreter = null
            nnApiDelegate?.close()
        }
    }
}
