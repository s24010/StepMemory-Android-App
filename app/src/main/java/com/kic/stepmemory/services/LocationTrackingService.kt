package com.kic.stepmemory.services

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.os.Looper
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.*
import com.google.android.gms.maps.model.LatLng
import com.kic.stepmemory.R // res/values/strings.xmlに定義されるアプリ名などへのアクセス
import com.kic.stepmemory.ui.recording.RecordingActivity // 通知タップ時に遷移する画面

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * バックグラウンドでユーザーの位置情報を追跡し、フォアグラウンドサービスとして動作するServiceクラスです。
 * 追跡された位置情報はMutableStateFlowを通じて外部に公開されます。
 */
class LocationTrackingService : Service() {

    private lateinit var fusedLocationProviderClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback

    // 位置情報更新の間隔 (ms)
    private val UPDATE_INTERVAL_MS: Long = 5000 // 5秒
    private val FASTEST_UPDATE_INTERVAL_MS: Long = 3000 // 3秒 (可能な限り速い場合)

    // 通知チャンネルID
    companion object {
        const val NOTIFICATION_CHANNEL_ID = "location_tracking_channel"
        const val NOTIFICATION_ID = 12345
        const val ACTION_START_TRACKING = "ACTION_START_TRACKING"
        const val ACTION_STOP_TRACKING = "ACTION_STOP_TRACKING"

        // 現在追跡中のパスデータ (緯度経度のリスト) を保持するStateFlow
        // RecordingActivityなどから監視してUIを更新するために使用します。
        // private val _pathPoints = MutableStateFlow<MutableList<LatLng>>(mutableListOf())
        // val pathPoints = _pathPoints.asStateFlow() // 外部からはReadOnlyのFlowとして公開
        // 上記はServiceのインスタンス間で共有されないので、シングルトンオブジェクトにするか、
        // EventBusのようなものを使用する必要があります。今回は簡単な例として直接パスを渡す形にします。
        // または、ViewModelとServiceをバインドする方法を後で検討します。
        // ここでは一旦、Service内でのみパスを保持し、ブロードキャストまたはバインダーで渡すことを想定します。

        // 記録中の位置情報リストを保持するシングルトン的なオブジェクト
        // これにより、サービスが再起動してもデータが失われないようにします。
        val currentPathPoints: MutableList<LatLng> = mutableListOf()
    }

    override fun onCreate() {
        super.onCreate()
        // 位置情報サービスプロバイダの初期化
        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this)

        // 位置情報が更新された時のコールバックを定義
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                super.onLocationResult(locationResult)
                // 最新の位置情報を取得
                locationResult.lastLocation?.let { location ->
                    val newPoint = LatLng(location.latitude, location.longitude)
                    // 取得した位置情報をリストに追加
                    currentPathPoints.add(newPoint)
                    // TODO: 必要であれば、RecordingActivityに位置情報が更新されたことを通知（例: LocalBroadcastManager）
                    // 例: Intent().also { it.action = "LOCATION_UPDATE"; sendBroadcast(it) }
                    // または、StateFlowを直接更新してFlowをcollectしているコンポーネントに通知
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 通知チャンネルを作成し、フォアグラウンドサービスを開始
        createNotificationChannel()
        val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setAutoCancel(false) // 通知を自動で消さない
            .setOngoing(true)    // 継続的なイベントとして表示
            .setSmallIcon(R.drawable.ic_launcher_foreground) // 小さなアイコン (プロジェクトのアイコンなどを指定)
            .setContentTitle("ステップメモリ")
            .setContentText("位置情報を追跡中です...")
            .setContentIntent(getMainActivityPendingIntent()) // 通知タップ時にMainActivityに戻る
            .build()

        startForeground(NOTIFICATION_ID, notification) // フォアグラウンドサービスとして開始

        // インテントアクションに基づいて処理を分岐
        when (intent?.action) {
            ACTION_START_TRACKING -> {
                startLocationUpdates() // 位置情報の更新を開始
                currentPathPoints.clear() // 新しい記録を開始する前に既存のパスをクリア
            }
            ACTION_STOP_TRACKING -> {
                stopSelf() // サービスを停止 (onCreate -> onDestroyのライフサイクルへ)
            }
        }

        // START_NOT_STICKY: システムによってサービスが強制終了された場合、再作成しない
        // START_STICKY: システムによって強制終了された場合、可能な限りサービスを再作成し、onStartCommand()をnullインテントで呼び出す
        // START_REDELIVER_INTENT: START_STICKYと同じだが、最後に配信されたインテントを再配信する
        return START_NOT_STICKY // 今回は明示的に停止されるまで追跡し続けるため、通常はSTART_STICKYでも良い
    }

    /**
     * 位置情報の更新を開始します。
     * 権限が許可されていることを前提とします。
     */
    private fun startLocationUpdates() {
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, UPDATE_INTERVAL_MS)
            .setWaitForAccurateLocation(false)
            .setMinUpdateIntervalMillis(FASTEST_UPDATE_INTERVAL_MS)
            .build()

        // 位置情報パーミッションが許可されているか確認
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // パーミッションがない場合、何もしない（通常はActivity側でチェックして要求する）
            return
        }

        // 位置情報の更新をリクエスト
        fusedLocationProviderClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.getMainLooper() // メインスレッドのLooperでコールバックを受け取る
        )
    }

    /**
     * 位置情報の更新を停止します。
     */
    private fun stopLocationUpdates() {
        fusedLocationProviderClient.removeLocationUpdates(locationCallback)
    }

    override fun onDestroy() {
        super.onDestroy()
        stopLocationUpdates() // サービスが破棄されるときに位置情報の更新を停止
        // TODO: 記録したパスデータを保存する処理を呼び出す（例: RecordingActivityにブロードキャストで通知）
        // または、RecordingActivityからServiceにバインドして直接パスデータを取得する
    }

    override fun onBind(intent: Intent?): IBinder? {
        // バインドされたServiceが必要な場合はここでIBinderを返す
        // 今回は startService() で開始し、ブロードキャストやシングルトンでデータ共有するため null を返す
        return null
    }

    /**
     * フォアグラウンドサービス通知のためのチャンネルを作成します。
     * Android 8.0 (API レベル 26) 以降で必須です。
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) { // Android Oreo (API 26) 以上
            val serviceChannel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "位置情報追跡サービス", // ユーザーに表示されるチャンネル名
                NotificationManager.IMPORTANCE_LOW // 重要度を低く設定（通知音が鳴らないように）
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    /**
     * 通知タップ時にRecordingActivityに戻るためのPendingIntentを作成します。
     */
    private fun getMainActivityPendingIntent(): PendingIntent? {
        val resultIntent = Intent(this, RecordingActivity::class.java)
        // FLAG_UPDATE_CURRENT: 既存のPendingIntentがあればデータを更新
        // FLAG_IMMUTABLE: PendingIntentは変更不可にする (推奨)
        return PendingIntent.getActivity(
            this,
            0,
            resultIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}