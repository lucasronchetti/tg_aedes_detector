package com.example.aedesdetector.models

import com.google.firebase.Timestamp
import com.google.firebase.firestore.GeoPoint
import com.google.firebase.firestore.QueryDocumentSnapshot
import java.util.*

open class UserReport() {

    var latitude: Double = 0.0
    var longitude: Double = 0.0
    var reportDate: Date = Date()
    var pinKey: String = ""

    constructor(document: QueryDocumentSnapshot) : this() {
        val location = document["reportLocation"] as GeoPoint
        val reportDateTimestamp = document["reportDate"] as Timestamp
            pinKey = document.id
            latitude = location.latitude
            longitude = location.longitude
            reportDate = reportDateTimestamp.toDate()

    }
}