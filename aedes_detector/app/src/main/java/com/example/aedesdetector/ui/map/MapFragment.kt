package com.example.aedesdetector.ui.map

import android.content.Context.LOCATION_SERVICE
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Outline
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.transition.*
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.Button
import android.widget.ImageButton
import androidx.annotation.RequiresApi
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import com.example.aedesdetector.R
import com.example.aedesdetector.models.UserReport
import com.example.aedesdetector.ui.report_screen.ReportScreenActivity
import com.example.aedesdetector.utils.AlertUtils
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.model.*
import com.google.maps.android.heatmaps.HeatmapTileProvider


class MapFragment : Fragment(), MapContract.View, OnMapReadyCallback, GoogleMap.OnMarkerClickListener  {

    private lateinit var mPresenter: MapPresenter
    private lateinit var mapView: MapView
    private lateinit var googleMap: GoogleMap
    private lateinit var locationManager: LocationManager

    private var heatmapOverlay: TileOverlay? = null

    private var showingPins = true
    private var showingHeatmap = false

    private var pinsHasMap = HashMap<String, Marker>()

    private lateinit var recordButton: Button
    private lateinit var transitionsContainer: ConstraintLayout
    private lateinit var recordingView: View

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View? {
        mPresenter = MapPresenter(this, requireContext(), requireActivity())
        val root = inflater.inflate(R.layout.fragment_map, container, false)

        mapView = root.findViewById(R.id.map_view)
        mapView.onCreate(savedInstanceState)
        mapView.onResume()
        mapView.getMapAsync(this)

        root.findViewById<ImageButton>(R.id.pin_button).setOnClickListener {
            setPins()
        }

        root.findViewById<ImageButton>(R.id.heatmap_button).setOnClickListener {
            setHeatmap()
        }

        recordingView = root.findViewById(R.id.button_view)
        transitionsContainer = root.findViewById(R.id.button_box)

        val curveRadius = 50F

        recordingView.outlineProvider = object : ViewOutlineProvider() {
            @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
            override fun getOutline(view: View?, outline: Outline?) {
                outline?.setRoundRect(0, 0, view!!.width, (view.height+curveRadius).toInt(), curveRadius)
            }
        }

        recordingView.clipToOutline = true
        recordButton = root.findViewById(R.id.record_button)
        recordButton.setOnClickListener {
            mPresenter.recordAudio()
        }

        return root
    }

    override fun setRecordingState() {
        recordButton.text = "GRAVANDO"

        val autoTransition = AutoTransition()
        autoTransition.duration = 1000

        TransitionManager.beginDelayedTransition(
            transitionsContainer, TransitionSet()
                .addTransition(ChangeBounds())
                .addTransition(autoTransition)
        )

        val params: ViewGroup.LayoutParams = transitionsContainer.layoutParams
        params.height = ViewGroup.LayoutParams.MATCH_PARENT
        transitionsContainer.layoutParams = params

        val viewParams: ViewGroup.LayoutParams = recordingView.layoutParams
        viewParams.height = ViewGroup.LayoutParams.MATCH_PARENT
        recordingView.layoutParams = viewParams

    }

    override fun setStoppedState() {
        recordButton.text = "GRAVAR"

        val autoTransition = AutoTransition()
        autoTransition.duration = 1000

        TransitionManager.beginDelayedTransition(
            transitionsContainer, TransitionSet()
                .addTransition(ChangeBounds())
                .addTransition(autoTransition)
        )

        val params: ViewGroup.LayoutParams = transitionsContainer.layoutParams
        params.height = ViewGroup.LayoutParams.WRAP_CONTENT
        transitionsContainer.layoutParams = params

        val viewSize = 140 * resources.displayMetrics.density

        val viewParams: ViewGroup.LayoutParams = recordingView.layoutParams
        viewParams.height = viewSize.toInt()
        recordingView.layoutParams = viewParams
    }

    override fun onDetectionPositive() {
        AlertUtils.displayAlert(requireContext(),
            "Aedes!",
            "Identificamos um Aedes aegypti. Gostaria de compartilhar no mapa?",
            "Sim",
            DialogInterface.OnClickListener { _, _ ->
                // User clicked OK button
                startActivity(Intent(requireContext(), ReportScreenActivity::class.java))
            },
            "Não",
            DialogInterface.OnClickListener { _, _ ->
                // User clicked OK button
            })
    }

    override fun onDetectionNegative() {
        AlertUtils.displayAlert(
            requireContext(),
            "Não é Aedes!",
            "Não identificamos um aedes na sua gravação.",
            "Ok",
            DialogInterface.OnClickListener { _, _ ->
                // User clicked OK button
            },
            null,
            null
        )
    }

    private fun setHeatmap() {
        if (showingHeatmap) {
            heatmapOverlay?.remove()
        }
        else {
            var latLngList = ArrayList<LatLng>()
            for (pinKey in pinsHasMap.keys) {
                val pin = pinsHasMap[pinKey]
                if (pin != null) {
                    latLngList.add(pin.position)
                }
            }
            val provider = HeatmapTileProvider.Builder()
                .data(latLngList)
                .build()

            heatmapOverlay = googleMap.addTileOverlay(TileOverlayOptions().tileProvider(provider))
        }
        showingHeatmap = !showingHeatmap

    }

    private fun setPins() {
        if (showingPins) {
            for (pin in pinsHasMap.keys) {
                pinsHasMap[pin]?.isVisible = false
            }
        }
        else {
            for (pin in pinsHasMap.keys) {
                pinsHasMap[pin]?.isVisible = true
            }
        }
        showingPins = !showingPins
    }

    override fun onMapReady(mMap: GoogleMap?) {
        if (mMap != null) {
            googleMap = mMap
            //Brazil coordinates
            googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(
                LatLng(-16.1815243,-51.6828065),
                4.0f
            ))
            setUpMap()
        }
    }

    override fun onResume() {
        super.onResume()
        if (this::googleMap.isInitialized) {
            mPresenter.fetchPinsWithMapLocation(googleMap.cameraPosition.target, 15.0f)
        }
    }

    override fun onMarkerClick(p0: Marker?): Boolean {
        return true
    }

    private fun setUpMap() {
        //Permissions check
        if (ActivityCompat.checkSelfPermission(requireContext(),
                android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(requireActivity(),
                arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION), LOCATION_PERMISSION_REQUEST_CODE)
            return
        }

        //Set up location manager to fetch user's current location
        if (!this::locationManager.isInitialized) {
            // Create persistent LocationManager reference
            try {
                locationManager =
                    requireActivity().getSystemService(LOCATION_SERVICE) as LocationManager
            }
            catch(e: Exception) {
                Log.d("EXCEPTION", e.toString())
            }
        }

        //Center map on user's location
        googleMap.isMyLocationEnabled = true
        val location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
        if (location == null) {
            googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(
                LatLng(-16.1815243,-51.6828065),
                4.0f
            ))
            mPresenter.fetchPinsWithMapLocation(LatLng(-16.1815243,-51.6828065), 15.0f)
        }
        else {
            val latLngLocation = LatLng(location.latitude, location.longitude)
            googleMap.moveCamera(
                CameraUpdateFactory.newLatLngZoom(
                    latLngLocation,
                    15.0f
                )
            )
           mPresenter.fetchPinsWithMapLocation(latLngLocation, 15.0f)
        }
    }


    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.first() == PackageManager.PERMISSION_GRANTED) {
                setUpMap()
            }
        }
    }



    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1
    }

    override fun onPinsFetch(pinArray: ArrayList<UserReport>) {
        for (pin in pinArray) {
            if (!pinsHasMap.contains(pin.pinKey)) {
                val pinOptions = MarkerOptions()
                pinOptions.position(LatLng(pin.latitude, pin.longitude))
                val marker = googleMap.addMarker(pinOptions)
                pinsHasMap[pin.pinKey] = marker
            }
        }
    }

    override fun onErrorFetchingPins(message: String) {

    }
}
