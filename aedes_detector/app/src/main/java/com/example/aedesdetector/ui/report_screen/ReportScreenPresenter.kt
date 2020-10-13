package com.example.aedesdetector.ui.report_screen

import android.content.Context
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.GeoPoint
import java.util.*

class ReportScreenPresenter(val mView: ReportScreenContract.View, val context: Context):ReportScreenContract.Presenter  {

    override fun uploadPinLocation(userLocation: LatLng?, reportLocation: LatLng, reportType: Int) {
        //Uploading to firebase here!
        var realUserLocation = userLocation
        if (realUserLocation == null) {
            realUserLocation = LatLng(0.0,0.0)
        }

        val mFirestore = FirebaseFirestore.getInstance()

//        val userLocationMap = hashMapOf(
//            realUserLocation.latitude to "latitude",
//            realUserLocation.longitude to "longitude"
//        )
//
//        val reportLocationMap = hashMapOf(
//            reportLocation.latitude to "latitude",
//            reportLocation.longitude to "longitude"
//        )
//
//        val userReport = hashMapOf(
//            userLocationMap to "userLocation",
//            reportLocationMap to "reportLocation"
//        )

        val userReport = hashMapOf(
            "userLocation" to GeoPoint(realUserLocation.latitude, realUserLocation.longitude),
            "reportLocation" to GeoPoint(reportLocation.latitude, reportLocation.longitude),
            "reportDate" to Timestamp(Date()),
            "reportType" to reportType
        )

        mFirestore.collection("pins").add(userReport).addOnSuccessListener {
            mView.onSuccess()
        }.addOnFailureListener {
            mView.onError(it.localizedMessage)
        }

    }
}