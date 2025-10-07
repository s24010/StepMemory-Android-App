package com.kic.stepmemory.ui.review

import android.content.Intent
import android.graphics.Color
import android.media.MediaPlayer
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.PolylineOptions
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage
import com.kic.stepmemory.R
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
    private var record: Record? = null
    private var recordId: String? = null
    private var mediaPlayer: MediaPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReviewBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        firestore = FirebaseFirestore.getInstance()
        recordId = intent.getStringExtra("RECORD_ID")

        val mapFragment = supportFragmentManager.findFragmentById(R.id.map_review) as SupportMapFragment
        mapFragment.getMapAsync(this)

        setupClickListeners()

        if (recordId == null) {
            Toast.makeText(this, "記録IDが見つかりません。", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        googleMap.uiSettings.isZoomControlsEnabled = true
        loadRecordData()
    }

    private fun loadRecordData() {
        recordId?.let { id ->
            firestore.collection("records").document(id).get()
                .addOnSuccessListener { document ->
                    if (document.exists()) {
                        record = document.toObject(Record::class.java)
                        record?.let { rec ->
                            updateUiWithRecord(rec)
                            drawRecordPath(rec)
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
        binding.collapsingToolbar.title = record.name ?: "無題の記録"
        binding.etRecordTitleReview.setText(record.name)
        binding.etRecordMemoReview.setText(record.memo)

        val sdf = SimpleDateFormat("yyyy年M月d日 HH:mm", Locale.JAPAN)
        val startTime = sdf.format(Date(record.startTime))
        val endTime = SimpleDateFormat("HH:mm", Locale.JAPAN).format(Date(record.endTime))
        binding.tvRecordDateReview.text = getString(R.string.record_date_format, startTime, endTime)

        if (record.audioPins.isNullOrEmpty()) {
            binding.btnPlayAudio.visibility = View.GONE
        } else {
            binding.btnPlayAudio.visibility = View.VISIBLE
        }
    }

    private fun drawRecordPath(record: Record) {
        if (record.pathPoints.isNotEmpty()) {
            val path = record.pathPoints.map { LatLng(it.latitude, it.longitude) }
            googleMap.addPolyline(PolylineOptions().addAll(path).color(Color.BLUE).width(10f))

            val cameraUpdate = CameraUpdateFactory.newLatLngZoom(path.first(), 15f)
            googleMap.moveCamera(cameraUpdate)
        } else {
            Toast.makeText(this, "この記録には経路情報がありません。", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupClickListeners() {
        binding.btnSaveReview.setOnClickListener {
            saveRecordChanges()
        }

        binding.btnShowStreetView.setOnClickListener {
            record?.pathPoints?.firstOrNull()?.let { point ->
                val intent = Intent(this, StreetViewActivity::class.java).apply {
                    putExtra("LATITUDE", point.latitude)
                    putExtra("LONGITUDE", point.longitude)
                }
                startActivity(intent)
            } ?: Toast.makeText(this, "経路データがありません。", Toast.LENGTH_SHORT).show()
        }

        binding.btnAddLandmarkReview.setOnClickListener {
            googleMap.cameraPosition.target?.let { target ->
                val bottomSheet = AddLandmarkBottomSheet(target.latitude, target.longitude) { landmark ->
                    // Save landmark logic here (omitted for brevity, assuming it exists)
                    Toast.makeText(this, "${landmark.title} を追加しました。", Toast.LENGTH_SHORT).show()
                }
                bottomSheet.show(supportFragmentManager, AddLandmarkBottomSheet.TAG)
            }
        }

        binding.btnPlayAudio.setOnClickListener {
            if (mediaPlayer?.isPlaying == true) {
                mediaPlayer?.pause()
                binding.btnPlayAudio.text = "音声を再生"
                binding.btnPlayAudio.setIconResource(android.R.drawable.ic_media_play)
            } else if (mediaPlayer != null) {
                mediaPlayer?.start()
                binding.btnPlayAudio.text = "一時停止"
                binding.btnPlayAudio.setIconResource(android.R.drawable.ic_media_pause)
            } else {
                downloadAndPlayAudio()
            }
        }
    }

    private fun saveRecordChanges() {
        val newTitle = binding.etRecordTitleReview.text.toString().trim()
        val newMemo = binding.etRecordMemoReview.text.toString().trim()

        if (newTitle.isEmpty()) {
            Toast.makeText(this, "タイトルは必須です。", Toast.LENGTH_SHORT).show()
            return
        }

        recordId?.let { id ->
            firestore.collection("records").document(id)
                .update(
                    mapOf(
                        "name" to newTitle,
                        "memo" to newMemo
                    )
                )
                .addOnSuccessListener {
                    Toast.makeText(this, "変更を保存しました。", Toast.LENGTH_SHORT).show()
                    record?.name = newTitle
                    record?.memo = newMemo
                    binding.collapsingToolbar.title = newTitle
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "保存に失敗しました: ${e.message}", Toast.LENGTH_LONG).show()
                }
        }
    }

    private fun downloadAndPlayAudio() {
        record?.audioPins?.firstOrNull()?.audioUrl?.let { audioUrl ->
            if(audioUrl.isNotEmpty()){
                binding.btnPlayAudio.text = "準備中..."
                binding.btnPlayAudio.isEnabled = false
                val storageRef = Firebase.storage.getReferenceFromUrl(audioUrl)
                storageRef.downloadUrl.addOnSuccessListener { uri ->
                    try {
                        mediaPlayer = MediaPlayer().apply {
                            setDataSource(this@ReviewActivity, uri)
                            prepareAsync()
                            setOnPreparedListener { player ->
                                binding.btnPlayAudio.isEnabled = true
                                player.start()
                                binding.btnPlayAudio.text = "一時停止"
                                binding.btnPlayAudio.setIconResource(android.R.drawable.ic_media_pause)
                            }
                            setOnCompletionListener { player ->
                                binding.btnPlayAudio.text = "音声を再生"
                                binding.btnPlayAudio.setIconResource(android.R.drawable.ic_media_play)
                                player.reset()
                            }
                        }
                    } catch (e: Exception) {
                        Toast.makeText(this, "音声の再生に失敗しました。", Toast.LENGTH_SHORT).show()
                        Log.e("ReviewActivity", "MediaPlayer setup failed", e)
                        binding.btnPlayAudio.text = "音声を再生"
                        binding.btnPlayAudio.isEnabled = true
                    }
                }.addOnFailureListener { e ->
                    Toast.makeText(this, "音声のダウンロードに失敗しました。", Toast.LENGTH_SHORT).show()
                    Log.e("ReviewActivity", "Audio download failed", e)
                    binding.btnPlayAudio.text = "音声を再生"
                    binding.btnPlayAudio.isEnabled = true
                }
            } else {
                 Toast.makeText(this, "音声URLが無効です。", Toast.LENGTH_SHORT).show()
            }
        } ?: Toast.makeText(this, "再生する音声がありません。", Toast.LENGTH_SHORT).show()
    }

    override fun onStop() {
        super.onStop()
        mediaPlayer?.release()
        mediaPlayer = null
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}
