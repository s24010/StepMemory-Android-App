package com.kic.stepmemory.ui.review

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.media.MediaPlayer
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.toObject
import com.kic.stepmemory.R
import com.kic.stepmemory.data.AudioPin
import com.kic.stepmemory.data.Landmark
import com.kic.stepmemory.data.Record
import com.kic.stepmemory.databinding.ActivityReviewBinding
import com.kic.stepmemory.ui.landmark.AddLandmarkBottomSheet
import com.kic.stepmemory.ui.streetview.StreetViewActivity
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

class ReviewActivity : AppCompatActivity(), OnMapReadyCallback, GoogleMap.OnMarkerClickListener {

    private lateinit var binding: ActivityReviewBinding
    private lateinit var googleMap: GoogleMap
    private lateinit var firestore: FirebaseFirestore

    private var recordId: String? = null
    private var currentRecord: Record? = null

    private var placementMarker: Marker? = null

    private var mediaPlayer: MediaPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReviewBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "記録の振り返り"

        firestore = FirebaseFirestore.getInstance()
        recordId = intent.getStringExtra("RECORD_ID")

        val mapFragment = supportFragmentManager.findFragmentById(R.id.map_review) as SupportMapFragment
        mapFragment.getMapAsync(this)

        binding.fabShowStreetView.setOnClickListener {
            recordId?.let { id ->
                val intent = Intent(this, StreetViewActivity::class.java)
                intent.putExtra("RECORD_ID", id)
                startActivity(intent)
            } ?: run {
                Toast.makeText(this, "記録IDがありません。", Toast.LENGTH_SHORT).show()
            }
        }

        binding.fabShowMemo.setOnClickListener {
            currentRecord?.let { record ->
                showMemoDialog(record.name, record.memo)
            } ?: run {
                Toast.makeText(this, "メモが読み込まれていません。", Toast.LENGTH_SHORT).show()
            }
        }

        binding.fabAddLandmarkReview.setOnClickListener {
            if (placementMarker == null) {
                val center = googleMap.cameraPosition.target
                placementMarker = googleMap.addMarker(
                    MarkerOptions()
                        .position(center)
                        .title("ここにランドマークを設置")
                        .draggable(true)
                )
                placementMarker?.showInfoWindow()
                binding.fabAddLandmarkReview.text = "場所を決定"
                binding.fabAddLandmarkReview.setIconResource(android.R.drawable.ic_menu_save)
                Toast.makeText(this, "ピンを長押しして好きな場所に移動してください。", Toast.LENGTH_LONG).show()
            } else {
                val position = placementMarker!!.position
                placementMarker?.remove()
                placementMarker = null
                binding.fabAddLandmarkReview.text = "ランドマーク追加"
                binding.fabAddLandmarkReview.setIconResource(android.R.drawable.ic_menu_add)

                AddLandmarkBottomSheet(position.latitude, position.longitude) { landmark ->
                    saveLandmarkToFirestore(landmark)
                }.show(supportFragmentManager, AddLandmarkBottomSheet.TAG)
            }
        }
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        googleMap.uiSettings.isZoomControlsEnabled = true
        googleMap.setOnMarkerClickListener(this)

        recordId?.let { id ->
            fetchRecordFromFirestore(id)
        } ?: run {
            Toast.makeText(this, "記録IDが指定されていません。", Toast.LENGTH_LONG).show()
            finish()
        }

        fetchAndDrawLandmarks()
    }

    private fun fetchRecordFromFirestore(id: String) {
        firestore.collection("records").document(id).get()
            .addOnSuccessListener { documentSnapshot ->
                val record = documentSnapshot.toObject(Record::class.java)
                record?.let {
                    it.idUUID = documentSnapshot.id
                    currentRecord = it
                    displayRecordOnMap(it)
                    supportActionBar?.title = it.name ?: formatRecordTitle(it)

                    // ▼▼▼ このログを追加 ▼▼▼
                    android.util.Log.d("ReviewActivity", "Fetched audioPins size: ${it.audioPins.size}")
                    for (pin in it.audioPins) {
                        android.util.Log.d("ReviewActivity", "AudioPin: URL=${pin.audioUrl}, Lat=${pin.latitude}, Lng=${pin.longitude}")
                    }
                    // ▲▲▲ ここまで追加 ▲▲▲


                    drawAudioPinsOnMap(it.audioPins)
                } ?: run {
                    Toast.makeText(this, "記録が見つかりませんでした。", Toast.LENGTH_LONG).show()
                    finish()
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "記録の読み込みに失敗しました: ${e.message}", Toast.LENGTH_LONG).show()
                e.printStackTrace()
                finish()
            }
    }

    override fun onMarkerClick(marker: Marker): Boolean {
        (marker.tag as? String)?.let { audioUrl ->
            playAudio(audioUrl)
            return true
        }
        return false
    }

    // Helper method to convert vector drawable to BitmapDescriptor
    private fun getBitmapDescriptorFromVector(context: Context, vectorResId: Int, scaleFactor: Float = 1.0f): BitmapDescriptor? {
        return ContextCompat.getDrawable(context, vectorResId)?.let { vectorDrawable ->
            // 元のサイズにスケールファクターを適用
            val width = (vectorDrawable.intrinsicWidth * scaleFactor).toInt()
            val height = (vectorDrawable.intrinsicHeight * scaleFactor).toInt()

            // 幅や高さが0以下にならないようにガード
            if (width <= 0 || height <= 0) {
                android.util.Log.e("ReviewActivity", "Failed to scale vector drawable, width or height is zero or negative.")
                return BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED) // フォールバック
            }

            vectorDrawable.setBounds(0, 0, width, height)
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            vectorDrawable.draw(canvas)
            BitmapDescriptorFactory.fromBitmap(bitmap)
        }
    }

    private fun drawAudioPinsOnMap(audioPins: List<AudioPin>) {
        android.util.Log.d("ReviewActivity", "drawAudioPinsOnMap called with ${audioPins.size} pins.")
        if (audioPins.isEmpty()) {
            android.util.Log.d("ReviewActivity", "audioPins list is empty, skipping drawing.")
            return
        }
        audioPins.forEach { pin ->
            android.util.Log.d("ReviewActivity", "Drawing pin at Lat: ${pin.latitude}, Lng: ${pin.longitude}")
            val position = LatLng(pin.latitude, pin.longitude)
            // スケールファクターを指定 (例: 1.5倍)
            val markerIcon = getBitmapDescriptorFromVector(this, R.drawable.ic_audio_pin, 1.5f)
            if (markerIcon == null) {
                android.util.Log.e("ReviewActivity", "Failed to get marker icon for audio pin!")
            }
            val marker = googleMap.addMarker(
                MarkerOptions()
                    .position(position)
                    .title("音声メモ")
                    .snippet("タップして再生")
                    .icon(markerIcon ?: BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
            )
            marker?.tag = pin.audioUrl
        }
    }


    private fun playAudio(url: String) {
        stopAudio()

        mediaPlayer = MediaPlayer().apply {
            try {
                setDataSource(url)
                prepareAsync()
                setOnPreparedListener {
                    start()
                    Toast.makeText(this@ReviewActivity, "音声を再生中...", Toast.LENGTH_SHORT).show()
                }
                setOnCompletionListener {
                    stopAudio()
                }
                setOnErrorListener { _, _, _ ->
                    Toast.makeText(this@ReviewActivity, "音声の再生に失敗しました。", Toast.LENGTH_SHORT).show()
                    stopAudio()
                    true
                }
            } catch (e: IOException) {
                e.printStackTrace()
                Toast.makeText(this@ReviewActivity, "音声ファイルが見つかりません。", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun stopAudio() {
        mediaPlayer?.release()
        mediaPlayer = null
    }

    private fun saveLandmarkToFirestore(landmark: Landmark) {
        firestore.collection("landmarks")
            .add(landmark)
            .addOnSuccessListener {
                Toast.makeText(this, "ランドマークを登録しました！", Toast.LENGTH_SHORT).show()
                googleMap.clear() // マップをクリア
                fetchRecordFromFirestore(recordId!!) // Firestoreから記録を再取得して再描画
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "ランドマークの登録に失敗しました: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun fetchAndDrawLandmarks() {
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
                                .icon(getMarkerIcon(it.iconType))
                        )
                    }
                }
            }
    }

    private fun getMarkerIcon(iconType: String): BitmapDescriptor {
        // 必要であれば、他のアイコンにもscaleFactorを適用
        return when (iconType) {
            "BRONZE_PIN" -> getBitmapDescriptorFromVector(this, R.drawable.ic_landmark_bronze_pin, 1.0f) ?: BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)
            "SILVER_PIN" -> getBitmapDescriptorFromVector(this, R.drawable.ic_landmark_silver_pin, 1.0f) ?: BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)
            "GOLD_PIN" -> getBitmapDescriptorFromVector(this, R.drawable.ic_landmark_gold_pin, 1.0f) ?: BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)
            "MOON_ICON" -> getBitmapDescriptorFromVector(this, R.drawable.ic_landmark_moon, 1.0f) ?: BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)
            "FOOD" -> BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ORANGE)
            "SCENERY" -> BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)
            "ONSEN" -> BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_CYAN)
            "SHOPPING" -> BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_MAGENTA)
            "SIGHTSEEING" -> BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_YELLOW)
            else -> BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)
        }
    }

    private fun displayRecordOnMap(record: Record) {
        val pathPoints = record.pathPoints.map { geoPoint ->
            LatLng(geoPoint.latitude, geoPoint.longitude)
        }

        if (pathPoints.isNotEmpty()) {
            val polylineOptions = PolylineOptions()
                .addAll(pathPoints)
                .color(Color.BLUE)
                .width(10f)
            googleMap.addPolyline(polylineOptions)

            googleMap.addMarker(MarkerOptions().position(pathPoints.first()).title("開始地点"))
            googleMap.addMarker(MarkerOptions().position(pathPoints.last()).title("終了地点"))

            val bounds = LatLngBounds.Builder()
            for (point in pathPoints) {
                bounds.include(point)
            }
            googleMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds.build(), 100))
        } else {
            Toast.makeText(this, "この記録にはパスデータがありません。", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showMemoDialog(name: String?, memo: String?) {
        val dialogTitle = if (name.isNullOrEmpty()) "記録メモ" else "記録名: $name"
        val dialogMessage = if (memo.isNullOrEmpty()) "メモはありません。" else memo

        AlertDialog.Builder(this)
            .setTitle(dialogTitle)
            .setMessage(dialogMessage)
            .setPositiveButton("閉じる", null)
            .show()
    }

    private fun formatRecordTitle(record: Record): String {
        val date = record.createdAt ?: Date(record.startTime)
        val dateFormatter = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault())
        return "記録: ${dateFormatter.format(date)}"
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer?.release()
        mediaPlayer = null
    }
}
