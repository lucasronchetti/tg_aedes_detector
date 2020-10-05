package com.example.aedesdetector.ui.recorder

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class RecorderViewModel : ViewModel() {

    private val _text = MutableLiveData<String>().apply {
        value = "Gravador aqui"
    }
    val text: LiveData<String> = _text
}