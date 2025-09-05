package com.kic.stepmemory.ui.recording

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.PolylineOptions
import com.google.firebase.firestore.FirebaseFirestore
import com.kic.stepmemory.R
import com.kic.stepmemory.data.Landmark
import com.kic.stepmemory.databinding.ActivityRecordingBinding
import com.kic.stepmemory.services.LocationTrackingService
import com.kic.stepmemory.ui.landmark.AddLandmarkBottomSheet
import com.kic.stepmemory.ui.memo.MemoActivity

class RecordingActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var binding: ActivityRecordingBinding
    private lateinit var googleMap: GoogleMap
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var firestore: FirebaseFirestore

    private var isTracking = false
    private var currentPathPoints: MutableList<LatLng> = LocationTrackingService.currentPathPoints
    private val LOCATION_PERMISSION_REQUEST_CODE = 1001
    private var trackingStartTime: Long = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRecordingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        firestore = FirebaseFirestore.getInstance()

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "道の記録"

        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        binding.btnRecordAction.setOnClickListener {
            if (isTracking) {
                stopTracking()
            } else {
                checkLocationPermissions()
            }
        }

        binding.fabMyLocation.setOnClickListener {
            moveCameraToCurrentLocation()
        }

        binding.fabAddLandmarkRecording.setOnClickListener {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "ランドマーク追加には位置情報が必要です。", Toast.LENGTH_SHORT).show()
                checkLocationPermissions()
                return@setOnClickListener
            }
            fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
                if (location != null) {
                    val currentLatLng = LatLng(location.latitude, location.longitude)
                    AddLandmarkBottomSheet(currentLatLng.latitude, currentLatLng.longitude) { landmark ->
                        saveLandmarkToFirestore(landmark)
                    }.show(supportFragmentManager, AddLandmarkBottomSheet.TAG)
                } else {
                    Toast.makeText(this, "現在地が取得できませんでした。", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        googleMap.uiSettings.isZoomControlsEnabled = true
        googleMap.uiSettings.isMyLocationButtonEnabled = false

        enableMyLocationLayer()
        moveCameraToCurrentLocation()
        fetchAndDrawLandmarks()

        isTracking = LocationTrackingService.isServiceRunning
        updateTrackingButtonState()

        if (isTracking && currentPathPoints.isNotEmpty()) {
            drawPathOnMap()
        }
    }

    private fun saveLandmarkToFirestore(landmark: Landmark) {
        firestore.collection("landmarks")
            .add(landmark)
            .addOnSuccessListener {
                Toast.makeText(this, "ランドマークを登録しました！", Toast.LENGTH_SHORT).show()
                fetchAndDrawLandmarks(clearMap = false)
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "ランドマークの登録に失敗しました: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun fetchAndDrawLandmarks(clearMap: Boolean = true) {
        if (clearMap) {
            googleMap.clear()
        }
        firestore.collection("landmarks").get()
            .addOnSuccessListener { querySnapshot ->
                for (document in querySnapshot.documents) {
                    val landmark = document.toObject(Landmark::class.java)
                    landmark?.let {
                        val latLng = LatLng(it.latitude, it.longitude)
                        googleMap.addMarker(
                            MarkerOptions()
                                .position(latLng)
                                .title(it.title)
                                .snippet(it.episode)
                                .icon(BitmapDescriptorFactory.defaultMarker(getMarkerColor(it.iconType)))
                        )
                    }
                }
            }
    }

    private fun getMarkerColor(iconType: String): Float {
        return when (iconType) {
            "FOOD" -> BitmapDescriptorFactory.HUE_ORANGE
            "SCENERY" -> BitmapDescriptorFactory.HUE_GREEN
            "ONSEN" -> BitmapDescriptorFactory.HUE_CYAN
            "SHOPPING" -> BitmapDescriptorFactory.HUE_MAGENTA
            "SIGHTSEEING" -> BitmapDescriptorFactory.HUE_YELLOW
            else -> BitmapDescriptorFactory.HUE_RED
        }
    }

    private fun moveCameraToCurrentLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.lastLocation
                .addOnSuccessListener { location: Location? ->
                    if (location != null) {
                        val currentLatLng = LatLng(location.latitude, location.longitude)
                        googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 15f))
                    } else {
                        Toast.makeText(this, "現在地を取得できませんでした。", Toast.LENGTH_SHORT).show()
                    }
                }
        } else {
            val defaultLocation = LatLng(34.702485, 135.495951)
            googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(defaultLocation, 10f))
        }
    }

    private fun checkLocationPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), LOCATION_PERMISSION_REQUEST_CODE)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION), LOCATION_PERMISSION_REQUEST_CODE)
        } else {
            startTracking()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                startTracking()
                enableMyLocationLayer()
            } else {
                Toast.makeText(this, "位置情報パーミッションが拒否されました。", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun startTracking() {
        isTracking = true
        trackingStartTime = System.currentTimeMillis()
        updateTrackingButtonState()
        val intent = Intent(this, LocationTrackingService::class.java).apply {
            action = LocationTrackingService.ACTION_START_TRACKING
        }
        startService(intent)
        Toast.makeText(this, "記録を開始しました！", Toast.LENGTH_SHORT).show()
    }

    private fun stopTracking() {
        isTracking = false
        val trackingEndTime = System.currentTimeMillis()
        val intent = Intent(this, LocationTrackingService::class.java).apply {
            action = LocationTrackingService.ACTION_STOP_TRACKING
        }
        stopService(intent)

        updateTrackingButtonState()

        if (currentPathPoints.isNotEmpty()) {
            googleMap.addMarker(MarkerOptions().position(currentPathPoints.last()).title("終了地点"))
        }

        Toast.makeText(this, "記録を終了しました。", Toast.LENGTH_SHORT).show()
        val memoIntent = Intent(this, MemoActivity::class.java)

        // ★★★ Intentで巨大な経路データを渡すのを完全にやめる ★★★
        memoIntent.putExtra("RECORD_START_TIME", trackingStartTime)
        memoIntent.putExtra("RECORD_END_TIME", trackingEndTime)
        memoIntent.putExtra("RECORD_DURATION_MS", trackingEndTime - trackingStartTime)
        startActivity(memoIntent)
        finish()
    }

    private fun drawPathOnMap() {
        googleMap.clear()
        fetchAndDrawLandmarks()
        if (currentPathPoints.size > 1) {
            googleMap.addMarker(MarkerOptions().position(currentPathPoints.first()).title("開始地点"))
            val polylineOptions = PolylineOptions().addAll(currentPathPoints).color(Color.BLUE).width(10f)
            googleMap.addPolyline(polylineOptions)
            googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(currentPathPoints.last(), 16f))
        } else if (currentPathPoints.size == 1) {
            googleMap.addMarker(MarkerOptions().position(currentPathPoints.first()).title("現在地"))
            googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(currentPathPoints.first(), 16f))
        }
    }

    private fun enableMyLocationLayer() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            googleMap.isMyLocationEnabled = true
        }
    }

    private fun updateTrackingButtonState() {
        binding.btnRecordAction.text = if (isTracking) "記録を終了する" else "記録を開始する"
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    override fun onResume() {
        super.onResume()
        isTracking = LocationTrackingService.isServiceRunning
        updateTrackingButtonState()

        if(::googleMap.isInitialized) {
            fetchAndDrawLandmarks()
            if (isTracking) {
                drawPathOnMap()
            }
        }
    }
}