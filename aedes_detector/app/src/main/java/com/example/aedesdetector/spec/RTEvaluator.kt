package com.example.aedesdetector.spec

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.common.TensorProcessor
import org.tensorflow.lite.support.common.ops.NormalizeOp
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.lang.reflect.Array.getShort
import java.nio.ByteBuffer
import java.nio.MappedByteBuffer
import java.util.*
import kotlin.concurrent.schedule
import kotlin.experimental.and
import kotlin.experimental.or
import kotlin.math.abs
import kotlin.math.log10


object RTEvaluator {

    //Audio Recorder properties
    private val RECORDER_SAMPLERATE = 44100
    private val RECORDER_CHANNELS = AudioFormat.CHANNEL_IN_STEREO
    private val RECORDER_AUDIO_ENCODING = AudioFormat.ENCODING_PCM_16BIT
    private var audioData: ByteArray
    private lateinit var recorder: AudioRecord
    private var bufferSize = 0
    private var isRecording = false

    //Threads
    private lateinit var recordingThread: Thread
    private lateinit var spectrogramThread: Thread
    private lateinit var tensorFlowThread: Thread
    private var audioQueue: Queue<ByteArray>
    private var spectrogramQueue: Queue<FloatArray>

    //Timer
    private lateinit var timeoutTimerTask: TimerTask
    private val TIMEOUT_MILIS: Long = 15000

    //Application context
    private lateinit var mContext: Context

    //TensorFlow
    lateinit var inBuffer: TensorBuffer
    lateinit var tflite: Interpreter
    lateinit var imageShape: IntArray
    lateinit var probabilityShape: IntArray
    lateinit var probabilityDataType: DataType

    lateinit var onPositiveDetection: () -> Unit
    lateinit var onFinishingDetection: () -> Unit
    lateinit var onNegativeDetection: () -> Unit

    init {
        bufferSize = AudioRecord.getMinBufferSize(
            RECORDER_SAMPLERATE,
            RECORDER_CHANNELS,
            RECORDER_AUDIO_ENCODING
        ) * 3
        audioData = ByteArray(bufferSize)

        audioQueue = LinkedList<ByteArray>()
        spectrogramQueue = LinkedList<FloatArray>()
    }

    fun startRecording(context: Context, onPositive: () -> Unit, onFinishedRecording: () -> Unit, onNegative: () -> Unit) {
        mContext = context
        onPositiveDetection = onPositive
        onFinishingDetection = onFinishedRecording
        onNegativeDetection = onNegative
        loadTensorflow()
        recorder = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            RECORDER_SAMPLERATE,
            RECORDER_CHANNELS,
            RECORDER_AUDIO_ENCODING,
            bufferSize
        )
        val i = recorder.state
        if (i == 1) recorder.startRecording()
        isRecording = true
        timeoutTimerTask = Timer().schedule(TIMEOUT_MILIS) {
            stopRecording()
        }
        recordingThread = Thread(Runnable {
            while (!recordingThread.isInterrupted)
                saveDataToEvaluatorBuffer()
        }, "AudioRecorder Thread")
        spectrogramThread = Thread(Runnable {
            while (!spectrogramThread.isInterrupted)
                readDataFromBufferAndGenerateSpectrogram()
        }, "Spectrogram Thread")
        tensorFlowThread = Thread(Runnable {
            while (!tensorFlowThread.isInterrupted)
                fetchSpectrogramAndEvaluateWithTensorFlow()
        }, "TensorFlow Thread")
        recordingThread.start()
        spectrogramThread.start()
        tensorFlowThread.start()
    }

    fun stopRecording() {
        isRecording = false
        timeoutTimerTask.cancel()
        onFinishingDetection()
        val i = recorder.state
        if (i == 1) recorder.stop()
        recorder.release()
        recordingThread.interrupt()
    }

    private fun saveDataToEvaluatorBuffer() {
        val data = ByteArray(bufferSize)
        val read = recorder.read(data, 0, bufferSize)
        if (AudioRecord.ERROR_INVALID_OPERATION != read) {
            try {
                audioQueue.add(data)
            } catch (e: Exception) {

            }
        }
    }

    private fun readDataFromBufferAndGenerateSpectrogram() {
        try {
            val data = audioQueue.poll()
            if (data != null) {
                val convertedData = data.map { byte -> byte.toDouble() }.toDoubleArray()
                val mfccConvert = MFCC()
                val mfccInput = mfccConvert.processBulkSpectrograms(convertedData, 40)
                for (element in mfccInput) {
                    spectrogramQueue.add(flattenSpectrogram(element))
                }
            }
            else if (!isRecording && audioQueue.size <= 0) {
                spectrogramThread.interrupt()
            }
        } catch (e: Exception) {
            Log.d("EXCEPTION", e.toString())
        }
    }

    private fun fetchSpectrogramAndEvaluateWithTensorFlow() {
        try {
            val data = spectrogramQueue.poll()
            if (data != null) {
                val predictedResult = evaluateSample(data)
                if (predictedResult >= 0.9) {

                    stopRecording()
                    recordingThread.interrupt()
                    spectrogramThread.interrupt()
                    tensorFlowThread.interrupt()
                    onPositiveDetection()
                }
            }
            else if (!isRecording && audioQueue.size <= 0 && spectrogramQueue.size <= 0) {
                tensorFlowThread.interrupt()
                onNegativeDetection()
            }
        } catch (e: Exception) {
            Log.d("EXCEPTION", e.toString())
        }
    }

    private fun flattenSpectrogram(input: Array<FloatArray>): FloatArray {
        var output: ArrayList<kotlin.Float> = ArrayList<kotlin.Float>();
        input[0].indices.forEach { i ->
            input.indices.forEach { j ->
                output.add(input[j][i])
            }
        }
        return output.toFloatArray()
    }

    private fun loadTensorflow() {
        //obtain the input and output tensor size required by the model
        val tfliteModel: MappedByteBuffer =
            FileUtil.loadMappedFile(mContext, getModelPath())

        /** Options for configuring the Interpreter.  */
        val tfliteOptions =
            Interpreter.Options()
        tfliteOptions.setNumThreads(2)
        tflite = Interpreter(tfliteModel, tfliteOptions)

        //obtain the input and output tensor size required by the model
        val imageTensorIndex = 0
        imageShape = tflite.getInputTensor(imageTensorIndex).shape()
        val imageDataType: DataType = tflite.getInputTensor(imageTensorIndex).dataType()
        val probabilityTensorIndex = 0
        probabilityShape = tflite.getOutputTensor(probabilityTensorIndex).shape()
        probabilityDataType = tflite.getOutputTensor(probabilityTensorIndex).dataType()

        //need to transform the MFCC 1d float buffer into 1x60x40 dimension tensor using TensorBuffer
        inBuffer = TensorBuffer.createDynamic(imageDataType)
    }

    private fun evaluateSample(meanMFCCValues: FloatArray): kotlin.Float {
        inBuffer.loadArray(meanMFCCValues, imageShape)
        val inpBuffer: ByteBuffer = inBuffer.buffer
        val outputTensorBuffer: TensorBuffer =
            TensorBuffer.createFixedSize(probabilityShape, probabilityDataType)
        //run the predictions with input and output buffer tensors to get probability values
        tflite.run(inpBuffer, outputTensorBuffer.buffer)
        
        val result = outputTensorBuffer.floatArray.first()
        val secondResult = outputTensorBuffer.floatArray[1]
        if (result >= 0.9) {
            Log.d("DETECTED", result.toString())
            Log.d("ACCURACY", secondResult.toString())
            val probabilityProcessor: TensorProcessor = TensorProcessor.Builder()
                .add(NormalizeOp(0.0f, 255.0f)).build()

            val finalResult = probabilityProcessor.process(outputTensorBuffer)
            Log.d("BUFFER", finalResult.toString())
        }

        return result
    }

    fun getModelPath(): String {
        return "aedes_converted.tflite"
    }
}