package com.example.aedesdetector.ui.report_screen

import android.content.Context
import android.content.DialogInterface
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.example.aedesdetector.R
import com.example.aedesdetector.utils.AlertUtils
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.model.Circle
import com.google.android.gms.maps.model.CircleOptions
import com.google.android.gms.maps.model.LatLng
import kotlinx.android.synthetic.main.activity_report_screen.*
import kotlin.random.Random


class ReportScreenActivity: AppCompatActivity(), ReportScreenContract.View, OnMapReadyCallback, GoogleMap.OnMapClickListener {


    private lateinit var googleMap: GoogleMap
    private lateinit var locationManager: LocationManager
    private var mapCircle: Circle? = null
    private var userLocation: LatLng? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_report_screen)

        map_view.onCreate(savedInstanceState)
        map_view.onResume()
        map_view.getMapAsync(this)

        report_button.setOnClickListener {
            uploadLocationToDatabase()
        }

        hideLoader()

    }

    override fun onMapReady(mMap: GoogleMap?) {
        if (mMap != null) {
            googleMap = mMap
            //Brazil coordinates
            googleMap.moveCamera(
                CameraUpdateFactory.newLatLngZoom(
                    LatLng(-16.1815243,-51.6828065),
                    4.0f
                ))
            setUpMap()
        }
    }

    private fun setUpMap() {
        //Permissions check
        if (ActivityCompat.checkSelfPermission(this,
                android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION),
                ReportScreenActivity.LOCATION_PERMISSION_REQUEST_CODE
            )
            return
        }
        //Set up location manager to fetch user's current location
        if (!this::locationManager.isInitialized) {
            // Create persistent LocationManager reference
            try {
                locationManager =
                    this.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            }
            catch(e: Exception) {
                Log.d("EXCEPTION", e.localizedMessage)
            }
        }

        //Center map on user's location
        googleMap.setOnMapClickListener(this)
        val location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
        if (location == null) {
            googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(
                LatLng(-16.1815243,-51.6828065),
                4.0f
            ))
        }
        else {
            val latLngLocation = LatLng(location.latitude, location.longitude)
            userLocation = latLngLocation
            googleMap.moveCamera(
                CameraUpdateFactory.newLatLngZoom(
                    userLocation,
                    15.0f
                )
            )
            onMapClick(getUserLocationWithError(latLngLocation))
        }
    }

    override fun onMapClick(p0: LatLng?) {
        try {
            mapCircle?.remove()
            val options = CircleOptions()
            options.center(
                LatLng(
                    p0!!.latitude,
                    p0.longitude
                )
            )
            options.radius(90.0)
            options.strokeColor(R.color.colorAccent)
            options.zIndex(1f)
            mapCircle = googleMap.addCircle(options)
        }
        catch(e: Exception) {

        }
    }

    override fun onSuccess() {
        AlertUtils.displayAlert(
            this,
            "Ocorrência",
            "Atualizamos o nosso mapa com a sua notificação!",
            "Ok",
            DialogInterface.OnClickListener { _, _ ->
                // User clicked OK button
                this.finish()

            },
            null,null
        )
    }

    override fun onError(message: String) {
        hideLoader()
        AlertUtils.displayAlert(
            this,
            "Ocorrência",
            "Ocorreu um erro ao salvar a sua notificação: $message",
            "Ok",
            DialogInterface.OnClickListener { dialog, id ->
                // User clicked OK button

            },
            null,null
        )
    }

    private fun getUserLocationWithError(userLocation: LatLng): LatLng {
        //20 meters error can be inserted
        val latError = Random(System.nanoTime()).nextDouble(-0.0009, 0.0009)
        val lonError = Random(System.nanoTime()).nextDouble(-0.0009, 0.0009)
        return LatLng(userLocation.latitude + latError, userLocation.longitude + lonError)
    }

    private fun showLoader() {
        loader_view.visibility = View.VISIBLE
        window.setFlags(
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
    }

    private fun hideLoader() {
        loader_view.visibility = View.GONE
        window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
    }

    private fun uploadLocationToDatabase() {
        if (mapCircle != null) {
            showLoader()
            //TODO: Edit the report type here...
            ReportScreenPresenter(this, this).uploadPinLocation(userLocation, mapCircle!!.center, 1)
        }
        else {
            Toast.makeText(this, "Selecione um local no mapa para registrar a ocorrência!", Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1
    }
}