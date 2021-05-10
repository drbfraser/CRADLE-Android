@file:Suppress("MagicNumber")
package com.cradleVSA.neptune.ocr.tflite

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.os.Build
import androidx.annotation.GuardedBy
import androidx.collection.arrayMapOf
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.nnapi.NnApiDelegate
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeWithCropOrPadOp
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.nio.ByteBuffer

private const val TAG = "TFLiteHelper"
private const val LABEL_FILE_NAME = "seven_seg_labelmap.txt"
private const val MODEL_FILENAME = "seven_seg_ssd.tflite"

/**
 * Helper for using TFLite. This class interprets the results from the [interpreter]
 * into [Classifier.Recognition] objects.
 *
 * References:
 * - https://github.com/tensorflow/models/tree/master/research/object_detection
 * - https://github.com/android/camera-samples/blob/main/CameraXTfLite
 * - https://github.com/tensorflow/examples/tree/master/lite/examples/image_classification/android
 * - https://github.com/tensorflow/examples/blob/84d53a9bc37b95079d0cd9d247ba0e3d1ebbf538/lite/examples/image_classification/android/lib_support/src/main/java/org/tensorflow/lite/examples/classification/tflite/Classifier.java
 *
 * Note: This class is **not** thread-safe.
 */
class TFLiteObjectDetectionHelper(context: Context) : Classifier {
    /**
     * Use Android Neural Network API if available. TensorFlow recommends using this for Android
     * Pie or above. https://www.tensorflow.org/lite/performance/nnapi
     */
    @GuardedBy("this")
    private var nnApiDelegate: NnApiDelegate? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            NnApiDelegate()
        } else {
            null
        }

    @GuardedBy("this")
    private var interpreter: Interpreter?
    private val inputImageBuffer: TensorImage
    private val inputImageProcessor: ImageProcessor
    private val inputImageSizeX: Int
    private val inputImageSizeY: Int

    private val numDetections: Int

    /**
     * Array of shape [batchsize, numDetections, 4].
     * Contains the location of detected boxes. The locations are an array of floats in the
     * closed interval [0, 1] corresponding to {top, left, bottom, right}.
     *
     * Note: The implementation of this array is flattened: Instead of being a
     * float[numDetections][4] (numDetections by 4) matrix, it's flattened so that every group of 4
     * entries corresponds to a detection. The [getTop], [getLeft], [getBottom], [getRight] methods
     * handles this abstraction.
     */
    @JvmInline
    private value class LocationTensorBuffer(val tensorBuffer: TensorBuffer) {
        /** Returns the data buffer of the underlying [TensorBuffer] */
        val buffer: ByteBuffer get() = tensorBuffer.buffer

        fun getTop(detectionIndex: Int) = tensorBuffer.getFloatValue(4 * detectionIndex + 0)
        fun getLeft(detectionIndex: Int) = tensorBuffer.getFloatValue(4 * detectionIndex + 1)
        fun getBottom(detectionIndex: Int) = tensorBuffer.getFloatValue(4 * detectionIndex + 2)
        fun getRight(detectionIndex: Int) = tensorBuffer.getFloatValue(4 * detectionIndex + 3)
    }
    private val outputLocationBuffer: LocationTensorBuffer

    /**
     * Array of shape [batchsize, numDetections]
     * Contains the classes of detected boxes
     */
    private val outputClassesBuffer: TensorBuffer

    /**
     * Array of shape [batchsize, numDetections].
     * Contains the scores / confidence of detected boxes.
     */
    private val outputScoresBuffer: TensorBuffer

    /**
     * Array of shape [ batchsize ]. This contains the number of detected boxes
     */
    private val outputNumDetectionsBuffer: TensorBuffer
    init {
        val interpreter = Interpreter(
            FileUtil.loadMappedFile(context, MODEL_FILENAME),
            Interpreter.Options().apply {
                nnApiDelegate?.let { addDelegate(it) }
            }
        )
        this.interpreter = interpreter

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

        require(interpreter.outputTensorCount == 4) { "unexpected TFLite model" }

        interpreter.getOutputTensor(0).let {
            outputLocationBuffer = LocationTensorBuffer(
                TensorBuffer.createFixedSize(it.shape(), it.dataType())
            )
        }
        interpreter.getOutputTensor(1).let {
            outputClassesBuffer = TensorBuffer.createFixedSize(it.shape(), it.dataType())
            numDetections = it.shape()[1]
        }
        interpreter.getOutputTensor(2).let {
            outputScoresBuffer = TensorBuffer.createFixedSize(it.shape(), it.dataType())
        }
        interpreter.getOutputTensor(3).let {
            outputNumDetectionsBuffer = TensorBuffer.createFixedSize(it.shape(), it.dataType())
        }

        require(outputLocationBuffer.tensorBuffer.flatSize == numDetections * 4) {
            "location buffer flatsize needs to be numDetections * 4 since each detection " +
                "should have 4 rectangle coordinates each"
        }
    }

    private val labels: List<String> = FileUtil.loadLabels(context, LABEL_FILE_NAME)

    override fun recognizeItemsInImage(bitmap: Bitmap): List<Classifier.Recognition> {
        val tfImageBuffer: TensorImage = inputImageBuffer.load(bitmap)
            .run { inputImageProcessor.process(inputImageBuffer) }

        // Copy the input data into TensorFlow.
        val inputArray = arrayOf<Any>(tfImageBuffer.buffer)
        val outputMap: Map<Int, Any> = arrayMapOf(
            0 to outputLocationBuffer.buffer.rewind(),
            1 to outputClassesBuffer.buffer.rewind(),
            2 to outputScoresBuffer.buffer.rewind(),
            3 to outputNumDetectionsBuffer.buffer.rewind()
        )

        // Run TFLite, but don't do anything if it's closed
        synchronized(this) {
            interpreter?.runForMultipleInputsOutputs(inputArray, outputMap) ?: return emptyList()
        }

        // Show the detections after scaling them back to the input size.
        return (0 until numDetections).map { i ->
            // SSD Mobilenet V1 Model assumes class 0 is background class
            // in label file and class labels start from 1 to number_of_classes+1,
            // while outputClasses correspond to class index from 0 to number_of_classes
            val labelOffset = 1
            val title = try {
                labels[outputClassesBuffer.getFloatValue(i).toInt() + labelOffset]
            } catch (e: IndexOutOfBoundsException) {
                ""
            }

            val scores = outputScoresBuffer.getFloatValue(i)

            val location = outputLocationBuffer.let {
                RectF(
                    it.getLeft(detectionIndex = i) * inputImageSizeX,
                    it.getTop(detectionIndex = i) * inputImageSizeY,
                    it.getRight(detectionIndex = i) * inputImageSizeX,
                    it.getBottom(detectionIndex = i) * inputImageSizeY
                )
            }

            Classifier.Recognition("$i", title, scores, location)
        }
    }

    override fun close() {
        synchronized(this) {
            interpreter?.close()
            interpreter = null
            nnApiDelegate?.close()
            nnApiDelegate = null
        }
    }
}
