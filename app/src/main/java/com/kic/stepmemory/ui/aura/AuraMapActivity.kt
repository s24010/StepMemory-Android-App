package com.kic.stepmemory.ui.aura

import android.graphics.Color
import android.location.Location
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.CircleOptions
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.kic.stepmemory.R
import com.kic.stepmemory.data.Record
import com.kic.stepmemory.databinding.ActivityAuraMapBinding
import java.util.concurrent.TimeUnit

// 場所ごとの集計データを保持するデータクラス
data class PlaceStats(
    val location: LatLng,
    var visitCount: Int = 0,
    var totalDurationMinutes: Long = 0,
    var memoCount: Int = 0,
    var auraPoint: Int = 0
)

class AuraMapActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var binding: ActivityAuraMapBinding
    private lateinit var googleMap: GoogleMap
    private lateinit var firestore: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAuraMapBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "場所のアウラ"

        firestore = FirebaseFirestore.getInstance()

        val mapFragment = supportFragmentManager.findFragmentById(R.id.map_aura) as SupportMapFragment
        mapFragment.getMapAsync(this)
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        googleMap.uiSettings.isZoomControlsEnabled = true

        fetchRecordsAndCalculateAura()
    }

    private fun fetchRecordsAndCalculateAura() {
        binding.progressBarAura.visibility = View.VISIBLE

        firestore.collection("records")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { querySnapshot ->
                val records = querySnapshot.toObjects(Record::class.java)
                if (records.isEmpty()) {
                    Toast.makeText(this, "表示できる記録がありません。", Toast.LENGTH_SHORT).show()
                    binding.progressBarAura.visibility = View.GONE
                    return@addOnSuccessListener
                }

                // 場所ごとに記録をグループ化
                val places = groupRecordsByLocation(records)
                // アウラポイントを計算
                calculateAuraPoints(places)
                // 地図にアウラを描画
                drawAuraOnMap(places)

                // 最初の場所にカメラを移動
                places.firstOrNull()?.let {
                    googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(it.location, 14f))
                }

                binding.progressBarAura.visibility = View.GONE
            }
            .addOnFailureListener { e ->
                binding.progressBarAura.visibility = View.GONE
                Toast.makeText(this, "記録の読み込みに失敗しました: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    // 50m以内の記録を同じ場所としてグループ化する
    private fun groupRecordsByLocation(records: List<Record>): List<PlaceStats> {
        val placeStatsList = mutableListOf<PlaceStats>()
        val groupingRadius = 50.0 // 50メートル

        for (record in records) {
            val startPoint = record.pathPoints.firstOrNull()?.let { LatLng(it.latitude, it.longitude) } ?: continue

            var foundGroup = false
            for (stats in placeStatsList) {
                val distance = FloatArray(1)
                Location.distanceBetween(startPoint.latitude, startPoint.longitude, stats.location.latitude, stats.location.longitude, distance)

                if (distance[0] < groupingRadius) {
                    stats.visitCount++
                    stats.totalDurationMinutes += record.durationMs?.let { TimeUnit.MILLISECONDS.toMinutes(it) } ?: 0L
                    if (!record.memo.isNullOrEmpty()) {
                        stats.memoCount++
                    }
                    foundGroup = true
                    break
                }
            }

            if (!foundGroup) {
                val newStats = PlaceStats(
                    location = startPoint,
                    visitCount = 1,
                    totalDurationMinutes = record.durationMs?.let { TimeUnit.MILLISECONDS.toMinutes(it) } ?: 0L,
                    memoCount = if (record.memo.isNullOrEmpty()) 0 else 1
                )
                placeStatsList.add(newStats)
            }
        }
        return placeStatsList
    }

    // 各場所のアウラポイントを計算
    private fun calculateAuraPoints(places: List<PlaceStats>) {
        for (place in places) {
            place.auraPoint = (place.visitCount * 10) + (place.totalDurationMinutes.toInt() * 1) + (place.memoCount * 5)
        }
    }

    // 地図上にアウラ（円）を描画
    private fun drawAuraOnMap(places: List<PlaceStats>) {
        googleMap.clear()
        for (place in places) {
            val (color, radius) = getAuraStyle(place.auraPoint)
            googleMap.addCircle(
                CircleOptions()
                    .center(place.location)
                    .radius(radius)
                    .strokeWidth(2f)
                    .strokeColor(color)
                    .fillColor(Color.argb(70, Color.red(color), Color.green(color), Color.blue(color)))
            )
        }
    }

    // アウラポイントに応じた色と円の半径を返す
    private fun getAuraStyle(auraPoint: Int): Pair<Int, Double> {
        return when {
            auraPoint >= 50 -> Pair(Color.RED, 100.0) // 高いアウラ
            auraPoint >= 20 -> Pair(Color.argb(255, 255, 165, 0), 75.0) // 中くらいのアウラ
            else -> Pair(Color.BLUE, 50.0) // 低いアウラ
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}