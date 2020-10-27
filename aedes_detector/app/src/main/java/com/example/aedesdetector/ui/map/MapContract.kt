package com.example.aedesdetector.ui.map

import com.example.aedesdetector.models.UserReport
import com.google.android.gms.maps.model.LatLng

interface MapContract {

    interface View{
        fun onPinsFetch(pinArray: ArrayList<UserReport>)
        fun onErrorFetchingPins(message: String)
        fun setRecordingState()
        fun setFinishingState()
        fun setStoppedState()
        fun onDetectionPositive()
        fun onDetectionNegative()
    }

    interface Presenter{
        fun fetchPinsWithMapLocation(mapLocation: LatLng, radius: Float)
        fun recordAudio()
    }
}