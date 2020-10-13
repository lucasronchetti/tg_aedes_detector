package com.example.aedesdetector.ui.report_screen

import com.google.android.gms.maps.model.LatLng

interface ReportScreenContract {

    interface View{
        fun onSuccess()
        fun onError(message: String)
    }

    interface Presenter{
        fun uploadPinLocation(userLocation: LatLng?, reportLocation: LatLng, reportType: Int)
    }
}