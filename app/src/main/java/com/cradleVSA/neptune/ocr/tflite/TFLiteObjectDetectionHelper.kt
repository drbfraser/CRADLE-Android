package com.cradleVSA.neptune.ocr.tflite

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.os.Build
import android.util.Log
import androidx.annotation.GuardedBy
import androidx.collection.arrayMapOf
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.nnapi.NnApiDelegate
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeWithCropOrPadOp

private const val TAG = "TFLiteHelper"
private const val LABEL_FILE_NAME = "seven_seg_labelmap.txt"
private const val MODEL_FILENAME = "seven_seg_ssd.tflite"

// Only return this many results.
private const val NUM_DETECTIONS = 6

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

    private val inputImageBuffer: TensorImage
    private val inputImageProcessor: ImageProcessor
    private val inputImageSizeX: Int
    private val inputImageSizeY: Int
    init {
        interpreter!!.let { interpreter ->
            val inputTensor = interpreter.getInputTensor(0)
            inputTensor.shape().let { shape ->
                // {1, height, width, 3}
                inputImageSizeX = shape[2]
                inputImageSizeY = shape[1]
            }

            inputImageBuffer = TensorImage(inputTensor.dataType())
            inputImageProcessor = ImageProcessor.Builder()
                .add(ResizeWithCropOrPadOp(inputImageSizeX, inputImageSizeY))
                .build()

            val outputTensors = (0 until interpreter.outputTensorCount).map { interpreter.getOutputTensor(it) }
            val outputTensorInfo = outputTensors.map { outputTensor ->
                """name: ${outputTensor.name()}, shape: ${outputTensor.shape().toList()}, dataType: ${outputTensor.dataType()}"""
            }

            Log.d(TAG, """
                DEBUG: Tensor information:
                Output tensors:
                $outputTensorInfo
            """.trimIndent())
        }
    }

    private val labels: List<String> = FileUtil.loadLabels(context, LABEL_FILE_NAME)

    @Suppress("MagicNumber")
    override fun recognizeItemsInImage(bitmap: Bitmap): List<Classifier.Recognition> {
        val tfImageBuffer: TensorImage = inputImageBuffer.load(bitmap)
            .run { inputImageProcessor.process(inputImageBuffer) }

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

        val inputArray = arrayOf<Any>(tfImageBuffer.buffer)
        val outputMap: Map<Int, Any> = arrayMapOf(
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
                val top = locations[0]
                val left = locations[1]
                val bottom = locations[2]
                val right = locations[3]
                RectF(
                    left * inputImageSizeX,
                    top * inputImageSizeY,
                    right * inputImageSizeX,
                    bottom * inputImageSizeY
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
