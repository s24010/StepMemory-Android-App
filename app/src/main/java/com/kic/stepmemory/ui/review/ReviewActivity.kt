package com.kic.stepmemory.ui.review

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.PolylineOptions
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.toObject
import com.kic.stepmemory.R
import com.kic.stepmemory.data.Landmark
import com.kic.stepmemory.data.Record
import com.kic.stepmemory.databinding.ActivityReviewBinding
import com.kic.stepmemory.ui.landmark.AddLandmarkBottomSheet
import com.kic.stepmemory.ui.streetview.StreetViewActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ReviewActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var binding: ActivityReviewBinding
    private lateinit var googleMap: GoogleMap
    private lateinit var firestore: FirebaseFirestore

    private var recordId: String? = null
    private var currentRecord: Record? = null

    private var placementMarker: Marker? = null

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

    private fun saveLandmarkToFirestore(landmark: Landmark) {
        firestore.collection("landmarks")
            .add(landmark)
            .addOnSuccessListener {
                Toast.makeText(this, "ランドマークを登録しました！", Toast.LENGTH_SHORT).show()
                googleMap.clear()
                displayRecordOnMap(currentRecord!!)
                fetchAndDrawLandmarks()
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
        val dateFormatter = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault())
        val date = Date(record.startTime)
        return "記録: ${dateFormatter.format(date)}"
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}