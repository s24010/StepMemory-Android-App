package com.kic.stepmemory.ui.streetview

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.toObject
import com.google.gson.Gson
import com.kic.stepmemory.data.Record
import com.kic.stepmemory.databinding.ActivityStreetViewBinding

class StreetViewActivity : AppCompatActivity() {

    private lateinit var binding: ActivityStreetViewBinding
    private var recordId: String? = null
    private lateinit var firestore: FirebaseFirestore

    private var pathPoints: List<LatLng> = listOf()
    private var currentIndex = 0

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStreetViewBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "ストリートビュー"

        recordId = intent.getStringExtra("RECORD_ID")
        firestore = FirebaseFirestore.getInstance()

        // WebViewの設定
        binding.streetViewWebview.settings.javaScriptEnabled = true
        binding.streetViewWebview.addJavascriptInterface(WebAppInterface(this), "Android")
        binding.streetViewWebview.loadUrl("file:///android_asset/streetview.html")

        // WebChromeClientを設定して、JavaScriptのalert()を扱えるようにする
        binding.streetViewWebview.webChromeClient = object : WebChromeClient() {
            override fun onJsAlert(view: WebView?, url: String?, message: String?, result: android.webkit.JsResult?): Boolean {
                AlertDialog.Builder(this@StreetViewActivity)
                    .setMessage(message)
                    .setPositiveButton(android.R.string.ok) { _, _ -> result?.confirm() }
                    .setCancelable(false)
                    .create()
                    .show()
                return true
            }
        }

        binding.streetViewWebview.loadUrl("file:///android_asset/streetview.html")

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

        // 「撮影日時を表示」ボタンの処理
        binding.fabShowDate.setOnClickListener {
            // WebView内のJavaScript関数 'showPanoramaDate()' を呼び出す
            binding.streetViewWebview.evaluateJavascript("javascript:showPanoramaDate()", null)
        }

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
                    // WebViewの準備ができたら最初の位置を更新
                    binding.streetViewWebview.webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            updatePanoramaPosition()
                        }
                    }
                } else {
                    Toast.makeText(this, "ストリートビューを表示できる場所がありません。", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "記録の読み込みに失敗しました。", Toast.LENGTH_SHORT).show()
            }
    }

    private fun updatePanoramaPosition() {
        if (pathPoints.isNotEmpty()) {
            val currentPosition = pathPoints[currentIndex]
            val nextPosition = if (currentIndex < pathPoints.size - 1) pathPoints[currentIndex + 1] else null
            val lat = currentPosition.latitude
            val lng = currentPosition.longitude
            var heading = 0.0
            if(nextPosition != null){
                heading = calculateBearing(currentPosition, nextPosition).toDouble()
            }

            // JavaScriptの関数を呼び出す
            binding.streetViewWebview.evaluateJavascript("javascript: setPanorama($lat, $lng, $heading)", null)
        }
    }

    private fun calculateBearing(start: LatLng, end: LatLng): Float {
        val startLocation = android.location.Location("").apply {
            latitude = start.latitude
            longitude = start.longitude
        }
        val endLocation = android.location.Location("").apply {
            latitude = end.latitude
            longitude = end.longitude
        }
        return startLocation.bearingTo(endLocation)
    }


    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    // JavaScriptからKotlinを呼び出すためのインターフェース
    class WebAppInterface(private val mContext: Context) {
        @JavascriptInterface
        fun showToast(toast: String) {
            Toast.makeText(mContext, toast, Toast.LENGTH_SHORT).show()
        }
    }
}