package com.kic.stepmemory.ui.heatmap

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.toObjects
import com.google.maps.android.heatmaps.Gradient
import com.google.maps.android.heatmaps.HeatmapTileProvider
import com.google.maps.android.heatmaps.WeightedLatLng
import com.kic.stepmemory.R
import com.kic.stepmemory.challenge.ChallengeManager
import com.kic.stepmemory.data.Landmark
import com.kic.stepmemory.data.Record
import com.kic.stepmemory.databinding.ActivityHeatmapBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.min

class HeatmapActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var binding: ActivityHeatmapBinding
    private lateinit var googleMap: GoogleMap
    private lateinit var firestore: FirebaseFirestore
    private lateinit var challengeManager: ChallengeManager

    private var currentHeatmapTileOverlay: TileOverlay? = null
    private val currentMapMarkers = mutableListOf<Marker>()
    private val currentMapPolylines = mutableListOf<Polyline>()

    private var heatmapMode = true
    private var rainyFilterActive = false
    private var nightFilterActive = false
    private var weekendFilterActive = false

    private val ABSOLUTE_MAX_SCORE = 255.0
    private val WEATHER_RAINY = "雨"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHeatmapBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "マップ表示"

        firestore = FirebaseFirestore.getInstance()
        challengeManager = ChallengeManager(this)

        binding.switchHeatmapToggle.isChecked = heatmapMode
        binding.switchHeatmapToggle.setOnCheckedChangeListener { _, isChecked ->
            heatmapMode = isChecked
            Log.d("HeatmapActivity", "Switch toggled. Heatmap mode: $heatmapMode")
            loadAndDrawMapData()
        }

        val mapFragment = supportFragmentManager.findFragmentById(R.id.map_heatmap) as SupportMapFragment
        mapFragment.getMapAsync(this)
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        googleMap.uiSettings.isZoomControlsEnabled = true
        initializeFilterChips()
        loadAndDrawMapData()
    }

    private fun initializeFilterChips() {
        if (challengeManager.isRainyDayFilterUnlocked()) {
            binding.chipFilterRainy.visibility = View.VISIBLE
            binding.chipFilterRainy.setOnCheckedChangeListener { _, isChecked ->
                rainyFilterActive = isChecked
                Log.d("HeatmapActivity", "Rainy filter: $rainyFilterActive")
                loadAndDrawMapData()
            }
        } else {
            binding.chipFilterRainy.visibility = View.GONE
        }

        if (challengeManager.isNightWalkFilterUnlocked()) {
            binding.chipFilterNight.visibility = View.VISIBLE
            binding.chipFilterNight.setOnCheckedChangeListener { _, isChecked ->
                nightFilterActive = isChecked
                Log.d("HeatmapActivity", "Night filter: $nightFilterActive")
                loadAndDrawMapData()
            }
        } else {
            binding.chipFilterNight.visibility = View.GONE
        }

        if (challengeManager.isWeekendFilterUnlocked()) {
            binding.chipFilterWeekend.visibility = View.VISIBLE
            binding.chipFilterWeekend.setOnCheckedChangeListener { _, isChecked ->
                weekendFilterActive = isChecked
                Log.d("HeatmapActivity", "Weekend filter: $weekendFilterActive")
                loadAndDrawMapData()
            }
        } else {
            binding.chipFilterWeekend.visibility = View.GONE
        }
    }

    private fun clearMapVisualization() {
        currentHeatmapTileOverlay?.remove()
        currentHeatmapTileOverlay = null
        currentMapMarkers.forEach { it.remove() }
        currentMapMarkers.clear()
        currentMapPolylines.forEach { it.remove() }
        currentMapPolylines.clear()
        Log.d("HeatmapActivity", "Map visualization completely cleared.")
    }

    private fun loadAndDrawMapData() {
        binding.progressBarHeatmap.visibility = View.VISIBLE
        clearMapVisualization()

        Log.d("HeatmapActivity", "Loading map data. Heatmap: $heatmapMode, Rainy: $rainyFilterActive, Night: $nightFilterActive, Weekend: $weekendFilterActive")

        CoroutineScope(Dispatchers.IO).launch {
            try {
                var query: Query = firestore.collection("records")

                if (rainyFilterActive) {
                    query = query.whereEqualTo("weather", WEATHER_RAINY)
                    Log.d("HeatmapActivity", "Applying Firestore weather filter: $WEATHER_RAINY")
                }

                val allFetchedRecords = query.get().await().toObjects<Record>()
                Log.d("HeatmapActivity", "Fetched ${allFetchedRecords.size} records after Firestore query.")

                val clientFilteredRecords = allFetchedRecords.filter { record ->
                    var passesNightFilter = true
                    if (nightFilterActive) {
                        if (record.startTime != null) {
                            val calendar = Calendar.getInstance().apply { timeInMillis = record.startTime!! }
                            val hour = calendar.get(Calendar.HOUR_OF_DAY)
                            passesNightFilter = hour >= 19 || hour < 5
                        } else {
                            passesNightFilter = false
                        }
                    }

                    var passesWeekendFilter = true
                    if (weekendFilterActive) {
                        if (record.startTime != null) {
                            val calendar = Calendar.getInstance().apply { timeInMillis = record.startTime!! }
                            val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
                            passesWeekendFilter = dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY
                        } else {
                            passesWeekendFilter = false
                        }
                    }
                    passesNightFilter && passesWeekendFilter
                }
                Log.d("HeatmapActivity", "Filtered to ${clientFilteredRecords.size} records on client-side.")

                val landmarks = firestore.collection("landmarks").get().await().toObjects<Landmark>()

                if (clientFilteredRecords.isEmpty() && (!heatmapMode || landmarks.isEmpty())) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@HeatmapActivity, "表示できるデータがありません。", Toast.LENGTH_SHORT).show()
                        binding.progressBarHeatmap.visibility = View.GONE
                    }
                    return@launch
                }

                withContext(Dispatchers.Main) {
                    if (heatmapMode) {
                        val (weightedData, firstLatLng) = calculatePlaceAttachment(clientFilteredRecords, landmarks)
                        if (weightedData.isNotEmpty()) {
                            drawHeatmapVisualization(weightedData)
                            firstLatLng?.let {
                                if (googleMap.cameraPosition.zoom < 10f) {
                                    googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(it, 13f))
                                }
                            }
                            Log.d("HeatmapActivity", "Heatmap drawn with ${weightedData.size} points.")
                        } else {
                            Toast.makeText(this@HeatmapActivity, "ヒートマップデータを生成できませんでした。", Toast.LENGTH_SHORT).show()
                            Log.d("HeatmapActivity", "No weighted data to draw heatmap.")
                        }
                    } else {
                        drawRecordsAsMarkersAndPaths(clientFilteredRecords, landmarks)
                        if (clientFilteredRecords.isNotEmpty() && clientFilteredRecords.first().pathPoints.isNotEmpty()) {
                            val firstRecordFirstPoint = clientFilteredRecords.first().pathPoints.first()
                            googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(firstRecordFirstPoint.latitude, firstRecordFirstPoint.longitude), 15f))
                        } else if (landmarks.isNotEmpty() && clientFilteredRecords.isEmpty()) {
                            val firstLandmark = landmarks.first()
                            googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(firstLandmark.latitude, firstLandmark.longitude), 13f))
                        } else if (clientFilteredRecords.isEmpty() && landmarks.isEmpty()) {
                            Toast.makeText(this@HeatmapActivity, "表示する記録もランドマークもありません。", Toast.LENGTH_SHORT).show()
                        }
                    }
                    binding.progressBarHeatmap.visibility = View.GONE
                }

            } catch (e: Exception) {
                Log.e("HeatmapActivity", "Error loading map data", e)
                withContext(Dispatchers.Main) {
                    binding.progressBarHeatmap.visibility = View.GONE
                    Toast.makeText(this@HeatmapActivity, "データの読み込みに失敗: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun calculatePlaceAttachment(records: List<Record>, landmarks: List<Landmark>): Pair<List<WeightedLatLng>, LatLng?> {
        val attachmentScores = mutableMapOf<LatLng, Double>()
        val groupingRadius = 50.0

        records.forEach { record ->
            val path = record.pathPoints
            if (path.isEmpty()) return@forEach

            val startPoint = LatLng(path.first().latitude, path.first().longitude)
            val visitKey = findNearbyKey(startPoint, attachmentScores.keys, groupingRadius) ?: startPoint
            attachmentScores[visitKey] = (attachmentScores[visitKey] ?: 0.0) + 2.0

            path.forEach { geoPoint ->
                val point = LatLng(geoPoint.latitude, geoPoint.longitude)
                val pathKey = findNearbyKey(point, attachmentScores.keys, groupingRadius) ?: point
                attachmentScores[pathKey] = (attachmentScores[pathKey] ?: 0.0) + 0.2
            }
            if (!record.memo.isNullOrBlank()) {
                val memoKey = findNearbyKey(startPoint, attachmentScores.keys, groupingRadius) ?: startPoint
                attachmentScores[memoKey] = (attachmentScores[memoKey] ?: 0.0) + 5.0
            }
        }

        landmarks.forEach { landmark ->
            val point = LatLng(landmark.latitude, landmark.longitude)
            val key = findNearbyKey(point, attachmentScores.keys, groupingRadius) ?: point
            attachmentScores[key] = (attachmentScores[key] ?: 0.0) + 10.0
        }

        val weightedList = attachmentScores.map { (latLng, score) ->
            val intensity = min(score / ABSOLUTE_MAX_SCORE, 1.0)
            WeightedLatLng(latLng, intensity)
        }
        return Pair(weightedList, attachmentScores.keys.firstOrNull() ?: landmarks.firstOrNull()?.let { LatLng(it.latitude, it.longitude) })
    }

    private fun findNearbyKey(point: LatLng, keys: Set<LatLng>, radius: Double): LatLng? {
        return keys.find {
            val distance = FloatArray(1)
            android.location.Location.distanceBetween(point.latitude, point.longitude, it.latitude, it.longitude, distance)
            distance[0] < radius
        }
    }

    private fun drawHeatmapVisualization(data: List<WeightedLatLng>) {
        if (data.isEmpty()) {
            Log.d("HeatmapActivity", "drawHeatmapVisualization called with empty data.")
            return
        }
        val colors = intArrayOf(
            Color.rgb(0, 0, 255), Color.rgb(0, 255, 255), Color.rgb(0, 255, 0),
            Color.rgb(255, 255, 0), Color.rgb(255, 165, 0), Color.rgb(255, 0, 0)
        )
        val startPoints = floatArrayOf(0.1f, 0.25f, 0.4f, 0.6f, 0.8f, 1.0f)
        val gradient = Gradient(colors, startPoints)
        val provider = HeatmapTileProvider.Builder().weightedData(data).radius(50).gradient(gradient).opacity(0.7).build()
        currentHeatmapTileOverlay = googleMap.addTileOverlay(TileOverlayOptions().tileProvider(provider))
        Log.d("HeatmapActivity", "Heatmap tile overlay added.")
    }

    private fun drawRecordsAsMarkersAndPaths(records: List<Record>, landmarks: List<Landmark>) {
        if (records.isEmpty() && landmarks.isEmpty()) {
            Toast.makeText(this, "表示する記録もランドマークもありません。", Toast.LENGTH_SHORT).show()
            Log.d("HeatmapActivity", "No records or landmarks to draw as markers/paths.")
            return
        }

        Log.d("HeatmapActivity", "Drawing ${records.size} records and ${landmarks.size} landmarks as markers/paths.")

        records.forEach { record ->
            if (record.pathPoints.isNotEmpty()) {
                val latLngPath = record.pathPoints.map { LatLng(it.latitude, it.longitude) }
                if (latLngPath.size > 1) {
                    val polylineOptions = PolylineOptions().addAll(latLngPath).color(Color.CYAN).width(12f).zIndex(1f)
                    currentMapPolylines.add(googleMap.addPolyline(polylineOptions))
                }

                val titleTime = record.startTime?.let { SimpleDateFormat("MM/dd HH:mm", Locale.getDefault()).format(Date(it)) } ?: "日時不明"
                val snippetDuration = formatDuration(record.durationMs)

                val startMarkerOptions = MarkerOptions()
                    .position(latLngPath.first())
                    .title(record.name ?: "記録: $titleTime")
                    .snippet("期間: $snippetDuration")
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN))
                currentMapMarkers.add(googleMap.addMarker(startMarkerOptions)!!)
            }
        }

        landmarks.forEach { landmark ->
            val landmarkMarkerOptions = MarkerOptions()
                .position(LatLng(landmark.latitude, landmark.longitude))
                .title(landmark.title)
                .snippet(landmark.episode ?: "")
                .icon(getLandmarkIcon(landmark.iconType))
            currentMapMarkers.add(googleMap.addMarker(landmarkMarkerOptions)!!)
        }
    }

    private fun getLandmarkIcon(iconType: String?): BitmapDescriptor {
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

    private fun formatDuration(millis: Long?): String {
        if (millis == null) {
            return "測定不能"
        }
        val seconds = (millis / 1000) % 60
        val minutes = (millis / (1000 * 60)) % 60
        val hours = (millis / (1000 * 60 * 60))
        return when {
            hours > 0 -> String.format("%d時間%02d分", hours, minutes)
            minutes > 0 -> String.format("%d分%02d秒", minutes, seconds)
            else -> String.format("%d秒", seconds)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
