package com.cradleVSA.neptune.ocr.tflite

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.nnapi.NnApiDelegate
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.common.ops.NormalizeOp
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp

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
    private val interpreter: Interpreter = Interpreter(
        FileUtil.loadMappedFile(context, MODEL_FILENAME),
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

    @Suppress("MagicNumber")
    override fun recognizeImage(bitmap: Bitmap): List<Classifier.Recognition> {
        // This preprocesses the image as a tensor in ByteBuffer form.
        // https://www.tensorflow.org/lite/inference_with_metadata/lite_support
        val tfImageBuffer = TensorImage(DataType.UINT8).apply { load(bitmap) }
        val tfImage = imageProcessor.process(tfImageBuffer)

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

        val inputArray = arrayOf<Any>(tfImage.buffer)
        val outputMap = mapOf(
            0 to outputLocations,
            1 to outputClasses,
            2 to outputScores,
            3 to numDetections
        )

        // Run TFLite
        interpreter.runForMultipleInputsOutputs(inputArray, outputMap)

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
}
