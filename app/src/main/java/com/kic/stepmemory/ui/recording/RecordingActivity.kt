package com.kic.stepmemory.ui.recording

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Location
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
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
import com.google.firebase.storage.FirebaseStorage
import com.kic.stepmemory.R
import com.kic.stepmemory.data.Landmark
import com.kic.stepmemory.databinding.ActivityRecordingBinding
import com.kic.stepmemory.services.LocationTrackingService
import com.kic.stepmemory.ui.landmark.AddLandmarkBottomSheet
import com.kic.stepmemory.ui.memo.MemoActivity
import java.io.File
import java.io.IOException
import java.util.UUID

class RecordingActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var binding: ActivityRecordingBinding
    private lateinit var googleMap: GoogleMap
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var firestore: FirebaseFirestore
    private lateinit var storage: FirebaseStorage

    private var isTracking = false
    private var currentPathPoints: MutableList<LatLng> = LocationTrackingService.currentPathPoints
    private val LOCATION_PERMISSION_REQUEST_CODE = 1001
    private var trackingStartTime: Long = 0L

    private var mediaRecorder: MediaRecorder? = null
    private var audioFilePath: String? = null
    private var uploadedAudioUrl: String? = null
    private var isAudioRecording = false
    private val AUDIO_PERMISSION_REQUEST_CODE = 2002

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRecordingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        firestore = FirebaseFirestore.getInstance()
        storage = FirebaseStorage.getInstance()

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

        binding.fabRecordAudio.setOnClickListener {
            if (isAudioRecording) {
                stopAudioRecording()
            } else {
                checkAudioPermissionAndStartRecording()
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
                    }
                }
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
        when (requestCode) {
            LOCATION_PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                    startTracking()
                    enableMyLocationLayer()
                } else {
                    Toast.makeText(this, "位置情報パーミッションが拒否されました。", Toast.LENGTH_LONG).show()
                }
            }
            AUDIO_PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    startAudioRecording()
                } else {
                    if (!ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.RECORD_AUDIO)) {
                        showGoToSettingsDialog()
                    } else {
                        Toast.makeText(this, "マイクの使用が許可されませんでした。", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    private fun showGoToSettingsDialog() {
        AlertDialog.Builder(this)
            .setTitle("権限が必要です")
            .setMessage("録音機能を使用するには、マイクの権限を手動で許可する必要があります。設定画面に移動しますか？")
            .setPositiveButton("設定へ") { _, _ ->
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                val uri = Uri.fromParts("package", packageName, null)
                intent.data = uri
                startActivity(intent)
            }
            .setNegativeButton("キャンセル", null)
            .show()
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
        if (isAudioRecording) {
            Toast.makeText(this, "音声録音を停止してから記録を終了してください。", Toast.LENGTH_LONG).show()
            return
        }

        isTracking = false
        val trackingEndTime = System.currentTimeMillis()
        val serviceIntent = Intent(this, LocationTrackingService::class.java).apply {
            action = LocationTrackingService.ACTION_STOP_TRACKING
        }
        stopService(serviceIntent)

        updateTrackingButtonState()

        if (currentPathPoints.isNotEmpty()) {
            googleMap.addMarker(MarkerOptions().position(currentPathPoints.last()).title("終了地点"))
        }

        Toast.makeText(this, "記録を終了しました。", Toast.LENGTH_SHORT).show()
        val memoIntent = Intent(this, MemoActivity::class.java)

        uploadedAudioUrl?.let {
            memoIntent.putExtra("AUDIO_URL", it)
        }

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

    private fun checkAudioPermissionAndStartRecording() {
        when {
            ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED -> {
                startAudioRecording()
            }
            ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.RECORD_AUDIO) -> {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), AUDIO_PERMISSION_REQUEST_CODE)
            }
            else -> {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), AUDIO_PERMISSION_REQUEST_CODE)
            }
        }
    }

    private fun startAudioRecording() {
        if (!isTracking) {
            Toast.makeText(this, "記録を開始してから音声メモを録音できます。", Toast.LENGTH_SHORT).show()
            return
        }
        val audioFile = File(externalCacheDir, "${UUID.randomUUID()}.3gp")
        audioFilePath = audioFile.absolutePath

        mediaRecorder = MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
            setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
            setOutputFile(audioFilePath)
            try {
                prepare()
                start()
                isAudioRecording = true
                binding.tvRecordingStatus.text = "録音中..."
                binding.tvRecordingStatus.visibility = View.VISIBLE
                binding.fabRecordAudio.setImageResource(android.R.drawable.ic_media_pause)
            } catch (e: IOException) {
                Toast.makeText(this@RecordingActivity, "録音の準備に失敗しました。", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun stopAudioRecording() {
        mediaRecorder?.apply {
            stop()
            release()
        }
        mediaRecorder = null
        isAudioRecording = false

        binding.tvRecordingStatus.text = "アップロード中..."
        binding.fabRecordAudio.setImageResource(android.R.drawable.ic_btn_speak_now)
        binding.fabRecordAudio.isEnabled = false

        uploadAudio()
    }

    private fun uploadAudio() {
        if (audioFilePath == null) return

        val file = Uri.fromFile(File(audioFilePath!!))
        val storageRef = storage.reference.child("audio/${file.lastPathSegment}")
        val uploadTask = storageRef.putFile(file)

        uploadTask.continueWithTask { task ->
            if (!task.isSuccessful) {
                task.exception?.let { throw it }
            }
            storageRef.downloadUrl
        }.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                uploadedAudioUrl = task.result.toString()
                binding.tvRecordingStatus.text = "アップロード完了！"
                Toast.makeText(this, "音声のアップロードが完了しました。", Toast.LENGTH_SHORT).show()
            } else {
                binding.tvRecordingStatus.text = "アップロード失敗"
                Toast.makeText(this, "音声のアップロードに失敗しました: ${task.exception?.message}", Toast.LENGTH_LONG).show()
            }
            binding.fabRecordAudio.isEnabled = true
            audioFilePath = null
        }
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

    override fun onDestroy() {
        super.onDestroy()
        mediaRecorder?.release()
        mediaRecorder = null
    }
}