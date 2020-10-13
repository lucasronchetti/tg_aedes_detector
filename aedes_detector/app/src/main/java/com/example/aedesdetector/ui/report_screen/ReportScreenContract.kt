package com.example.aedesdetector.ui.report_screen

interface ReportScreenContract {

    interface View{
        fun onSuccess()
        fun onError(message: String)
    }

    interface Presenter{
        fun uploadPinLocation()
    }
}