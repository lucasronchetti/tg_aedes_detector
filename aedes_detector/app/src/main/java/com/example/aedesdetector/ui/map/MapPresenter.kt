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
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.GeoPoint
import com.google.firebase.firestore.Query

class MapPresenter(val mView: MapContract.View, val context: Context, val activity: FragmentActivity): MapContract.Presenter {

    private var isRecording = false

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
                startRecording()
            }
        } else {
            stopRecording()
        }
    }

    override fun fetchPinsWithMapLocation(mapLocation: LatLng, radius: Float) {
       val query = getNearestLocation(mapLocation.latitude, mapLocation.longitude, 10.0)

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

    private fun getNearestLocation(latitude: Double, longitude: Double, distance: Double): Query {
        // ~1 mile of lat and lon in degrees
        val lat = 0.0144927536231884
        val lon = 0.0181818181818182

        val lowerLat = latitude - (lat * distance)
        val lowerLon = longitude - (lon * distance)

        val greaterLat = latitude + (lat * distance)
        val greaterLon = longitude + (lon * distance)

        val lesserGeopoint = GeoPoint(lowerLat, lowerLon)
        val greaterGeopoint = GeoPoint(greaterLat, greaterLon)

        val docRef = FirebaseFirestore.getInstance().collection("pins")
        return docRef
            .whereGreaterThan("reportLocation", lesserGeopoint)
            .whereLessThan("reportLocation", greaterGeopoint)
    }

    private fun startRecording() {
        isRecording = true
        mView.setRecordingState()
        RTEvaluator.startRecording(context, {
           activity.runOnUiThread {
               isRecording = false
               mView.setStoppedState()
               mView.onDetectionPositive()
           }
        }, {
            activity.runOnUiThread {
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