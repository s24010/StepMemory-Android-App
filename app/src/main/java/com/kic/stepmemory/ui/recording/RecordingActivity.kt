package com.kic.stepmemory.ui.recording

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.PolylineOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.kic.stepmemory.R
import com.kic.stepmemory.data.GeoPoint
import com.kic.stepmemory.data.Record
import com.kic.stepmemory.databinding.ActivityRecordingBinding
import com.kic.stepmemory.services.LocationTrackingService
import java.util.Date

class RecordingActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var binding: ActivityRecordingBinding
    private lateinit var googleMap: GoogleMap
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private var isRecording = false
    private val pathPoints = mutableListOf<LatLng>()
    private var startTime: Long = 0
    private lateinit var firestore: FirebaseFirestore
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRecordingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        firestore = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        val mapFragment = supportFragmentManager.findFragmentById(R.id.map_recording) as SupportMapFragment
        mapFragment.getMapAsync(this)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        binding.btnStartRecording.setOnClickListener {
            if (!isRecording) {
                startRecording()
            }
        }

        binding.btnStopRecording.setOnClickListener {
            if (isRecording) {
                stopRecording()
            }
        }

        createLocationCallback()
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                REQUEST_LOCATION_PERMISSION
            )
            return
        }
        googleMap.isMyLocationEnabled = true
        googleMap.uiSettings.isMyLocationButtonEnabled = true
        googleMap.uiSettings.isZoomControlsEnabled = true
    }

    private fun createLocationCallback() {
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { location ->
                    val newPoint = LatLng(location.latitude, location.longitude)
                    if (isRecording) {
                        pathPoints.add(newPoint)
                        drawPath()
                        googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(newPoint, 18f))
                    }
                }
            }
        }
    }

    private fun startRecording() {
        if (auth.currentUser == null) {
            Toast.makeText(this, "記録を開始するにはログインが必要です。", Toast.LENGTH_SHORT).show()
            return
        }

        isRecording = true
        startTime = System.currentTimeMillis()
        pathPoints.clear()
        googleMap.clear() // Clear previous paths
        binding.btnStartRecording.isEnabled = false
        binding.btnStopRecording.isEnabled = true
        startLocationUpdates()

        // Start foreground service
        val serviceIntent = Intent(this, LocationTrackingService::class.java)
        startService(serviceIntent)

        Toast.makeText(this, "記録を開始しました。", Toast.LENGTH_SHORT).show()
    }

    private fun stopRecording() {
        isRecording = false
        binding.btnStartRecording.isEnabled = true
        binding.btnStopRecording.isEnabled = false
        stopLocationUpdates()

        // Stop foreground service
        val serviceIntent = Intent(this, LocationTrackingService::class.java)
        stopService(serviceIntent)

        if (pathPoints.isNotEmpty()) {
            saveRecordToFirestore()
        } else {
            Toast.makeText(this, "記録データがありません。", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun saveRecordToFirestore() {
        val userId = auth.currentUser?.uid
        if (userId == null) {
            Toast.makeText(this, "ユーザー情報が見つかりません。", Toast.LENGTH_SHORT).show()
            return
        }

        val endTime = System.currentTimeMillis()
        val durationMs = endTime - startTime

        val customPathPoints = pathPoints.map { GeoPoint(it.latitude, it.longitude) }

        val record = Record(
            userId = userId,
            name = "${formatDate(startTime)}の記録",
            memo = "",
            startTime = startTime,
            endTime = endTime,
            durationMs = durationMs,
            pathPoints = customPathPoints,
            createdAt = Date()
        )

        firestore.collection("users").document(userId).collection("records")
            .add(record)
            .addOnSuccessListener {
                Toast.makeText(this, "記録を保存しました。", Toast.LENGTH_SHORT).show()
                finish()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "記録の保存に失敗しました: ${e.message}", Toast.LENGTH_LONG).show()
                Log.e("RecordingActivity", "Error saving record", e)
            }
    }

    private fun startLocationUpdates() {
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000)
            .setWaitForAccurateLocation(false)
            .setMinUpdateIntervalMillis(2000)
            .setMaxUpdateDelayMillis(10000)
            .build()

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
        }
    }

    private fun stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    private fun drawPath() {
        googleMap.clear()
        googleMap.addPolyline(PolylineOptions().addAll(pathPoints).color(R.color.purple_500).width(15f))
    }

    private fun formatDate(millis: Long): String {
        val sdf = java.text.SimpleDateFormat("yyyy/MM/dd HH:mm", java.util.Locale.getDefault())
        return sdf.format(Date(millis))
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_LOCATION_PERMISSION) {
            if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                if (ActivityCompat.checkSelfPermission(
                        this,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    googleMap.isMyLocationEnabled = true
                    googleMap.uiSettings.isMyLocationButtonEnabled = true
                }
            } else {
                Toast.makeText(this, "位置情報の許可が必要です。", Toast.LENGTH_SHORT).show()
            }
        }
    }

    companion object {
        private const val REQUEST_LOCATION_PERMISSION = 1
    }
}
