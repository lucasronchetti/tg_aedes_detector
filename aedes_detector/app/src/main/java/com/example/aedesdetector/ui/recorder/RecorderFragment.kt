package com.example.aedesdetector.ui.recorder

import android.app.Activity
import android.content.ContentResolver
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProviders
import cafe.adriel.androidaudioconverter.AndroidAudioConverter
import cafe.adriel.androidaudioconverter.callback.IConvertCallback
import cafe.adriel.androidaudioconverter.model.AudioFormat
import com.example.aedesdetector.R
import com.example.aedesdetector.spec.MFCC
import java.io.File


class RecorderFragment : Fragment() {

    private val REQ_CODE_PICK_SOUNDFILE = 12345
    private lateinit var recorderViewModel: RecorderViewModel

    //current recorded audio
    private lateinit var currentAudio: ByteArray

    //audio converter
    private lateinit var audioConverterCallback: IConvertCallback


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

    fun ByteArray.toDoubleSamples() = mapPairsToDoubles{ a, b ->
        (a.toInt() and 0xFF or (b.toInt() shl 8)).toDouble()
    }

    inline fun ByteArray.mapPairsToDoubles(block: (Byte, Byte) -> Double)
            = DoubleArray(size / 2){ i -> block(this[2 * i], this[2 * i + 1]) }
}

