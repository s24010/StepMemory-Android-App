// app/src/main/java/com/kic/stepmemory/ui/review/ReviewActivity.kt

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
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.PolylineOptions
import com.google.firebase.firestore.FirebaseFirestore
import com.kic.stepmemory.R
import com.kic.stepmemory.data.Landmark
import com.kic.stepmemory.data.Record
import com.kic.stepmemory.databinding.ActivityReviewBinding
import com.kic.stepmemory.ui.landmark.AddLandmarkActivity
import com.kic.stepmemory.ui.streetview.StreetViewActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 振り返り画面のアクティビティです。
 * 選択された記録のパスを地図に表示し、メモを参照したり、ランドマークを追加・表示したりします。
 */
class ReviewActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var binding: ActivityReviewBinding
    private lateinit var googleMap: GoogleMap
    private lateinit var firestore: FirebaseFirestore

    private var recordId: String? = null // 履歴画面から渡された記録ID
    private var currentRecord: Record? = null // 読み込んだ記録データ

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

        // 「ランドマークを追加」ボタンのクリックリスナー
        binding.fabAddLandmark.setOnClickListener {
            val centerLatLng = googleMap.cameraPosition.target
            val intent = Intent(this, AddLandmarkActivity::class.java).apply {
                putExtra("LATITUDE", centerLatLng.latitude)
                putExtra("LONGITUDE", centerLatLng.longitude)
            }
            startActivity(intent)
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

        // ランドマークのマーカークリックリスナーを設定
        googleMap.setOnMarkerClickListener { marker ->
            if (marker.tag is Landmark) {
                val landmark = marker.tag as Landmark
                showLandmarkDialog(landmark)
                return@setOnMarkerClickListener true
            }
            return@setOnMarkerClickListener false
        }
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

            val bounds = com.google.android.gms.maps.model.LatLngBounds.Builder()
            for (point in pathPoints) {
                bounds.include(point)
            }
            googleMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds.build(), 100))
        } else {
            Toast.makeText(this, "この記録にはパスデータがありません。", Toast.LENGTH_SHORT).show()
        }

        // ランドマークを取得して表示する関数を呼び出す
        fetchAndDisplayLandmarks()
    }

    /**
     * Firestoreからランドマークのデータを取得し、地図上に表示します。
     */
    private fun fetchAndDisplayLandmarks() {
        firestore.collection("landmarks")
            .get()
            .addOnSuccessListener { documents ->
                for (document in documents) {
                    val landmark = document.toObject(Landmark::class.java).copy(id = document.id)
                    val position = LatLng(landmark.location.latitude, landmark.location.longitude)
                    val marker = googleMap.addMarker(
                        MarkerOptions()
                            .position(position)
                            .title(landmark.name)
                            .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_YELLOW))
                    )
                    marker?.tag = landmark
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "ランドマークの読み込みに失敗しました。", Toast.LENGTH_SHORT).show()
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

    /**
     * ランドマークの詳細をダイアログで表示します。
     */
    private fun showLandmarkDialog(landmark: Landmark) {
        val message = if (landmark.episode.isNotEmpty()) {
            landmark.episode
        } else {
            "このランドマークにはエピソードが登録されていません。"
        }
        AlertDialog.Builder(this)
            .setTitle("🚩 ${landmark.name}")
            .setMessage(message)
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