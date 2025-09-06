package com.kic.stepmemory.ui.heatmap

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.TileOverlayOptions
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.toObjects
import com.google.maps.android.heatmaps.Gradient
import com.google.maps.android.heatmaps.HeatmapTileProvider
import com.google.maps.android.heatmaps.WeightedLatLng
import com.kic.stepmemory.R
import com.kic.stepmemory.data.Landmark
import com.kic.stepmemory.data.Record
import com.kic.stepmemory.databinding.ActivityHeatmapBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import kotlin.math.min

class HeatmapActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var binding: ActivityHeatmapBinding
    private lateinit var googleMap: GoogleMap
    private lateinit var firestore: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHeatmapBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "場所との絆（ヒートマップ）"

        firestore = FirebaseFirestore.getInstance()

        val mapFragment = supportFragmentManager.findFragmentById(R.id.map_heatmap) as SupportMapFragment
        mapFragment.getMapAsync(this)
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        googleMap.uiSettings.isZoomControlsEnabled = true

        generateHeatmap()
    }

    private fun generateHeatmap() {
        binding.progressBarHeatmap.visibility = View.VISIBLE
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val records = firestore.collection("records").get().await().toObjects<Record>()
                val landmarks = firestore.collection("landmarks").get().await().toObjects<Landmark>()

                if (records.isEmpty() && landmarks.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@HeatmapActivity, "表示できるデータがありません。", Toast.LENGTH_SHORT).show()
                        binding.progressBarHeatmap.visibility = View.GONE
                    }
                    return@launch
                }

                // ★★★ 愛着度の計算結果を、データ本体と最初の座標のペアで受け取るように変更 ★★★
                val (weightedData, firstLatLng) = calculatePlaceAttachment(records, landmarks)

                withContext(Dispatchers.Main) {
                    if (weightedData.isNotEmpty() && firstLatLng != null) {
                        drawHeatmap(weightedData)
                        // ★★★ 受け取った最初の座標にカメラを移動 ★★★
                        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(firstLatLng, 13f))
                    }
                    binding.progressBarHeatmap.visibility = View.GONE
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.progressBarHeatmap.visibility = View.GONE
                    Toast.makeText(this@HeatmapActivity, "データの読み込みに失敗しました: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // ★★★ 戻り値を、ヒートマップデータと最初の座標のペア (`Pair`) に変更 ★★★
    private fun calculatePlaceAttachment(records: List<Record>, landmarks: List<Landmark>): Pair<List<WeightedLatLng>, LatLng?> {
        val attachmentScores = mutableMapOf<LatLng, Double>()
        val groupingRadius = 50.0

        records.forEach { record ->
            val point = record.pathPoints.firstOrNull()?.let { LatLng(it.latitude, it.longitude) } ?: return@forEach
            val durationMinutes = record.durationMs?.let { TimeUnit.MILLISECONDS.toMinutes(it) } ?: 0L
            val key = findNearbyKey(point, attachmentScores.keys, groupingRadius) ?: point
            val currentScore = attachmentScores.getOrDefault(key, 0.0)
            attachmentScores[key] = currentScore + 5.0 + (durationMinutes * 0.1)
        }

        landmarks.forEach { landmark ->
            val point = LatLng(landmark.latitude, landmark.longitude)
            val key = findNearbyKey(point, attachmentScores.keys, groupingRadius) ?: point
            val currentScore = attachmentScores.getOrDefault(key, 0.0)
            attachmentScores[key] = currentScore + 30.0
        }
        records.filter { !it.memo.isNullOrBlank() }.forEach { record ->
            val point = record.pathPoints.firstOrNull()?.let { LatLng(it.latitude, it.longitude) } ?: return@forEach
            val key = findNearbyKey(point, attachmentScores.keys, groupingRadius) ?: point
            val currentScore = attachmentScores.getOrDefault(key, 0.0)
            attachmentScores[key] = currentScore + 10.0
        }

        val maxScore = attachmentScores.values.maxOrNull() ?: 1.0
        val weightedList = attachmentScores.map { (latLng, score) ->
            val intensity = min(score / maxScore, 1.0)
            WeightedLatLng(latLng, intensity)
        }

        // ★★★ 計算結果のリストと、最初の座標をペアで返す ★★★
        return Pair(weightedList, attachmentScores.keys.firstOrNull())
    }

    private fun findNearbyKey(point: LatLng, keys: Set<LatLng>, radius: Double): LatLng? {
        return keys.find {
            val distance = FloatArray(1)
            android.location.Location.distanceBetween(point.latitude, point.longitude, it.latitude, it.longitude, distance)
            distance[0] < radius
        }
    }

    private fun drawHeatmap(data: List<WeightedLatLng>) {
        if (data.isEmpty()) return

        val colors = intArrayOf(
            Color.rgb(102, 225, 0),
            Color.rgb(255, 0, 0)
        )
        val startPoints = floatArrayOf(0.2f, 1f)
        val gradient = Gradient(colors, startPoints)

        val provider = HeatmapTileProvider.Builder()
            .weightedData(data)
            .radius(50)
            .gradient(gradient)
            .build()

        googleMap.addTileOverlay(TileOverlayOptions().tileProvider(provider))
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}