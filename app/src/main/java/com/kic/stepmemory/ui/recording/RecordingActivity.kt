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
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.PolylineOptions
import com.google.gson.Gson
import com.kic.stepmemory.R
import com.kic.stepmemory.databinding.ActivityRecordingBinding
import com.kic.stepmemory.services.LocationTrackingService
import com.kic.stepmemory.ui.memo.MemoActivity
import com.kic.stepmemory.ui.landmark.AddLandmarkActivity

class RecordingActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var binding: ActivityRecordingBinding
    private lateinit var googleMap: GoogleMap
    private var isTracking = false
    private var currentPathPoints: MutableList<LatLng> = LocationTrackingService.currentPathPoints
    private val LOCATION_PERMISSION_REQUEST_CODE = 1001
    private var trackingStartTime: Long = 0L

    // ★ 現在地取得のために FusedLocationProviderClient を追加
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRecordingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // ★ FusedLocationProviderClient のインスタンスを初期化
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "記録中"

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
            moveCameraToCurrentLocation() // ★ 現在地ボタンの挙動も現在地取得に変更
        }

        binding.fabAddLandmarkRecording.setOnClickListener {
            // 現在地を取得
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                // 権限がある場合のみ、現在地を取得する処理を実行
                fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
                    if (location != null) {
                        // AddLandmarkActivityを開始し、現在地の緯度経度を渡す
                        val intent = Intent(this, AddLandmarkActivity::class.java).apply {
                            putExtra("LATITUDE", location.latitude)
                            putExtra("LONGITUDE", location.longitude)
                        }
                        startActivity(intent)
                    } else {
                        Toast.makeText(this, "現在地が取得できませんでした。", Toast.LENGTH_SHORT)
                            .show()
                    }
                }.addOnFailureListener {
                    Toast.makeText(this, "現在地の取得に失敗しました。", Toast.LENGTH_SHORT).show()
                }
            } else {
                // 権限がない場合は、ユーザーにトーストで通知する（もしくは再度権限をリクエストする）
                Toast.makeText(this, "位置情報の権限がありません。", Toast.LENGTH_SHORT).show()
                // 必要であれば、再度権限を要求するロジックをここに追加することもできます
                // checkLocationPermissions() などを呼び出す
            }
        }

        updateTrackingButtonState()
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        googleMap.uiSettings.isZoomControlsEnabled = true
        googleMap.uiSettings.isMyLocationButtonEnabled = false

        enableMyLocationLayer()
        // ★ これまでの固定地点表示から、現在地への移動に変更
        moveCameraToCurrentLocation()

        if (isTracking && currentPathPoints.isNotEmpty()) {
            drawPathOnMap()
        }
    }

    /**
     * ★ 現在地を取得してカメラを移動させる関数
     */
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
            // 権限がない場合は、デフォルト地点（大阪駅）を表示
            val defaultLocation = LatLng(34.702485, 135.495951)
            googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(defaultLocation, 10f))
        }
    }

    private fun checkLocationPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    startTracking()
                } else {
                    ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION), LOCATION_PERMISSION_REQUEST_CODE)
                }
            } else {
                startTracking()
            }
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), LOCATION_PERMISSION_REQUEST_CODE)
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
        updateTrackingButtonState()
        val trackingEndTime = System.currentTimeMillis()
        val intent = Intent(this, LocationTrackingService::class.java).apply {
            action = LocationTrackingService.ACTION_STOP_TRACKING
        }
        stopService(intent)

        if (currentPathPoints.isNotEmpty()) {
            googleMap.addMarker(MarkerOptions().position(currentPathPoints.last()).title("終了地点"))
        }

        Toast.makeText(this, "記録を終了しました。", Toast.LENGTH_SHORT).show()
        val memoIntent = Intent(this, MemoActivity::class.java)
        val pathDataJson = Gson().toJson(currentPathPoints)
        memoIntent.putExtra("RECORD_PATH_DATA", pathDataJson)
        memoIntent.putExtra("RECORD_START_TIME", trackingStartTime)
        memoIntent.putExtra("RECORD_END_TIME", trackingEndTime)
        memoIntent.putExtra("RECORD_DURATION_MS", trackingEndTime - trackingStartTime)
        startActivity(memoIntent)
        finish()
    }

    private fun drawPathOnMap() {
        googleMap.clear()
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
        if (isTracking) {
            drawPathOnMap()
        }
    }
}