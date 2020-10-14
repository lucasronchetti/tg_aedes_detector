package com.example.aedesdetector.ui.map

import android.content.Context.LOCATION_SERVICE
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProviders
import com.example.aedesdetector.R
import com.example.aedesdetector.models.UserReport
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.model.*
import com.google.maps.android.heatmaps.HeatmapTileProvider
import kotlinx.android.synthetic.main.fragment_map.*


class MapFragment : Fragment(), MapContract.View, OnMapReadyCallback, GoogleMap.OnMarkerClickListener  {

    private lateinit var mapView: MapView
    private lateinit var googleMap: GoogleMap
    private lateinit var locationManager: LocationManager

    private var heatmapOverlay: TileOverlay? = null

    private var showingPins = true
    private var showingHeatmap = false

    private var pinsHasMap = HashMap<String, Marker>()

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View? {
        val root = inflater.inflate(R.layout.fragment_map, container, false)

        mapView = root.findViewById(R.id.map_view)
        mapView.onCreate(savedInstanceState)
        mapView.onResume()
        mapView.getMapAsync(this)

        root.findViewById<Button>(R.id.pin_button).setOnClickListener {
            setPins()
        }

        root.findViewById<Button>(R.id.heatmap_button).setOnClickListener {
            setHeatmap()
        }

        return root
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
            MapPresenter(this, requireContext()).fetchPinsWithMapLocation(googleMap.cameraPosition.target, 15.0f)
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
        if (googleMap == null) {
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
                Log.d("EXCEPTION", e.localizedMessage)
            }
        }

        //Center map on user's location
        googleMap.isMyLocationEnabled = true
        val location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
        val latLngLocation = LatLng(location.latitude, location.longitude)
        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(
            latLngLocation,
            15.0f
        ))
        MapPresenter(this, requireContext()).fetchPinsWithMapLocation(latLngLocation, 15.0f)
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

    override fun onPinsFetch(pinsList: ArrayList<UserReport>) {
        for (pin in pinsList) {
            if (!pinsHasMap.contains(pin.pinKey)) {
                var pinOptions = MarkerOptions()
                pinOptions.position(LatLng(pin.latitude, pin.longitude))
                val marker = googleMap.addMarker(pinOptions)
                pinsHasMap[pin.pinKey] = marker
            }
        }
    }

    override fun onErrorFetchingPins(message: String) {

    }
}
