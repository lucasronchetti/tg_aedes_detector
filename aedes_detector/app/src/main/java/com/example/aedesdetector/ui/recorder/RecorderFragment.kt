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
import com.example.aedesdetector.utils.AlertUtils
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException


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
    private val RECORDER_CHANNELS: Int = android.media.AudioFormat.CHANNEL_IN_MONO
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

        positiveButton = root.findViewById(R.id.positive_action_button)
        positiveButton.setOnClickListener {
            positiveAedesIdentification()
        }

        negativeButton = root.findViewById(R.id.negative_action_button)
        negativeButton.setOnClickListener {
            negativeAedesIdentification()
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

        filePath = "${requireContext().cacheDir}/${System.currentTimeMillis()}.pcm"
        bufferSizeInByte = AudioRecord.getMinBufferSize(RECORDER_SAMPLERATE, RECORDER_CHANNELS, RECORDER_AUDIO_ENCODING)

        recorder = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            RECORDER_SAMPLERATE, RECORDER_CHANNELS,
            RECORDER_AUDIO_ENCODING, bufferElements2Rec * bytesPerElement
        )

        recorder!!.startRecording()
        isRecording = true
        recordButton.setText("GRAVANDO")
        recordingThread = Thread(Runnable { writeAudioDataToFile() }, "AudioRecorder Thread")
        recordingThread!!.start()
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
        if (recorder != null) {
            recordButton.setText("GRAVAR")
            isRecording = false
            recorder?.stop()
            recorder?.release()
            recorder = null
            recordingThread = null
            try {
                val contentResolver: ContentResolver = this.context!!.contentResolver
                val audioBytes = contentResolver.openInputStream(Uri.parse(filePath))
                if (audioBytes != null) {
                    currentAudio = audioBytes.readBytes()
                    val mfccConvert = MFCC()
                    val audioDoubleSample = currentAudio.toDoubleSamples()
                    val mfccInput = mfccConvert.processSpectrogram(audioDoubleSample)
                    Log.d("MFCC", mfccInput.toString())
                }
                else {
                    Log.d("ERROR", "Null audio!")
                }
            }
            catch(e: Exception) {
                Log.d("EXCEPTION", e.localizedMessage)
            }
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
                            if (audioBytes != null) {
                                currentAudio = audioBytes.readBytes()
                                val mfccConvert = MFCC()
                                val audioDoubleSample = currentAudio.toDoubleSamples()
                                val mfccInput = mfccConvert.processSpectrogram(audioDoubleSample)
                                Log.d("MFCC", mfccInput.toString())
                            }
                            else {
                                Log.d("ERROR", "Null audio!")
                            }
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

    private fun positiveAedesIdentification() {
        AlertUtils.displayAlert(requireContext(),
            "Aedes!",
            "Identificamos um Aedes aegypti. Gostaria de compartilhar no mapa?",
            "Sim",
            DialogInterface.OnClickListener { dialog, id ->
                // User clicked OK button
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
}

