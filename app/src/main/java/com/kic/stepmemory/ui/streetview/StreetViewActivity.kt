package com.kic.stepmemory.ui.streetview

import android.animation.Animator
import android.animation.ValueAnimator
import android.location.Location
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.maps.OnStreetViewPanoramaReadyCallback
import com.google.android.gms.maps.StreetViewPanorama
import com.google.android.gms.maps.SupportStreetViewPanoramaFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.StreetViewPanoramaCamera
import com.google.android.gms.maps.model.StreetViewSource
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.toObject
import com.kic.stepmemory.R
import com.kic.stepmemory.data.Record
import com.kic.stepmemory.databinding.ActivityStreetViewBinding

class StreetViewActivity : AppCompatActivity(), OnStreetViewPanoramaReadyCallback {

    private lateinit var binding: ActivityStreetViewBinding
    private var recordId: String? = null
    private lateinit var firestore: FirebaseFirestore
    private lateinit var panorama: StreetViewPanorama

    private var pathPoints: List<LatLng> = listOf()
    private var currentIndex = 0

    // --- アニメーション & 自動再生 ---
    private val autoPlayHandler = Handler(Looper.getMainLooper())
    private lateinit var autoPlayRunnable: Runnable
    private var isPlaying = false
    private var animator: ValueAnimator? = null

    companion object {
        private const val AUTO_PLAY_DELAY_MS = 1000L // 次の地点へ移動するまでの待機時間
        private const val MIN_MOVE_DISTANCE_METERS = 5.0f
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStreetViewBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initializeUi()
        recordId = intent.getStringExtra("RECORD_ID")
        firestore = FirebaseFirestore.getInstance()

        setupStreetViewFragment()
        setupClickListeners()
        setupAutoPlayRunnable()
    }

    private fun initializeUi() {
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "ストリートビュー"
        binding.fabPrev.visibility = View.GONE
        binding.fabNext.visibility = View.GONE
        binding.fabPlayPause.visibility = View.GONE
    }

    private fun setupStreetViewFragment() {
        val streetViewPanoramaFragment =
            supportFragmentManager.findFragmentById(R.id.street_view_panorama) as SupportStreetViewPanoramaFragment
        streetViewPanoramaFragment.getStreetViewPanoramaAsync(this)
    }

    private fun setupClickListeners() {
        binding.fabPrev.setOnClickListener {
            stopAutoPlay()
            moveToPreviousPoint()
        }

        binding.fabNext.setOnClickListener {
            stopAutoPlay()
            moveToNextPoint()
        }

        binding.fabPlayPause.setOnClickListener {
            toggleAutoPlay()
        }
    }

    private fun setupAutoPlayRunnable() {
        autoPlayRunnable = Runnable {
            if (isPlaying) {
                moveToNextPoint()
            }
        }
    }

    override fun onStreetViewPanoramaReady(panorama: StreetViewPanorama) {
        this.panorama = panorama
        panorama.isUserNavigationEnabled = false // ユーザーによる手動操作を禁止
        recordId?.let { fetchRecordAndSetupPanorama(it) }
    }

    private fun fetchRecordAndSetupPanorama(id: String) {
        firestore.collection("records").document(id).get()
            .addOnSuccessListener { document ->
                val record = document.toObject<Record>()
                if (record != null && record.pathPoints.isNotEmpty()) {
                    pathPoints = record.pathPoints.map { LatLng(it.latitude, it.longitude) }
                    binding.fabPrev.visibility = View.VISIBLE
                    binding.fabNext.visibility = View.VISIBLE
                    binding.fabPlayPause.visibility = View.VISIBLE
                    updatePanoramaPosition(pathPoints.first()) // 最初の位置へ移動
                } else {
                    Toast.makeText(this, "ストリートビューを表示できる場所がありません。", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "記録の読み込みに失敗しました。", Toast.LENGTH_SHORT).show()
            }
    }

    private fun toggleAutoPlay() {
        isPlaying = !isPlaying
        if (isPlaying) {
            binding.fabPlayPause.setImageResource(android.R.drawable.ic_media_pause)
            moveToNextPoint()
        } else {
            stopAutoPlay()
        }
    }

    private fun stopAutoPlay() {
        isPlaying = false
        binding.fabPlayPause.setImageResource(android.R.drawable.ic_media_play)
        autoPlayHandler.removeCallbacks(autoPlayRunnable)
        animator?.cancel()
    }

    private fun moveToNextPoint() {
        if (currentIndex >= pathPoints.size - 1) {
            Toast.makeText(this, "最後の地点です", Toast.LENGTH_SHORT).show()
            stopAutoPlay()
            return
        }

        val startPoint = pathPoints[currentIndex]
        var nextIndex = currentIndex + 1

        while (nextIndex < pathPoints.size - 1 && calculateDistance(startPoint, pathPoints[nextIndex]) < MIN_MOVE_DISTANCE_METERS) {
            nextIndex++
        }

        val endPoint = pathPoints[nextIndex]
        animatePanorama(startPoint, endPoint)
        currentIndex = nextIndex
    }

    private fun moveToPreviousPoint() {
        if (currentIndex <= 0) {
            Toast.makeText(this, "最初の地点です", Toast.LENGTH_SHORT).show()
            return
        }

        val endPoint = pathPoints[currentIndex]
        var prevIndex = currentIndex - 1

        while (prevIndex > 0 && calculateDistance(pathPoints[prevIndex], endPoint) < MIN_MOVE_DISTANCE_METERS) {
            prevIndex--
        }

        animatePanorama(endPoint, pathPoints[prevIndex], false)
        currentIndex = prevIndex
    }

    private fun animatePanorama(start: LatLng, end: LatLng, autoPlayNext: Boolean = true) {
        animator?.cancel()
        val bearing = calculateBearing(start, end)
        val distance = calculateDistance(start, end)
        val duration = (distance * 150).toLong().coerceIn(1000, 5000)

        val camera = StreetViewPanoramaCamera.Builder().bearing(bearing).build()
        panorama.animateTo(camera, 500) // まず視点移動

        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            this.duration = duration
            interpolator = LinearInterpolator()
            addUpdateListener { animation ->
                val fraction = animation.animatedValue as Float
                val lat = (1 - fraction) * start.latitude + fraction * end.latitude
                val lng = (1 - fraction) * start.longitude + fraction * end.longitude
                panorama.setPosition(LatLng(lat, lng))
            }
            addListener(object : Animator.AnimatorListener {
                override fun onAnimationEnd(animation: Animator) {
                    if (isPlaying && autoPlayNext) {
                        autoPlayHandler.postDelayed(autoPlayRunnable, AUTO_PLAY_DELAY_MS)
                    }
                }
                override fun onAnimationStart(animation: Animator) {}
                override fun onAnimationCancel(animation: Animator) {}
                override fun onAnimationRepeat(animation: Animator) {}
            })
        }
        // 視点移動のアニメーションが終わってから移動を開始
        Handler(Looper.getMainLooper()).postDelayed({
            animator?.start()
        }, 500)
    }

    private fun updatePanoramaPosition(position: LatLng) {
        panorama.setPosition(position, 50, StreetViewSource.OUTDOOR)
    }

    private fun calculateBearing(start: LatLng, end: LatLng): Float {
        val startLocation = Location("").apply { latitude = start.latitude; longitude = start.longitude }
        val endLocation = Location("").apply { latitude = end.latitude; longitude = end.longitude }
        return startLocation.bearingTo(endLocation)
    }

    private fun calculateDistance(start: LatLng, end: LatLng): Float {
        val results = FloatArray(1)
        Location.distanceBetween(start.latitude, start.longitude, end.latitude, end.longitude, results)
        return results[0]
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    override fun onDestroy() {
        super.onDestroy()
        stopAutoPlay()
    }
}