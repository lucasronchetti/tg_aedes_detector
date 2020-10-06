package com.example.aedesdetector.ui.recorder

import android.R.attr
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProviders
import com.example.aedesdetector.R


class RecorderFragment : Fragment() {

    private val REQ_CODE_PICK_SOUNDFILE = 12345
    private lateinit var recorderViewModel: RecorderViewModel

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

        return root
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_CODE_PICK_SOUNDFILE && resultCode == Activity.RESULT_OK) {
            if (data != null) {
                val uri = data.toUri(Intent.URI_INTENT_SCHEME)
            }
        }
    }
}
