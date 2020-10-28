package com.example.aedesdetector.ui.map

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.example.aedesdetector.models.UserReport
import com.example.aedesdetector.spec.RTEvaluator
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.GeoPoint
import com.google.firebase.firestore.Query

class MapPresenter(val mView: MapContract.View, val context: Context, val activity: FragmentActivity): MapContract.Presenter {

    private var isRecording = false
    private var isProcessing = false

    override fun recordAudio() {
        if (!isRecording) {
            if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.RECORD_AUDIO
                ) != PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                val permissions = arrayOf(
                    Manifest.permission.RECORD_AUDIO,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                )
                ActivityCompat.requestPermissions(activity, permissions, 0)
            } else {
                if (!isProcessing) {
                    startRecording()
                }
            }
        } else {
            stopRecording()
        }
    }

    override fun fetchPinsWithMapLocation(mapLocation: LatLngBounds) {

        if (mapLocation.center.latitude == 0.0 && mapLocation.center.longitude == 0.0) {
            return
        }

       val query = getNearestLocation(mapLocation.southwest, mapLocation.northeast)

        query.get().addOnSuccessListener { pins ->
            var pinArray = ArrayList<UserReport>()
            for (pin in pins) {
                pinArray.add(UserReport(pin))
            }
            mView.onPinsFetch(pinArray)
        }.addOnFailureListener {
            mView.onErrorFetchingPins(it.localizedMessage)
        }
    }

    private fun getNearestLocation(southWest: LatLng, northEast: LatLng): Query {
        val lesserGeopoint = GeoPoint(southWest.latitude, southWest.longitude)
        val greaterGeopoint = GeoPoint(northEast.latitude, northEast.longitude)

        val docRef = FirebaseFirestore.getInstance().collection("pins")
        return docRef
            .whereGreaterThan("reportLocation", lesserGeopoint)
            .whereLessThan("reportLocation", greaterGeopoint)
    }

    private fun startRecording() {
        isRecording = true
        isProcessing = true
        mView.setRecordingState()
        RTEvaluator.startRecording(context, {
           activity.runOnUiThread {
               isRecording = false
               isProcessing = false
               mView.setStoppedState()
               mView.onDetectionPositive()
           }
        }, {
            activity.runOnUiThread {
                if (isProcessing) {
                    mView.setFinishingState()
                }
            }
        }
            ,{
            activity.runOnUiThread {
                isProcessing = false
                isRecording = false
                mView.setStoppedState()
                mView.onDetectionNegative()
            }
        }
        )
    }

    private fun stopRecording() {
        isRecording = false
        mView.setStoppedState()
        RTEvaluator.stopRecording()
        //val fileName = WavRecorder.getInstance().stopRecording()
        //Log.d("FILENAME", fileName)
        try {
            //val audioBytes = File(fileName).inputStream()
            //readAudioBytesAndEvaluate(audioBytes)
        } catch (e: Exception) {
            Log.d("EXCEPTION", e.toString())
        }
    }

}