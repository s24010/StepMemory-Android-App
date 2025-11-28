package com.kic.stepmemory.ui.heatmap

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.toObjects
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
import kotlin.math.sin

class HeatmapActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var binding: ActivityHeatmapBinding
    private lateinit var googleMap: GoogleMap
    private lateinit var firestore: FirebaseFirestore
    private lateinit var challengeManager: ChallengeManager
    private lateinit var auth: FirebaseAuth

    private var currentHeatmapTileOverlay: TileOverlay? = null
    private val currentMapMarkers = mutableListOf<Marker>()
    private val currentMapPolylines = mutableListOf<Polyline>()

    private var heatmapMode = true
    private var rainyFilterActive = false
    private var nightFilterActive = false
    private var weekendFilterActive = false

    private var userId: String? = null

    private val absoluteMaxScore = 255.0
    private val weatherRainy = "雨"

    companion object {
        private const val GROUPING_RADIUS = 50.0f
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHeatmapBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "マップ表示"

        firestore = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()
        userId = auth.currentUser?.uid

        // ユーザーIDがない場合は、ChallengeManagerを初期化せずに終了
        if (userId == null) {
            Toast.makeText(this, "表示するにはログインが必要です。", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        challengeManager = ChallengeManager(this, userId!!)

        binding.switchHeatmapToggle.isChecked = heatmapMode
        binding.switchHeatmapToggle.setOnCheckedChangeListener { _, isChecked ->
            heatmapMode = isChecked
            Log.d("HeatmapActivity", "Switch toggled. Heatmap mode: $heatmapMode")
            loadAndDrawMapData()
        }

        val mapFragment = supportFragmentManager.findFragmentById(R.id.map_heatmap) as SupportMapFragment
        mapFragment.getMapAsync(this)
    }

    @SuppressLint("PotentialBehaviorOverride")
    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        googleMap.uiSettings.isZoomControlsEnabled = true
        initializeFilterChips()
        loadAndDrawMapData()

        googleMap.setOnMarkerClickListener { marker ->
            if (marker.tag == "landmark") {
                val animator = ValueAnimator.ofFloat(0f, 1f)
                animator.duration = 600 // milliseconds
                animator.interpolator = AccelerateDecelerateInterpolator()
                val startPosition = marker.position
                val jumpHeight = 0.0003 // adjust for visible jump

                animator.addUpdateListener { valueAnimator ->
                    val t = valueAnimator.animatedValue as Float
                    // Simple jump up and down using a sine wave
                    val newLatitude = startPosition.latitude + jumpHeight * sin(t * Math.PI)
                    marker.position = LatLng(newLatitude, startPosition.longitude)
                }
                animator.start()
            }
            // Return false to allow the default behavior (show info window)
            false
        }
    }

    private fun initializeFilterChips() {
        // ユーザーIDがない場合は何もしない
        if (userId == null) return

        CoroutineScope(Dispatchers.Main).launch {
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
        val currentUserId = userId ?: return

        binding.progressBarHeatmap.visibility = View.VISIBLE
        clearMapVisualization()

        Log.d("HeatmapActivity", "Loading map data for user $currentUserId. Heatmap: $heatmapMode, Rainy: $rainyFilterActive, Night: $nightFilterActive, Weekend: $weekendFilterActive")

        CoroutineScope(Dispatchers.IO).launch {
            try {
                var query: Query = firestore.collection("users").document(currentUserId).collection("records")

                if (rainyFilterActive) {
                    query = query.whereEqualTo("weather", weatherRainy)
                    Log.d("HeatmapActivity", "Applying Firestore weather filter: $weatherRainy")
                }

                val allFetchedRecords: List<Record> = query.get().await().toObjects()
                Log.d("HeatmapActivity", "Fetched ${allFetchedRecords.size} records for user $currentUserId.")

                val clientFilteredRecords = allFetchedRecords.filter { record ->
                    val passesNightFilter = if (nightFilterActive) {
                        (record.startTime != 0L) && (Calendar.getInstance().apply { timeInMillis = record.startTime }.get(Calendar.HOUR_OF_DAY) in 19..23 || Calendar.getInstance().apply { timeInMillis = record.startTime }.get(Calendar.HOUR_OF_DAY) in 0..4)
                    } else true

                    val passesWeekendFilter = if (weekendFilterActive) {
                        (record.startTime != 0L) && (Calendar.getInstance().apply { timeInMillis = record.startTime }.get(Calendar.DAY_OF_WEEK) in arrayOf(Calendar.SATURDAY, Calendar.SUNDAY))
                    } else true

                    passesNightFilter && passesWeekendFilter
                }
                Log.d("HeatmapActivity", "Filtered to ${clientFilteredRecords.size} records on client-side.")

                val isAnyFilterActive = rainyFilterActive || nightFilterActive || weekendFilterActive
                val landmarks: List<Landmark> = if (isAnyFilterActive) {
                    emptyList()
                } else {
                    firestore.collection("users").document(currentUserId).collection("landmarks").get().await().toObjects()
                }

                if (clientFilteredRecords.isEmpty() && landmarks.isEmpty()) {
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
                        } else {
                            Toast.makeText(this@HeatmapActivity, "ヒートマップデータを生成できませんでした。", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        drawRecordsAsMarkersAndPaths(clientFilteredRecords, landmarks)
                        if (clientFilteredRecords.isNotEmpty() && clientFilteredRecords.first().pathPoints.isNotEmpty()) {
                            val firstRecordFirstPoint = clientFilteredRecords.first().pathPoints.first()
                            googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(firstRecordFirstPoint.latitude, firstRecordFirstPoint.longitude), 15f))
                        } else if (landmarks.isNotEmpty() && clientFilteredRecords.isEmpty()) {
                            landmarks.firstOrNull()?.let {
                                googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(it.latitude, it.longitude), 13f))
                            }
                        }
                    }
                    binding.progressBarHeatmap.visibility = View.GONE
                }

            } catch (e: Exception) {
                Log.e("HeatmapActivity", "Error loading map data for user $currentUserId", e)
                withContext(Dispatchers.Main) {
                    binding.progressBarHeatmap.visibility = View.GONE
                    Toast.makeText(this@HeatmapActivity, "データの読み込みに失敗しました。", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun calculatePlaceAttachment(records: List<Record>, landmarks: List<Landmark>): Pair<List<WeightedLatLng>, LatLng?> {
        val attachmentScores = mutableMapOf<LatLng, Double>()

        records.forEach { record ->
            // Path Points (Start, Goal, and Intermediate)
            record.pathPoints.forEach { geoPoint ->
                val point = LatLng(geoPoint.latitude, geoPoint.longitude)
                val pathKey = findNearbyKey(point, attachmentScores.keys) ?: point
                attachmentScores[pathKey] = (attachmentScores[pathKey] ?: 0.0) + 2.5 // Score for each path point
            }

            // Audio Pins
            record.audioPins.forEach { audioPin ->
                val point = LatLng(audioPin.latitude, audioPin.longitude)
                val audioKey = findNearbyKey(point, attachmentScores.keys) ?: point
                attachmentScores[audioKey] = (attachmentScores[audioKey] ?: 0.0) + 10.0 // Score for each audio pin
            }
        }

        // Landmarks
        landmarks.forEach { landmark ->
            val point = LatLng(landmark.latitude, landmark.longitude)
            val key = findNearbyKey(point, attachmentScores.keys) ?: point
            attachmentScores[key] = (attachmentScores[key] ?: 0.0) + 20.0 // Landmark score
        }

        val weightedList = attachmentScores.map { (latLng, score) ->
            WeightedLatLng(latLng, score)
        }.toMutableList()

        // Add dummy points to anchor the color scale to an absolute range [0, 255].
        // This prevents the heatmap from using a relative scale based on currently visible points.
        val dummyLocation = LatLng(90.0, 0.0) // A remote location like the North Pole
        weightedList.add(WeightedLatLng(dummyLocation, 0.0)) // Anchor for the minimum score
        weightedList.add(WeightedLatLng(dummyLocation, absoluteMaxScore)) // Anchor for the maximum score

        return Pair(weightedList, attachmentScores.keys.firstOrNull() ?: landmarks.firstOrNull()?.let { LatLng(it.latitude, it.longitude) })
    }

    private fun findNearbyKey(point: LatLng, keys: Set<LatLng>): LatLng? {
        return keys.find {
            val distance = FloatArray(1)
            android.location.Location.distanceBetween(point.latitude, point.longitude, it.latitude, it.longitude, distance)
            distance[0] < GROUPING_RADIUS
        }
    }

    private fun drawHeatmapVisualization(data: List<WeightedLatLng>) {
        if (data.isEmpty()) {
            Log.d("HeatmapActivity", "drawHeatmapVisualization called with empty data.")
            return
        }
        val colors = intArrayOf(
            Color.rgb(0, 0, 255),    // Blue
            Color.rgb(0, 255, 255),  // Cyan
            Color.rgb(0, 255, 0),    // Green
            Color.rgb(255, 255, 0),  // Yellow
            Color.rgb(255, 0, 0)     // Red
        )
        val startPoints = floatArrayOf(0.2f, 0.4f, 0.6f, 0.8f, 1.0f)
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

                val titleTime = SimpleDateFormat("MM/dd HH:mm", Locale.getDefault()).format(Date(record.startTime))
                val snippetDuration = formatDuration(record.durationMs)

                val startMarkerOptions = MarkerOptions()
                    .position(latLngPath.first())
                    .title(record.name ?: "記録: $titleTime")
                    .snippet("期間: $snippetDuration")
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN))
                val marker = googleMap.addMarker(startMarkerOptions)
                if (marker != null) {
                    marker.tag = "record"
                    currentMapMarkers.add(marker)
                }
            }
        }

        landmarks.forEach { landmark ->
            val landmarkMarkerOptions = MarkerOptions()
                .position(LatLng(landmark.latitude, landmark.longitude))
                .title(landmark.title)
                .snippet(landmark.episode)
                .icon(getLandmarkIcon(landmark.iconType))
            val marker = googleMap.addMarker(landmarkMarkerOptions)
            if (marker != null) {
                marker.tag = "landmark"
                currentMapMarkers.add(marker)
            }
        }
    }

    private fun getLandmarkIcon(iconType: String?): BitmapDescriptor {
        return when (iconType) {
            "BRONZE_PIN" -> BitmapDescriptorFactory.fromResource(R.drawable.ic_landmark_bronze_pin)
            "SILVER_PIN" -> BitmapDescriptorFactory.fromResource(R.drawable.ic_landmark_silver_pin)
            "GOLD_PIN" -> BitmapDescriptorFactory.fromResource(R.drawable.ic_landmark_gold_pin)
            "MOON_ICON" -> BitmapDescriptorFactory.fromResource(R.drawable.ic_landmark_moon)
            "FOOD" -> BitmapDescriptorFactory.fromResource(R.drawable.ic_landmark_food) // Custom icon
            "SCENERY" -> BitmapDescriptorFactory.fromResource(R.drawable.ic_landmark_scenery) // Custom icon
            "ONSEN" -> BitmapDescriptorFactory.fromResource(R.drawable.ic_landmark_onsen) // Custom icon
            "SHOPPING" -> BitmapDescriptorFactory.fromResource(R.drawable.ic_landmark_shopping) // Custom icon
            "SIGHTSEEING" -> BitmapDescriptorFactory.fromResource(R.drawable.ic_landmark_sightseeing) // Custom icon
            else -> BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED) // Default
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
            hours > 0 -> String.format(Locale.getDefault(), "%d時間%02d分", hours, minutes)
            minutes > 0 -> String.format(Locale.getDefault(), "%d分%02d秒", minutes, seconds)
            else -> String.format(Locale.getDefault(), "%d秒", seconds)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
