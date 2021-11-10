package com.soloupis.sample.photos_with_depth.fragments.segmentation

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import com.soloupis.sample.photos_with_depth.utils.ImageUtils
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.io.IOException
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

data class ModelExecutionResult(
    val styledImage: Bitmap,
    val preProcessTime: Long = 0L,
    val stylePredictTime: Long = 0L,
    val styleTransferTime: Long = 0L,
    val postProcessTime: Long = 0L,
    val totalExecutionTime: Long = 0L,
    val executionLog: String = "",
    val errorMessage: String = ""
)

@SuppressWarnings("GoodTime")
class DepthAndStyleModelExecutor(
    context: Context,
    private var useGPU: Boolean = true
) {

    private var numberThreads = 7
    private var fullExecutionTime = 0L
    private var preProcessTime = 0L
    private var findPotraitTime = 0L
    private var styleTransferTime = 0L
    private var postProcessTime = 0L
    private var interpreterPortrait: Interpreter
    //private lateinit var gpuDelegate: GpuDelegate

    companion object {
        private const val TAG = "PhotosWithDepthProcedure"
        private const val CONTENT_IMAGE_SIZE = 512
        private const val DEPTH_MODEL = "portrait_dr_quant.tflite"
    }

    init {

        interpreterPortrait = getInterpreter(context, DEPTH_MODEL, useGPU)

    }

    // Function for ML Binding
    fun executeProcedureForPhotosWithDepth(
        contentImage: Bitmap,
        context: Context
    ): Pair<Bitmap, Bitmap> {
        try {
            Log.i(TAG, "running models")
            fullExecutionTime = SystemClock.uptimeMillis()

            // Creates inputs for reference.
            // This model expects a 1,3,512,512 input so it is impossible to use Support Library and byteBuffer
            // So we go with plain array inputs and outputs

            preProcessTime = SystemClock.uptimeMillis()

            //var loadedBitmap = ImageUtils.loadBitmapFromResources(context, "woman.png")
            val loadedBitmap = Bitmap.createScaledBitmap(
                contentImage,
                CONTENT_IMAGE_SIZE,
                CONTENT_IMAGE_SIZE,
                true
            )

            // Convert Bitmap to Float array
            val inputStyle = ImageUtils.bitmapToFloatArray(loadedBitmap)
            //Log.i(TAG, inputStyle[0][0][0].contentToString())

            // Use below if you like to proceed withrunForMultipleInputsOutputs
            // Create an output array with size 1,1,512,512
            /*val output1 =
                Array(1) { Array(1) { Array(CONTENT_IMAGE_SIZE) { FloatArray(CONTENT_IMAGE_SIZE) } } }
            val output2 =
                Array(1) { Array(1) { Array(CONTENT_IMAGE_SIZE) { FloatArray(CONTENT_IMAGE_SIZE) } } }
            val output3 =
                Array(1) { Array(1) { Array(CONTENT_IMAGE_SIZE) { FloatArray(CONTENT_IMAGE_SIZE) } } }
            val output4 =
                Array(1) { Array(1) { Array(CONTENT_IMAGE_SIZE) { FloatArray(CONTENT_IMAGE_SIZE) } } }
            val output5 =
                Array(1) { Array(1) { Array(CONTENT_IMAGE_SIZE) { FloatArray(CONTENT_IMAGE_SIZE) } } }
            val output6 =
                Array(1) { Array(1) { Array(CONTENT_IMAGE_SIZE) { FloatArray(CONTENT_IMAGE_SIZE) } } }
            val output7 =
                Array(1) { Array(1) { Array(CONTENT_IMAGE_SIZE) { FloatArray(CONTENT_IMAGE_SIZE) } } }

            val outputs: MutableMap<Int,
                    Any> = HashMap()
            outputs[0] = output1
            outputs[1] = output2
            outputs[2] = output3
            outputs[3] = output4
            outputs[4] = output5
            outputs[5] = output6
            outputs[6] = output7
            preProcessTime = SystemClock.uptimeMillis() - preProcessTime
            Log.d(TAG, "Pre process time: $preProcessTime")

            // Runs model inference and gets result.
            findPotraitTime = SystemClock.uptimeMillis()
            val array = arrayOf(inputStyle)
            interpreterPortrait.runForMultipleInputsOutputs(array, outputs)*/

            // Use below with runSignature
            preProcessTime = SystemClock.uptimeMillis() - preProcessTime
            val signatures = interpreterPortrait.signatureKeys
            val output = Array(1) { Array(1) { Array(CONTENT_IMAGE_SIZE) { FloatArray(CONTENT_IMAGE_SIZE) } } }
            // Values from python code interpreter.get_signatures_list()
            // Python output = {'serving_default': {'inputs': ['input'], 'outputs': ['1884', '1885', '1886', '1887', '1888', '1889', 'output']}}
            val inputs: MutableMap<String, Any> = HashMap()
            inputs["input"] = inputStyle
            val outputs: MutableMap<String, Any> = HashMap()
            // We use the last array for output
            outputs["output"] = output

            findPotraitTime = SystemClock.uptimeMillis()
            interpreterPortrait.runSignature(
                inputs, outputs, signatures[0]
            )

            //Log.d(TAG, "Output array: " + outputs[0][0][0].contentToString())
            findPotraitTime = SystemClock.uptimeMillis() - findPotraitTime
            Log.d(TAG, "Find depth time: $findPotraitTime")

            // Post process time
            postProcessTime = SystemClock.uptimeMillis()

            // Convert output array to Bitmap
            // For code with runForMultipleInputsOutputs use output7 //////////
            val (finalBitmapGrey, finalBitmapBlack) = ImageUtils.convertArrayToBitmap(
                output, CONTENT_IMAGE_SIZE,
                CONTENT_IMAGE_SIZE
            )
            postProcessTime = SystemClock.uptimeMillis() - postProcessTime
            Log.d(TAG, "Post process time: $postProcessTime")

            // Full execution time
            fullExecutionTime = SystemClock.uptimeMillis() - fullExecutionTime
            Log.d(TAG, "Time to run everything: $fullExecutionTime")

            // Return grayscale image (model output) to show this on screen and a useless bitmap
            return Pair(
                finalBitmapGrey,
                finalBitmapBlack
            )
        } catch (e: Exception) {
            val exceptionLog = "something went wrong: ${e.message}"
            Log.e("EXECUTOR", exceptionLog)

            val emptyBitmap =
                ImageUtils.createEmptyBitmap(
                    CONTENT_IMAGE_SIZE,
                    CONTENT_IMAGE_SIZE
                )
            return Pair(emptyBitmap, emptyBitmap)
        }
    }

    @Throws(IOException::class)
    private fun loadModelFile(context: Context, modelFile: String): MappedByteBuffer {
        val fileDescriptor = context.assets.openFd(modelFile)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        val retFile = fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
        fileDescriptor.close()
        return retFile
    }

    @Throws(IOException::class)
    private fun getInterpreter(
        context: Context,
        modelName: String,
        useGpu: Boolean
    ): Interpreter {
        val tfliteOptions = Interpreter.Options()
        if (useGpu) {
            //gpuDelegate = GpuDelegate()
            //tfliteOptions.addDelegate(gpuDelegate)

            // Create the Delegate instance.
            /*try {
                gpuDelegate = HexagonDelegate(context)
                tfliteOptions.addDelegate(gpuDelegate)
            } catch (e: Exception) {
                // Hexagon delegate is not supported on this device.
                Log.e("HEXAGON", e.toString())
            }*/

            //val delegate =
            //GpuDelegate(GpuDelegate.Options().setQuantizedModelsAllowed(true))
        }

        tfliteOptions.setNumThreads(numberThreads)
        //tfliteOptions.setUseXNNPACK(true)
        return Interpreter(loadModelFile(context, modelName), tfliteOptions)
        //return Interpreter(context.assets.openFd(DEPTH_MODEL),tfliteOptions)
    }

    private fun formatExecutionLog(): String {
        val sb = StringBuilder()
        sb.append("Input Image Size: $CONTENT_IMAGE_SIZE x $CONTENT_IMAGE_SIZE\n")
        sb.append("GPU enabled: $useGPU\n")
        sb.append("Number of threads: $numberThreads\n")
        sb.append("Pre-process execution time: $preProcessTime ms\n")
        sb.append("Predicting style execution time: $findPotraitTime ms\n")
        sb.append("Transferring style execution time: $styleTransferTime ms\n")
        sb.append("Post-process execution time: $postProcessTime ms\n")
        sb.append("Full execution time: $fullExecutionTime ms\n")
        return sb.toString()
    }

    fun close() {
        interpreterPortrait.close()
    }
}
