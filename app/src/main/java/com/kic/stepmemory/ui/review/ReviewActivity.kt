// app/src/main/java/com.kic.stepmemory/ui/review/ReviewActivity.kt

package com.kic.stepmemory.ui.review

import android.graphics.Color
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.PolylineOptions
import com.google.firebase.firestore.FirebaseFirestore
import com.kic.stepmemory.R
import com.kic.stepmemory.data.Record
import com.kic.stepmemory.databinding.ActivityReviewBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 振り返り画面のアクティビティです。
 * 選択された記録のパスを地図に表示し、メモを参照できるようにします。
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

        // ActionBarに「戻る」ボタンを表示
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "記録の振り返り"

        firestore = FirebaseFirestore.getInstance()

        // Intentから記録IDを取得
        recordId = intent.getStringExtra("RECORD_ID")

        // Google Map Fragmentを初期化し、コールバックを設定
        val mapFragment = supportFragmentManager.findFragmentById(R.id.map_review) as SupportMapFragment
        mapFragment.getMapAsync(this)

        // 「メモを見る」ボタンのクリックリスナー
        binding.fabShowMemo.setOnClickListener {
            currentRecord?.let { record ->
                showMemoDialog(record.name, record.memo)
            } ?: run {
                Toast.makeText(this, "メモが読み込まれていません。", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * 地図の準備ができた時に呼び出されるコールバック
     */
    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        googleMap.uiSettings.isZoomControlsEnabled = true // ズームコントロールを有効化

        // 記録IDがある場合のみデータを読み込み、地図に表示
        recordId?.let { id ->
            fetchRecordFromFirestore(id)
        } ?: run {
            Toast.makeText(this, "記録IDが指定されていません。", Toast.LENGTH_LONG).show()
            finish() // IDがない場合はActivityを終了
        }
    }

    /**
     * Firebase Firestoreから特定の記録データを取得します。
     */
    private fun fetchRecordFromFirestore(id: String) {
        firestore.collection("records").document(id).get()
            .addOnSuccessListener { documentSnapshot ->
                val record = documentSnapshot.toObject(Record::class.java)
                record?.let {
                    it.idUUID = documentSnapshot.id // IDを設定
                    currentRecord = it // 取得した記録を保持
                    displayRecordOnMap(it) // 地図にパスを描画
                    // ActionBarのタイトルを記録名に更新
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

    /**
     * 取得した記録データを地図に表示します。
     */
    private fun displayRecordOnMap(record: Record) {
        val pathPoints = record.pathPoints.map { geoPoint ->
            LatLng(geoPoint.latitude, geoPoint.longitude)
        }

        if (pathPoints.isNotEmpty()) {
            // パスを描画
            val polylineOptions = PolylineOptions()
                .addAll(pathPoints)
                .color(Color.BLUE)
                .width(10f)
            googleMap.addPolyline(polylineOptions)

            // 開始地点と終了地点にマーカーを設置
            googleMap.addMarker(MarkerOptions().position(pathPoints.first()).title("開始地点"))
            googleMap.addMarker(MarkerOptions().position(pathPoints.last()).title("終了地点"))

            // 地図のカメラをパス全体が表示されるように移動
            val bounds = com.google.android.gms.maps.model.LatLngBounds.Builder()
            for (point in pathPoints) {
                bounds.include(point)
            }
            googleMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds.build(), 100)) // 100はパディング
        } else {
            Toast.makeText(this, "この記録にはパスデータがありません。", Toast.LENGTH_SHORT).show()
            // パスがない場合でも、もし開始地点があればそこにズーム
            if (record.startTime != 0L) { // 仮のチェック
                val markerLocation = LatLng(35.681236, 139.767125) // 例: デフォルト位置
                // 実際の記録に LatLng が含まれていない場合、中心点を特定するのは難しいです。
                // Firestoreに単一の開始地点Lat/Lngも保存するようにすると良いかもしれません。
                googleMap.addMarker(MarkerOptions().position(markerLocation).title("データなし"))
                googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(markerLocation, 10f))
            }
        }
    }

    /**
     * メモ内容をダイアログで表示します。
     */
    private fun showMemoDialog(name: String?, memo: String?) {
        val dialogTitle = if (name.isNullOrEmpty()) "記録メモ" else "記録名: $name"
        val dialogMessage = if (memo.isNullOrEmpty()) "メモはありません。" else memo

        AlertDialog.Builder(this)
            .setTitle(dialogTitle)
            .setMessage(dialogMessage)
            .setPositiveButton("閉じる", null) // OKボタンで閉じる
            .show()
    }

    // 記録名がない場合に表示するデフォルトタイトルをフォーマットするヘルパー関数
    private fun formatRecordTitle(record: Record): String {
        val dateFormatter = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault())
        val date = Date(record.startTime)
        return "記録: ${dateFormatter.format(date)}"
    }

    // ActionBarの戻るボタンが押された時の処理
    override fun onSupportNavigateUp(): Boolean {
        finish() // 現在のアクティビティを終了し、前の画面に戻る
        return true
    }
}