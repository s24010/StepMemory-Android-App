package com.kic.stepmemory.ui.recording

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.PolylineOptions
import com.kic.stepmemory.R
import com.kic.stepmemory.databinding.ActivityRecordingBinding // View Binding
import com.kic.stepmemory.services.LocationTrackingService // 位置情報追跡サービス
import com.kic.stepmemory.ui.memo.MemoActivity // メモ画面への遷移用

import kotlinx.coroutines.flow.MutableStateFlow // Kotlin Coroutines Flow用
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import com.kic.stepmemory.data.Record // データモデル

/**
 * 記録画面のアクティビティです。
 * 地図の表示、位置情報の追跡開始/停止、道の描画を行います。
 */
class RecordingActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var binding: ActivityRecordingBinding
    private lateinit var googleMap: GoogleMap

    // 記録状態を管理する変数
    private var isTracking = false

    // 記録された道のポイントを保持 (サービスから取得)
    // val pathPoints = LocationTrackingService.pathPoints.asStateFlow() // Service内のStateFlowを監視する場合
    // ここではLocationTrackingServiceのcurrentPathPointsを直接参照します
    private var currentPathPoints: MutableList<LatLng> = LocationTrackingService.currentPathPoints

    // パーミッションリクエストコード
    private val LOCATION_PERMISSION_REQUEST_CODE = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRecordingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // ActionBarに「戻る」ボタンを表示
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "記録中" // タイトルを設定

        // Google Map Fragmentを初期化し、コールバックを設定
        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this) // 地図の準備ができた時にonMapReadyが呼ばれる

        // 記録開始/終了ボタンのクリックリスナーを設定
        binding.btnRecordAction.setOnClickListener {
            if (isTracking) {
                stopTracking() // 記録停止
            } else {
                checkLocationPermissions() // パーミッション確認後に記録開始
            }
        }

        // 現在地ボタンのクリックリスナーを設定 (オプション)
        binding.fabMyLocation.setOnClickListener {
            // 現在地にカメラを移動するロジックを実装（後述）
            if (currentPathPoints.isNotEmpty()) {
                googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(currentPathPoints.last(), 16f))
            } else {
                Toast.makeText(this, "位置情報がありません", Toast.LENGTH_SHORT).show()
            }
        }

        // 記録状態に応じてボタンのテキストを更新
        updateTrackingButtonState()

        // サービスから位置情報更新を監視して地図に描画
        // ここではLocalBroadcastManagerを使用する代わりに、
        // LocationTrackingService.currentPathPoints を直接参照し、
        // 一定間隔で更新されると仮定するか、
        // またはサービスから定期的にUIに更新通知を送る仕組みを導入する必要があります。
        // 簡単のため、RecordingActivityがアクティブな間はサービスから直接リストを読み取るとします。
        // ※より堅牢な実装には、ServiceとActivity間でデータ共有のためのObserverパターンやFlowを用いるべきです。
    }

    /**
     * 地図の準備ができた時に呼び出されるコールバック
     */
    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        googleMap.uiSettings.isZoomControlsEnabled = true // ズームコントロールを有効化
        googleMap.uiSettings.isMyLocationButtonEnabled = false // デフォルトの現在地ボタンを無効化 (カスタムFABを使用するため)

        // 初期カメラ位置を東京駅に設定 (仮)
        val initialLocation = LatLng(35.681236, 139.767125)
        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(initialLocation, 10f))

        // 現在地レイヤーを有効化（パーミッションがあれば）
        enableMyLocationLayer()

        // アプリが起動中で既に追跡中の場合、パスを再描画
        if (isTracking && currentPathPoints.isNotEmpty()) {
            drawPathOnMap()
            // サービスから位置情報が更新されるたびに地図も更新されるようにする
            // ここでは簡易的に、一定間隔で地図を更新するポーリング処理を実装
            // または、サービスからの明示的なコールバックを受け取る仕組みが必要です。
            // より良い方法は、LocationTrackingServiceからのLocalBroadcastReceiverを設定することです。
            // しかし、RecordingActivityがフォアグラウンドにいる間だけ更新したいので、
            // onResumeでリスナーを登録し、onPauseで解除するようなライフサイクル管理が必要。
        }
    }

    /**
     * 位置情報パーミッションを確認し、必要であれば要求します。
     */
    private fun checkLocationPermissions() {
        // ACCESS_FINE_LOCATION または ACCESS_COARSE_LOCATION が許可されているか
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        ) {
            // バックグラウンド位置情報パーミッションの確認 (API 29以降)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    startTracking()
                } else {
                    // バックグラウンド位置情報パーミッションを要求
                    ActivityCompat.requestPermissions(
                        this,
                        arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION),
                        LOCATION_PERMISSION_REQUEST_CODE
                    )
                }
            } else {
                // API 28以下ではバックグラウンド位置情報パーミッションは不要
                startTracking()
            }
        } else {
            // フォアグラウンド位置情報パーミッションを要求
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                LOCATION_PERMISSION_REQUEST_CODE
            )
        }
    }

    /**
     * パーミッション要求の結果を受け取るコールバック
     */
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            var allPermissionsGranted = true
            for (result in grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allPermissionsGranted = false
                    break
                }
            }

            if (allPermissionsGranted) {
                // すべてのパーミッションが許可された場合
                startTracking()
                enableMyLocationLayer() // 地図の現在地レイヤーを有効化
            } else {
                Toast.makeText(this, "位置情報パーミッションが拒否されました。", Toast.LENGTH_LONG).show()
            }
        }
    }

    /**
     * 位置情報追跡を開始します。
     * LocationTrackingServiceを起動し、UIを更新します。
     */
    private fun startTracking() {
        isTracking = true
        updateTrackingButtonState()

        // LocationTrackingServiceを起動
        val intent = Intent(this, LocationTrackingService::class.java).apply {
            action = LocationTrackingService.ACTION_START_TRACKING
        }
        startService(intent) // サービスを開始

        // 記録開始地点にマーカーを設置（任意）
        if (currentPathPoints.isNotEmpty()) {
            googleMap.addMarker(MarkerOptions().position(currentPathPoints.first()).title("開始地点"))
            googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(currentPathPoints.first(), 16f))
        }

        Toast.makeText(this, "記録を開始しました！", Toast.LENGTH_SHORT).show()

        // サービスからの位置情報更新を監視して地図に描画
        // ここではCoroutineScopeを使ってFlowを収集する例を記述します。
        // ServiceからのデータがFlowで提供されている前提です。
        // もしServiceがPathPointsを更新するStateFlowを持つなら、
        // ここで collectLatest を使うことで、リアルタイムに地図を更新できます。
        // 現在はLocationTrackingService.currentPathPointsを直接更新しているので、
        // UI側の定期的な更新ロジックや、サービスからのブロードキャストで通知する仕組みが必要です。
        // 簡単のため、今回はサービスが`currentPathPoints`を更新するたびに
        // RecordingActivityが起動中であれば自動的に描画されるという前提で進めます。
        // (より堅牢にするには、以下のようなObserverパターンを実装する必要があります)

        // TODO: サービスから位置情報更新を受け取るリスナーを設定
        // 例: LocalBroadcastManager.getInstance(this).registerReceiver(locationUpdateReceiver, IntentFilter("LOCATION_UPDATE"))
        // Receiver内で drawPathOnMap() を呼び出す

        // 一時的な措置として、定期的に地図を更新
        // （推奨される方法ではありませんが、デモ用としては機能します）
        // Handler().postDelayed({
        //     drawPathOnMap()
        // }, 1000) // 1秒ごとに更新
    }

    /**
     * 位置情報追跡を停止します。
     * LocationTrackingServiceを停止し、UIを更新後、メモ画面に遷移します。
     */
    private fun stopTracking() {
        isTracking = false
        updateTrackingButtonState()

        // LocationTrackingServiceを停止
        val intent = Intent(this, LocationTrackingService::class.java).apply {
            action = LocationTrackingService.ACTION_STOP_TRACKING
        }
        stopService(intent) // サービスを停止

        // 記録終了地点にマーカーを設置（任意）
        if (currentPathPoints.isNotEmpty()) {
            googleMap.addMarker(MarkerOptions().position(currentPathPoints.last()).title("終了地点"))
        }

        Toast.makeText(this, "記録を終了しました。", Toast.LENGTH_SHORT).show()

        // メモ画面に遷移
        val memoIntent = Intent(this, MemoActivity::class.java)
        // 記録したパスデータをMemoActivityに渡す（JSON文字列として）
        val record = Record(
            startTime = System.currentTimeMillis(), // 仮の開始時間
            endTime = System.currentTimeMillis(),   // 仮の終了時間
            pathData = com.google.gson.Gson().toJson(currentPathPoints) // JSON文字列に変換
        )
        // RecordオブジェクトをIntent経由で渡すには、ParcelableまたはSerializableを実装する必要があります。
        // 簡単のため、ここではJSON文字列を直接渡します。
        memoIntent.putExtra("RECORD_PATH_DATA", record.pathData)
        memoIntent.putExtra("RECORD_START_TIME", record.startTime)
        memoIntent.putExtra("RECORD_END_TIME", record.endTime)
        memoIntent.putExtra("RECORD_DURATION_MS", record.endTime - record.startTime) // 継続時間も渡す
        startActivity(memoIntent)

        // Activityを終了 (MainActivityに戻るため)
        finish()
    }

    /**
     * 記録されたパスを地図に描画します。
     */
    private fun drawPathOnMap() {
        if (currentPathPoints.size > 1) {
            googleMap.clear() // 古い線やマーカーをクリア
            // 記録開始地点にマーカーを再設置
            googleMap.addMarker(MarkerOptions().position(currentPathPoints.first()).title("開始地点"))

            val polylineOptions = PolylineOptions()
                .addAll(currentPathPoints) // 全てのポイントを追加
                .color(Color.BLUE)        // 青色の線
                .width(10f)               // 線の太さ

            googleMap.addPolyline(polylineOptions)

            // カメラを最終地点に移動（ズームレベルは任意）
            googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(currentPathPoints.last(), 16f))
        } else if (currentPathPoints.size == 1) {
            // 1点のみの場合はマーカーのみ表示
            googleMap.clear()
            googleMap.addMarker(MarkerOptions().position(currentPathPoints.first()).title("現在地"))
            googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(currentPathPoints.first(), 16f))
        }
    }

    /**
     * 現在地レイヤーを有効にします。
     * パーミッションが許可されていることを確認してから呼び出してください。
     */
    private fun enableMyLocationLayer() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            googleMap.isMyLocationEnabled = true // 現在地を示す青い点を表示
        }
    }

    /**
     * 記録開始/終了ボタンのテキストと状態を更新します。
     */
    private fun updateTrackingButtonState() {
        if (isTracking) {
            binding.btnRecordAction.text = "記録を終了する"
        } else {
            binding.btnRecordAction.text = "記録を開始する"
        }
    }

    // ActionBarの戻るボタンが押された時の処理
    override fun onSupportNavigateUp(): Boolean {
        finish() // 現在のアクティビティを終了し、前の画面に戻る
        return true
    }

    // ライフサイクルイベント（推奨）
    override fun onResume() {
        super.onResume()
        // アクティビティがフォアグラウンドに戻った時に地図を更新
        if (isTracking) {
            drawPathOnMap()
            // ここでサービスからのリアルタイム更新リスナーを再登録するのが理想
        }
    }

    override fun onPause() {
        super.onPause()
        // ここでリアルタイム更新リスナーを解除するのが理想
    }
}