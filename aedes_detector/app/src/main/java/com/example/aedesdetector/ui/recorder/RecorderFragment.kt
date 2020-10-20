package com.example.aedesdetector.ui.recorder

import android.Manifest
import android.app.Activity
import android.content.ContentResolver
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProviders
import cafe.adriel.androidaudioconverter.AndroidAudioConverter
import cafe.adriel.androidaudioconverter.callback.IConvertCallback
import cafe.adriel.androidaudioconverter.model.AudioFormat
import com.example.aedesdetector.R
import com.example.aedesdetector.spec.MFCC
import com.example.aedesdetector.spec.WavFile
import com.example.aedesdetector.spec.WavRecorder
import com.example.aedesdetector.ui.report_screen.ReportScreenActivity
import com.example.aedesdetector.utils.AlertUtils
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.common.TensorProcessor
import org.tensorflow.lite.support.common.ops.NormalizeOp
import org.tensorflow.lite.support.label.TensorLabel
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.io.*
import java.lang.Float.max
import java.net.URI
import java.nio.ByteBuffer
import java.nio.MappedByteBuffer


class RecorderFragment : Fragment() {

    private val REQ_CODE_PICK_SOUNDFILE = 12345
    private lateinit var recorderViewModel: RecorderViewModel

    //current recorded audio
    private lateinit var currentAudio: ByteArray

    //audio converter
    private lateinit var audioConverterCallback: IConvertCallback

    //audio recorder
    private lateinit var filePath: String
    private var bufferSizeInByte: Int = 0
    private val bufferElements2Rec = 1024 // want to play 2048 (2K) since 2 bytes we use only 1024
    private val bytesPerElement = 2 // 2 bytes in 16bit format
    private val RECORDER_SAMPLERATE: Int = 8000
    private val RECORDER_CHANNELS: Int = android.media.AudioFormat.CHANNEL_IN_STEREO
    private val RECORDER_AUDIO_ENCODING: Int = android.media.AudioFormat.ENCODING_PCM_16BIT
    private var recorder: AudioRecord? = null
    private var recordingThread: Thread? = null
    private var isRecording = false

    private lateinit var recordButton: Button

    private lateinit var positiveButton: Button
    private lateinit var negativeButton: Button

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View? {
        recorderViewModel =
                ViewModelProviders.of(this).get(RecorderViewModel::class.java)
        val root = inflater.inflate(R.layout.fragment_recorder, container, false)
        val openFileButton: Button = root.findViewById(R.id.open_file_button)
        openFileButton.setOnClickListener {
            val intent = Intent()
            intent.action = Intent.ACTION_GET_CONTENT
            intent.type = "audio/wav|audio/m4a|audio/mp3"
            startActivityForResult(
                Intent.createChooser(
                    intent,
                    getString(R.string.select_audio_file_title)
                ),
                REQ_CODE_PICK_SOUNDFILE
            )
        }

        recordButton = root.findViewById(R.id.record_button)
        recordButton.setOnClickListener {
            recordAudio()
        }

         audioConverterCallback = object : IConvertCallback {
            override fun onSuccess(convertedFile: File) {
                // audio converted!
                currentAudio = convertedFile.readBytes()
            }

            override fun onFailure(error: java.lang.Exception) {
                Log.d("ERROR", error.toString())
                // audio failed to convert
            }
        }

        return root
    }

    private fun recordAudio() {
        if (!isRecording) {
            if (ContextCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.RECORD_AUDIO
                ) != PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                val permissions = arrayOf(
                    android.Manifest.permission.RECORD_AUDIO,
                    android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    android.Manifest.permission.READ_EXTERNAL_STORAGE
                )
                ActivityCompat.requestPermissions(requireActivity(), permissions, 0)
            } else {
                startRecording()
            }
        }
        else {
            stopRecording()
        }
    }

    private fun startRecording() {
//        filePath = "${requireContext().cacheDir}/${System.currentTimeMillis()}.wav"
//        bufferSizeInByte = AudioRecord.getMinBufferSize(RECORDER_SAMPLERATE, RECORDER_CHANNELS, RECORDER_AUDIO_ENCODING)
//
//        recorder = AudioRecord(
//            MediaRecorder.AudioSource.MIC,
//            RECORDER_SAMPLERATE, RECORDER_CHANNELS,
//            RECORDER_AUDIO_ENCODING, bufferElements2Rec * bytesPerElement
//        )
//
//        recorder!!.startRecording()
        isRecording = true
        recordButton.setText("GRAVANDO")
//        recordingThread = Thread(Runnable { writeAudioDataToFile() }, "AudioRecorder Thread")
//        recordingThread!!.start()
        WavRecorder.getInstance().startRecording(requireContext())
    }

    private fun writeAudioDataToFile() {
        // Write the output audio in byte
        val sData = ByteArray(bufferSizeInByte)
        var os: FileOutputStream? = null
        try {
            os = FileOutputStream(filePath)
        } catch (e: FileNotFoundException) {
            e.printStackTrace()
        }
        while (isRecording) {
            // gets the voice output from microphone to byte format
            val length = recorder!!.read(sData, 0, bufferSizeInByte)
            println("Short writing to file$sData")
            try {
                // // writes the data to file from buffer
                // // stores the voice buffer
                val bData: ByteArray = sData
                os?.write(bData, 0, length)
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
        try {
            os?.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private fun stopRecording() {
        val fileName = WavRecorder.getInstance().stopRecording()
        recordButton.setText("GRAVAR")
        isRecording = false
        Log.d("FILENAME", fileName)
        try {
                val audioBytes = File(fileName).inputStream()
                if (audioBytes != null) {
                    val result = extractFeaturesAndRunEvaluation(audioBytes)
                    if (result) {
                        positiveAedesIdentification()
                    }
                    else {
                        negativeAedesIdentification()
                    }
                }
        }
        catch(e: Exception) {
            Log.d("EXCEPTION", e.localizedMessage)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_CODE_PICK_SOUNDFILE && resultCode == Activity.RESULT_OK) {
            if (data != null) {
                try {
                    val uri = data.data
                    if (uri != null) {
                        val contentResolver: ContentResolver = this.context!!.contentResolver
                        val audioType = uri?.let { contentResolver.getType(it) }
                        Log.d("FILETYPE", audioType)
                        //convert here?
                        //audio/mp4
                        if (audioType == "audio/wav" || audioType == "audio/x-wav") {
                            //supported format
                            val audioBytes = uri?.let { contentResolver.openInputStream(it) }
                            val result = extractFeaturesAndRunEvaluation(audioBytes)
                            if (result) {
                                positiveAedesIdentification()
                            }
                            else {
                                negativeAedesIdentification()
                            }
                            return
                        }
                        else {
                            //unsuported file format, will try to convert
                            val audioFile = File(uri.path)
                            AndroidAudioConverter.with(this.context!!).setFile(audioFile).setFormat(
                                AudioFormat.WAV).setCallback(audioConverterCallback).convert()

                        }
                    }
                    else {
                        //null uri
                        Log.d("EXCEPTION", "Null URI")
                    }

                }
                catch (e: Exception) {
                    Log.d("EXCEPTION", e.toString())
                }
            }
        }
    }

    private fun extractFeaturesAndRunEvaluation(audioBytes: InputStream?): Boolean {
        try {
            val mNumFrames: Int
            val mSampleRate: Int
            val mChannels: Int

            var predictedResult: Float = 0.0F

            var wavFile: WavFile? = null
            wavFile = WavFile.openWavFile(audioBytes)
            mNumFrames = wavFile.numFrames.toInt()
            mChannels = wavFile.numChannels
            val buffer = Array(mChannels) { DoubleArray(mNumFrames) }
            wavFile.readFrames(buffer, mNumFrames, 0)
            val mfccConvert = MFCC()

            for (channel in buffer) {
                val mfccInput = mfccConvert.processBulkSpectrograms(channel, 40)

                for (element in mfccInput) {
                    val flattenedSpec = flattenSpectrogram(element)
                    predictedResult =
                        max(loadModelAndMakePredictions(flattenedSpec), predictedResult)
                }
            }
            Log.d("MFCC R", predictedResult.toString())
            Log.d("MFCC", "Finished")
            return predictedResult > 0.95
        } catch (e: Exception) {
            Log.d("EXCEPTION", e.localizedMessage)
            return false
        }
        return false
    }

    private fun flattenSpectrogram(input: Array<FloatArray>): FloatArray {
        var output: ArrayList<Float> = ArrayList<Float>();
        input[0].indices.forEach { i ->
            input.indices.forEach{ j ->
                output.add(input[j][i])
            }
        }
        return output.toFloatArray()
    }

    private fun positiveAedesIdentification() {
        AlertUtils.displayAlert(requireContext(),
            "Aedes!",
            "Identificamos um Aedes aegypti. Gostaria de compartilhar no mapa?",
            "Sim",
            DialogInterface.OnClickListener { dialog, id ->
                // User clicked OK button
                startActivity(Intent(requireContext(), ReportScreenActivity::class.java))
            },
            "Não",
            DialogInterface.OnClickListener { dialog, id ->
                // User clicked OK button
            })
    }

    private fun negativeAedesIdentification() {
        AlertUtils.displayAlert(requireContext(),
            "Não é Aedes!",
            "Não identificamos um aedes na sua gravação.",
            "Ok",
            DialogInterface.OnClickListener { dialog, id ->
                // User clicked OK button
            },
            null,
            null)
    }

    fun ByteArray.toDoubleSamples() = mapPairsToDoubles{ a, b ->
        (a.toInt() and 0xFF or (b.toInt() shl 8)).toDouble()
    }

    inline fun ByteArray.mapPairsToDoubles(block: (Byte, Byte) -> Double)
            = DoubleArray(size / 2){ i -> block(this[2 * i], this[2 * i + 1]) }

    protected fun loadModelAndMakePredictions(meanMFCCValues : FloatArray) : Float {
        //load the TFLite model in 'MappedByteBuffer' format using TF Interpreter
        val tfliteModel: MappedByteBuffer =
            FileUtil.loadMappedFile(requireContext(), getModelPath())
        val tflite: Interpreter

        /** Options for configuring the Interpreter.  */
        val tfliteOptions =
            Interpreter.Options()
        tfliteOptions.setNumThreads(2)
        tflite = Interpreter(tfliteModel, tfliteOptions)

        //obtain the input and output tensor size required by the model
        //for urban sound classification, input tensor should be of 1x40x1x1 shape
        val imageTensorIndex = 0
        val imageShape =
            tflite.getInputTensor(imageTensorIndex).shape()
        val imageDataType: DataType = tflite.getInputTensor(imageTensorIndex).dataType()
        val probabilityTensorIndex = 0
        val probabilityShape =
            tflite.getOutputTensor(probabilityTensorIndex).shape()
        val probabilityDataType: DataType =
            tflite.getOutputTensor(probabilityTensorIndex).dataType()

        //need to transform the MFCC 1d float buffer into 1x40x1x1 dimension tensor using TensorBuffer
        val inBuffer: TensorBuffer = TensorBuffer.createDynamic(imageDataType)
        inBuffer.loadArray(meanMFCCValues, imageShape)
        val inpBuffer: ByteBuffer = inBuffer.buffer
        val outputTensorBuffer: TensorBuffer =
            TensorBuffer.createFixedSize(probabilityShape, probabilityDataType)
        //run the predictions with input and output buffer tensors to get probability values across the labels
        tflite.run(inpBuffer, outputTensorBuffer.buffer)

        val result = outputTensorBuffer.floatArray.first()
        val secondResult = outputTensorBuffer.floatArray[1]
            Log.d("RESULT", "")
            Log.d("0", result.toString())
            Log.d("1", secondResult.toString())

        return result

    }

    fun getModelPath(): String {
        return "aedes_converted.tflite"
    }
}

