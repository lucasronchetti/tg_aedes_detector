package com.example.aedesdetector.ui.map

import android.content.Context
import com.example.aedesdetector.models.UserReport
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.GeoPoint
import com.google.firebase.firestore.Query

class MapPresenter(val mView: MapContract.View, val context: Context): MapContract.Presenter {
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
}