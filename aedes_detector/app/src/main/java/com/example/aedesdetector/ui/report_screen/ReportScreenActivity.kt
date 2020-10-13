package com.example.aedesdetector.ui.report_screen

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.aedesdetector.R

class ReportScreenActivity: AppCompatActivity(), ReportScreenContract.View {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_report_screen)

    }
}