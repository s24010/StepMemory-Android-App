package com.kic.stepmemory.ui.review

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.media.MediaPlayer
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.PolylineOptions
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.toObject
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage
import com.kic.stepmemory.R
import com.kic.stepmemory.data.AudioPin
import com.kic.stepmemory.data.Landmark
import com.kic.stepmemory.data.Record
import com.kic.stepmemory.databinding.ActivityReviewBinding
import com.kic.stepmemory.ui.landmark.AddLandmarkBottomSheet
import com.kic.stepmemory.ui.streetview.StreetViewActivity
import java.text.SimpleDateFormat
import java.util.*

class ReviewActivity : AppCompatActivity(), OnMapReadyCallback, GoogleMap.OnMarkerClickListener {

    private lateinit var binding: ActivityReviewBinding
    private lateinit var googleMap: GoogleMap
    private lateinit var firestore: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private var record: Record? = null
    private var recordId: String? = null
    private var mediaPlayer: MediaPlayer? = null
    private lateinit var bottomSheetBehavior: BottomSheetBehavior<View>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReviewBinding.inflate(layoutInflater)
        setContentView(binding.root)

        firestore = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()
        recordId = intent.getStringExtra("RECORD_ID")

        val mapFragment = supportFragmentManager.findFragmentById(R.id.map_review) as SupportMapFragment
        mapFragment.getMapAsync(this)

        setupBottomSheet()
        setupClickListeners()
        handleBackButton()

        if (recordId == null) {
            Toast.makeText(this, "記録IDが見つかりません。", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        googleMap.uiSettings.isZoomControlsEnabled = true
        googleMap.setInfoWindowAdapter(CustomInfoWindowAdapter(this))
        googleMap.setOnMarkerClickListener(this)
        loadRecordData()
    }

    private fun setupBottomSheet() {
        bottomSheetBehavior = BottomSheetBehavior.from(binding.bottomSheetLayout)
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
    }

    private fun handleBackButton() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    binding.ivCrosshair.visibility == View.VISIBLE -> {
                        exitLandmarkSelectionMode()
                    }
                    bottomSheetBehavior.state == BottomSheetBehavior.STATE_EXPANDED -> {
                        bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
                    }
                    else -> {
                        finish()
                    }
                }
            }
        })
    }

    private fun loadRecordData() {
        val userId = auth.currentUser?.uid
        if (userId == null) {
            Toast.makeText(this, "ログインが必要です。", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        recordId?.let { id ->
            firestore.collection("users").document(userId).collection("records").document(id).get()
                .addOnSuccessListener { document ->
                    if (document.exists()) {
                        record = document.toObject(Record::class.java)
                        record?.let {
                            updateUiWithRecord(it)
                            drawRecordPath(it)
                            loadLandmarks(id)
                            drawAudioPins(it.audioPins)
                        }
                    } else {
                        Toast.makeText(this, "記録が見つかりませんでした。", Toast.LENGTH_SHORT).show()
                    }
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "記録の読み込みに失敗: ${e.message}", Toast.LENGTH_LONG).show()
                }
        }
    }

    private fun updateUiWithRecord(record: Record) {
        val title = record.name ?: "無題の記録"
        binding.tvRecordTitleHeader.text = title
        binding.etRecordTitleReview.setText(record.name)
        binding.etRecordMemoReview.setText(record.memo)

        val sdf = SimpleDateFormat("yyyy年M月d日 HH:mm", Locale.JAPAN)
        val startTime = sdf.format(Date(record.startTime))
        val endTime = SimpleDateFormat("HH:mm", Locale.JAPAN).format(Date(record.endTime))
        binding.tvRecordDateReview.text = getString(R.string.record_date_format, startTime, endTime)
    }

    private fun bitmapDescriptorFromVector(context: Context, vectorResId: Int): BitmapDescriptor? {
        return ContextCompat.getDrawable(context, vectorResId)?.run {
            setBounds(0, 0, intrinsicWidth, intrinsicHeight)
            val bitmap = Bitmap.createBitmap(intrinsicWidth, intrinsicHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            draw(canvas)
            BitmapDescriptorFactory.fromBitmap(bitmap)
        }
    }

    private fun drawRecordPath(record: Record) {
        if (record.pathPoints.isNotEmpty()) {
            val path = record.pathPoints.map { LatLng(it.latitude, it.longitude) }
            googleMap.addPolyline(PolylineOptions().addAll(path).color(Color.BLUE).width(10f))

            path.firstOrNull()?.let {
                googleMap.addMarker(
                    MarkerOptions()
                        .position(it)
                        .title("スタート")
                        .icon(bitmapDescriptorFromVector(this, R.drawable.ic_start_flag))
                )
            }

            if (path.size > 1) {
                path.lastOrNull()?.let {
                    googleMap.addMarker(
                        MarkerOptions()
                            .position(it)
                            .title("ゴール")
                            .icon(bitmapDescriptorFromVector(this, R.drawable.ic_finish_flag))
                    )
                }
            }

            val cameraUpdate = CameraUpdateFactory.newLatLngZoom(path.first(), 15f)
            googleMap.moveCamera(cameraUpdate)
        } else {
            Toast.makeText(this, "この記録には経路情報がありません。", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupClickListeners() {
        binding.btnSaveReview.setOnClickListener { saveRecordChanges() }

        binding.btnShowStreetView.setOnClickListener {
            recordId?.let {
                val intent = Intent(this, StreetViewActivity::class.java).apply {
                    putExtra("RECORD_ID", it)
                }
                startActivity(intent)
            } ?: Toast.makeText(this, "記録データがありません。", Toast.LENGTH_SHORT).show()
        }

        binding.btnAddLandmarkReview.setOnClickListener { enterLandmarkSelectionMode() }
        binding.btnConfirmLandmark.setOnClickListener { confirmLandmarkLocation() }
        binding.btnCancelLandmark.setOnClickListener { exitLandmarkSelectionMode() }
    }

    private fun enterLandmarkSelectionMode() {
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
        binding.landmarkSelectionControls.visibility = View.VISIBLE
        binding.ivCrosshair.visibility = View.VISIBLE
        Toast.makeText(this, "ランドマークを追加したい場所にカーソルを合わせてください。", Toast.LENGTH_LONG).show()
    }

    private fun exitLandmarkSelectionMode() {
        binding.landmarkSelectionControls.visibility = View.GONE
        binding.ivCrosshair.visibility = View.GONE
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
    }

    private fun confirmLandmarkLocation() {
        val target = googleMap.cameraPosition.target
        val bottomSheet = AddLandmarkBottomSheet(target.latitude, target.longitude) { landmark ->
            saveLandmarkToFirestore(landmark)
        }
        bottomSheet.show(supportFragmentManager, AddLandmarkBottomSheet.TAG)
    }

    private fun saveLandmarkToFirestore(landmark: Landmark) {
        val userId = auth.currentUser?.uid
        if (userId == null) {
            Toast.makeText(this, "ログインが必要です。", Toast.LENGTH_SHORT).show()
            return
        }
        recordId?.let { recordId ->
            val landmarkWithRecordId = landmark.copy(recordId = recordId)
            firestore.collection("users").document(userId).collection("landmarks").add(landmarkWithRecordId)
                .addOnSuccessListener {
                    Toast.makeText(this, "ランドマークを登録しました！", Toast.LENGTH_SHORT).show()
                    addMarkerToMap(landmarkWithRecordId)
                    exitLandmarkSelectionMode()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "ランドマークの登録に失敗しました: ${e.message}", Toast.LENGTH_LONG).show()
                }
        } ?: Toast.makeText(this, "記録IDがありません。", Toast.LENGTH_SHORT).show()

    }

    private fun addMarkerToMap(landmark: Landmark) {
        val marker: Marker? = googleMap.addMarker(
            MarkerOptions()
                .position(LatLng(landmark.latitude, landmark.longitude))
                .title(landmark.title)       // This title will be overridden by the InfoWindowAdapter
                .snippet(landmark.episode)   // This snippet will be overridden by the InfoWindowAdapter
                .icon(getMarkerIcon(landmark.iconType))
        )
        marker?.tag = landmark
    }

    private fun loadLandmarks(recordId: String) {
        val userId = auth.currentUser?.uid ?: return

        firestore.collection("users").document(userId).collection("landmarks").whereEqualTo("recordId", recordId).get()
            .addOnSuccessListener { documents ->
                for (document in documents) {
                    val landmark = document.toObject<Landmark>()
                    addMarkerToMap(landmark)
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "ランドマークの読み込みに失敗しました: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun getMarkerIcon(iconType: String): BitmapDescriptor {
        return when (iconType) {
            "BRONZE_PIN" -> BitmapDescriptorFactory.fromResource(R.drawable.ic_landmark_bronze_pin)
            "SILVER_PIN" -> BitmapDescriptorFactory.fromResource(R.drawable.ic_landmark_silver_pin)
            "GOLD_PIN" -> BitmapDescriptorFactory.fromResource(R.drawable.ic_landmark_gold_pin)
            "MOON_ICON" -> BitmapDescriptorFactory.fromResource(R.drawable.ic_landmark_moon)
            "FOOD" -> BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ORANGE)
            "SCENERY" -> BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)
            "ONSEN" -> BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_CYAN)
            "SHOPPING" -> BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_MAGENTA)
            "SIGHTSEEING" -> BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_YELLOW)
            else -> BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)
        }
    }

    private fun saveRecordChanges() {
        val newTitle = binding.etRecordTitleReview.text.toString().trim()
        val newMemo = binding.etRecordMemoReview.text.toString().trim()

        if (newTitle.isEmpty()) {
            Toast.makeText(this, "タイトルは必須です。", Toast.LENGTH_SHORT).show()
            return
        }

        val userId = auth.currentUser?.uid
        if (userId == null) {
            Toast.makeText(this, "ログインが必要です。", Toast.LENGTH_SHORT).show()
            return
        }

        recordId?.let { id ->
            firestore.collection("users").document(userId).collection("records").document(id)
                .update(mapOf("name" to newTitle, "memo" to newMemo))
                .addOnSuccessListener {
                    Toast.makeText(this, "変更を保存しました。", Toast.LENGTH_SHORT).show()
                    record?.name = newTitle
                    record?.memo = newMemo
                    binding.tvRecordTitleHeader.text = newTitle
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "保存に失敗しました: ${e.message}", Toast.LENGTH_LONG).show()
                }
        }
    }

    private fun drawAudioPins(audioPins: List<AudioPin>) {
        audioPins.forEach { pin ->
            val marker = googleMap.addMarker(
                MarkerOptions()
                    .position(LatLng(pin.latitude, pin.longitude))
                    .title("音声メモ")
                    .icon(bitmapDescriptorFromVector(this, R.drawable.ic_audio_pin))
            )
            marker?.tag = pin
        }
    }

    override fun onMarkerClick(marker: Marker): Boolean {
        when (val tag = marker.tag) {
            is AudioPin -> {
                playAudioFromUrl(tag.audioUrl)
                return true // Prevent info window from showing
            }
            is Landmark -> {
                // Let the default behavior handle it (show info window)
                return false
            }
        }
        return false
    } 

    private fun playAudioFromUrl(audioUrl: String) {
        if (mediaPlayer?.isPlaying == true) {
            mediaPlayer?.stop()
        }
        mediaPlayer?.reset()

        if (audioUrl.isNotEmpty()) {
            Toast.makeText(this, "音声再生の準備をしています...", Toast.LENGTH_SHORT).show()
            val storageRef = Firebase.storage.getReferenceFromUrl(audioUrl)
            storageRef.downloadUrl.addOnSuccessListener { uri ->
                try {
                    mediaPlayer = MediaPlayer().apply {
                        setDataSource(this@ReviewActivity, uri)
                        prepareAsync()
                        setOnPreparedListener { player ->
                            Toast.makeText(this@ReviewActivity, "再生開始", Toast.LENGTH_SHORT).show()
                            player.start()
                        }
                        setOnCompletionListener { player ->
                            Toast.makeText(this@ReviewActivity, "再生終了", Toast.LENGTH_SHORT).show()
                            player.release()
                            mediaPlayer = null
                        }
                    }
                } catch (e: Exception) {
                    handlePlaybackError("MediaPlayer setup failed", e)
                }
            }.addOnFailureListener { e ->
                handlePlaybackError("Audio download failed", e)
            }
        } else {
            Toast.makeText(this, "音声URLが無効です。", Toast.LENGTH_SHORT).show()
        }
    }

    private fun handlePlaybackError(logMessage: String, e: Exception? = null) {
        e?.let { Log.e("ReviewActivity", logMessage, it) }
        Toast.makeText(this, "音声の再生に失敗しました。", Toast.LENGTH_SHORT).show()
        mediaPlayer?.release()
        mediaPlayer = null
    }

    override fun onStop() {
        super.onStop()
        mediaPlayer?.release()
        mediaPlayer = null
    }
}
