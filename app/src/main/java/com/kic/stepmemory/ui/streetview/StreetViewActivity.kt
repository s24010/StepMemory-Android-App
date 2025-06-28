package com.kic.stepmemory.ui.streetview

import android.location.Location
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.maps.OnStreetViewPanoramaReadyCallback
import com.google.android.gms.maps.StreetViewPanorama
import com.google.android.gms.maps.SupportStreetViewPanoramaFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.StreetViewSource
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.toObject
import com.kic.stepmemory.R
import com.kic.stepmemory.data.Record
import com.kic.stepmemory.databinding.ActivityStreetViewBinding // ★ ViewBindingをインポート
import java.util.Date

class StreetViewActivity : AppCompatActivity(), OnStreetViewPanoramaReadyCallback {

    private lateinit var binding: ActivityStreetViewBinding // ★ ViewBindingのインスタンス
    private var recordId: String? = null
    private lateinit var firestore: FirebaseFirestore
    private lateinit var panorama: StreetViewPanorama

    private var pathPoints: List<LatLng> = listOf()
    private var currentIndex = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // ★ ViewBinding を使用するように変更
        binding = ActivityStreetViewBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "ストリートビュー"

        recordId = intent.getStringExtra("RECORD_ID")
        firestore = FirebaseFirestore.getInstance()

        val streetViewPanoramaFragment =
            supportFragmentManager.findFragmentById(R.id.street_view_panorama) as SupportStreetViewPanoramaFragment
        streetViewPanoramaFragment.getStreetViewPanoramaAsync(this)

        // 「前へ」ボタンの処理
        binding.fabPrev.setOnClickListener {
            if (currentIndex > 0) {
                currentIndex--
                updatePanoramaPosition()
            } else {
                Toast.makeText(this, "最初の地点です", Toast.LENGTH_SHORT).show()
            }
        }

        // 「次へ」ボタンの処理
        binding.fabNext.setOnClickListener {
            if (currentIndex < pathPoints.size - 1) {
                currentIndex++
                updatePanoramaPosition()
            } else {
                Toast.makeText(this, "最後の地点です", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onStreetViewPanoramaReady(panorama: StreetViewPanorama) {
        this.panorama = panorama
        // ★ ユーザーによる移動やズームを無効化
        panorama.isUserNavigationEnabled = false
        panorama.isZoomGesturesEnabled = false
        panorama.isPanningGesturesEnabled = false

        recordId?.let { id ->
            fetchRecordAndSetupPanorama(id)
        }
    }

    private fun fetchRecordAndSetupPanorama(id: String) {
        firestore.collection("records").document(id).get()
            .addOnSuccessListener { document ->
                val record = document.toObject<Record>()
                if (record != null && record.pathPoints.isNotEmpty()) {
                    pathPoints = record.pathPoints.map { LatLng(it.latitude, it.longitude) }
                    binding.fabPrev.visibility = View.VISIBLE
                    binding.fabNext.visibility = View.VISIBLE
                    updatePanoramaPosition()
                } else {
                    Toast.makeText(this, "ストリートビューを表示できる場所がありません。", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "記録の読み込みに失敗しました。", Toast.LENGTH_SHORT).show()
            }
    }

    // ★ ストリートビューの位置と向きを更新する関数
    private fun updatePanoramaPosition() {
        if (pathPoints.isNotEmpty()) {
            val currentPosition = pathPoints[currentIndex]
            panorama.setPosition(currentPosition, 50, StreetViewSource.OUTDOOR)

            // 次の地点があれば、その方向を向くようにカメラを調整
            if (currentIndex < pathPoints.size - 1) {
                val nextPosition = pathPoints[currentIndex + 1]
                val bearing = calculateBearing(currentPosition, nextPosition)
                val panoramaCamera = com.google.android.gms.maps.model.StreetViewPanoramaCamera.Builder()
                    .bearing(bearing)
                    .build()
                panorama.animateTo(panoramaCamera, 1000)
            }
        }
    }

    // ★ 2点間の角度を計算するヘルパー関数
    private fun calculateBearing(start: LatLng, end: LatLng): Float {
        val startLocation = Location("").apply {
            latitude = start.latitude
            longitude = start.longitude
        }
        val endLocation = Location("").apply {
            latitude = end.latitude
            longitude = end.longitude
        }
        return startLocation.bearingTo(endLocation)
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}