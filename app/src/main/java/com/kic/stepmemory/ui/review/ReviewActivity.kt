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
import com.kic.stepmemory.R
import com.kic.stepmemory.data.AudioPin
import com.kic.stepmemory.data.Landmark
import com.kic.stepmemory.data.Record
import com.kic.stepmemory.databinding.ActivityReviewBinding
import com.kic.stepmemory.ui.landmark.AddLandmarkBottomSheet
import com.kic.stepmemory.ui.streetview.StreetViewActivity
import java.text.SimpleDateFormat
import java.util.*

class ReviewActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var binding: ActivityReviewBinding
    private lateinit var googleMap: GoogleMap
    private lateinit var firestore: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private var record: Record? = null
    private var recordId: String? = null
    private var userId: String? = null
    private var mediaPlayer: MediaPlayer? = null
    private lateinit var bottomSheetBehavior: BottomSheetBehavior<View>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReviewBinding.inflate(layoutInflater)
        setContentView(binding.root)

        firestore = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()
        userId = auth.currentUser?.uid
        recordId = intent.getStringExtra("RECORD_ID")

        val mapFragment = supportFragmentManager.findFragmentById(R.id.map_review) as SupportMapFragment
        mapFragment.getMapAsync(this)

        setupBottomSheet()
        setupClickListeners()
        handleBackButton()

        if (recordId == null || userId == null) {
            Toast.makeText(this, "記録IDまたはユーザー情報が見つかりません。", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        googleMap.uiSettings.isZoomControlsEnabled = true
        googleMap.setInfoWindowAdapter(CustomInfoWindowAdapter(this))
        loadRecordData()

        googleMap.setOnMarkerClickListener { marker ->
            when (val tag = marker.tag) {
                is Landmark -> {
                    // Let the InfoWindowAdapter handle it
                    false
                }
                is AudioPin -> {
                    playAudioFromUrl(tag.audioUrl)
                    true // Consume the event
                }
                else -> {
                    // For Start/Goal markers, just show the default window
                    false
                }
            }
        }
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
        val currentUserId = userId ?: return
        val currentRecordId = recordId ?: return

        firestore.collection("users").document(currentUserId).collection("records").document(currentRecordId).get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    record = document.toObject(Record::class.java)
                    record?.let {
                        updateUiWithRecord(it)
                        drawRecordPath(it)
                        drawAudioPins(it)
                        loadLandmarks(currentRecordId)
                    }
                } else {
                    Toast.makeText(this, "記録が見つかりませんでした。", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "記録の読み込みに失敗: ${e.message}", Toast.LENGTH_LONG).show()
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

    private fun drawAudioPins(record: Record) {
        record.audioPins.forEach { audioPin ->
            val position = LatLng(audioPin.latitude, audioPin.longitude)
            val marker = googleMap.addMarker(
                MarkerOptions()
                    .position(position)
                    .title("音声ジャーナル")
                    .icon(bitmapDescriptorFromVector(this, R.drawable.ic_audio_journal_pin))
            )
            marker?.tag = audioPin
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
        val currentUserId = userId ?: return
        recordId?.let {
            val landmarkWithRecordId = landmark.copy(recordId = it, userId = currentUserId)
            firestore.collection("users").document(currentUserId).collection("landmarks").add(landmarkWithRecordId)
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
                .icon(getMarkerIcon(landmark.iconType))
        )
        marker?.tag = landmark
    }

    private fun loadLandmarks(recordId: String) {
        val currentUserId = userId ?: return
        firestore.collection("users").document(currentUserId).collection("landmarks").whereEqualTo("recordId", recordId).get()
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
        val hue = when (iconType) {
            "FOOD" -> BitmapDescriptorFactory.HUE_ORANGE
            "SCENERY" -> BitmapDescriptorFactory.HUE_GREEN
            "ONSEN" -> BitmapDescriptorFactory.HUE_CYAN
            "SHOPPING" -> BitmapDescriptorFactory.HUE_MAGENTA
            "SIGHTSEEING" -> BitmapDescriptorFactory.HUE_YELLOW
            "BRONZE_PIN", "SILVER_PIN", "GOLD_PIN", "MOON_ICON" -> BitmapDescriptorFactory.HUE_AZURE
            else -> BitmapDescriptorFactory.HUE_RED // Default for PIN and others
        }
        return BitmapDescriptorFactory.defaultMarker(hue)
    }

    private fun saveRecordChanges() {
        val currentUserId = userId ?: return
        val currentRecordId = recordId ?: return

        val newTitle = binding.etRecordTitleReview.text.toString().trim()
        val newMemo = binding.etRecordMemoReview.text.toString().trim()

        if (newTitle.isEmpty()) {
            Toast.makeText(this, "タイトルは必須です。", Toast.LENGTH_SHORT).show()
            return
        }

        firestore.collection("users").document(currentUserId).collection("records").document(currentRecordId)
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

    private fun playAudioFromUrl(audioUrl: String) {
        // Stop any currently playing audio
        mediaPlayer?.release()
        mediaPlayer = null

        if (audioUrl.isEmpty()) {
            Toast.makeText(this, "音声URLが無効です。", Toast.LENGTH_SHORT).show()
            return
        }

        Toast.makeText(this, "音声を再生します...", Toast.LENGTH_SHORT).show()

        try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(audioUrl)
                prepareAsync()
                setOnPreparedListener { player ->
                    player.start()
                    Toast.makeText(this@ReviewActivity, "再生中", Toast.LENGTH_SHORT).show()
                }
                setOnCompletionListener { player ->
                    Toast.makeText(this@ReviewActivity, "再生終了", Toast.LENGTH_SHORT).show()
                    player.release()
                    mediaPlayer = null
                }
                setOnErrorListener { _, _, _ ->
                    Toast.makeText(this@ReviewActivity, "音声の再生に失敗しました。", Toast.LENGTH_SHORT).show()
                    true
                }
            }
        } catch (e: Exception) {
            Log.e("ReviewActivity", "MediaPlayer setup failed", e)
            Toast.makeText(this, "音声の再生に失敗しました。", Toast.LENGTH_SHORT).show()
        }
    }


    override fun onStop() {
        super.onStop()
        mediaPlayer?.release()
        mediaPlayer = null
    }
}
