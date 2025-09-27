package com.kic.stepmemory.ui.memo

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.kic.stepmemory.MainActivity
import com.kic.stepmemory.R // Rクラスをインポート
import com.kic.stepmemory.challenge.ChallengeManager
import com.kic.stepmemory.data.AudioPin
import com.kic.stepmemory.data.GeoPoint
import com.kic.stepmemory.data.Record
import com.kic.stepmemory.databinding.ActivityMemoBinding
import com.kic.stepmemory.services.LocationTrackingService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Date
import android.widget.ArrayAdapter // ArrayAdapterをインポート

class MemoActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMemoBinding
    private lateinit var firestore: FirebaseFirestore
    private lateinit var challengeManager: ChallengeManager

    private var recordStartTime: Long = 0L
    private var recordEndTime: Long = 0L
    private var recordDurationMs: Long = 0L

    private var audioPins: List<AudioPin> = listOf()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMemoBinding.inflate(layoutInflater)
        setContentView(binding.root)

        firestore = FirebaseFirestore.getInstance()
        challengeManager = ChallengeManager(this)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "記録の詳細"

        recordStartTime = intent.getLongExtra("RECORD_START_TIME", 0L)
        recordEndTime = intent.getLongExtra("RECORD_END_TIME", 0L)
        recordDurationMs = intent.getLongExtra("RECORD_DURATION_MS", 0L)
        val audioPinsJson = intent.getStringExtra("AUDIO_PINS_JSON")
        if (audioPinsJson != null) {
            val type = object : TypeToken<List<AudioPin>>() {}.type
            audioPins = Gson().fromJson(audioPinsJson, type)
        }

        // ▼▼▼ Spinner の設定を追加 ▼▼▼
        ArrayAdapter.createFromResource(
            this,
            R.array.weather_options, // strings.xmlで定義した配列
            android.R.layout.simple_spinner_item
        ).also { adapter ->
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            binding.spinnerWeather.adapter = adapter
        }
        // ▲▲▲ Spinner の設定ここまで ▲▲▲

        binding.btnSaveMemo.setOnClickListener {
            saveRecordToFirestore()
        }
    }

    private fun saveRecordToFirestore() {
        val recordName = binding.etRecordName.text.toString().trim()
        val memoContent = binding.etMemoContent.text.toString().trim()

        // ▼▼▼ 天気情報の取得 ▼▼▼
        val selectedWeatherPosition = binding.spinnerWeather.selectedItemPosition
        val selectedWeatherString = if (selectedWeatherPosition > 0) { // 0番目は「天気を選択してください」
            binding.spinnerWeather.selectedItem.toString()
        } else {
            null // 何も選択されていないか、プレースホルダーが選択されている場合はnull
        }
        // ▲▲▲ 天気情報の取得ここまで ▲▲▲


        if (recordName.isEmpty() && memoContent.isEmpty()) {
            Toast.makeText(this, "記録名またはメモ内容を入力してください。", Toast.LENGTH_SHORT).show()
            return
        }

        val pathPoints = LocationTrackingService.currentPathPoints.map { latLng ->
            GeoPoint(latLng.latitude, latLng.longitude)
        }

        val newRecord = Record(
            name = if (recordName.isNotEmpty()) recordName else null,
            memo = if (memoContent.isNotEmpty()) memoContent else null,
            startTime = recordStartTime,
            endTime = recordEndTime,
            durationMs = recordDurationMs,
            pathPoints = pathPoints,
            audioPins = audioPins,
            weather = selectedWeatherString, // 取得した天気情報をセット
            createdAt = Date(),
            updatedAt = Date()
        )

        firestore.collection("records")
            .add(newRecord)
            .addOnSuccessListener {
                Toast.makeText(this, "記録を保存しました！", Toast.LENGTH_SHORT).show()
                LocationTrackingService.currentPathPoints.clear()

                CoroutineScope(Dispatchers.IO).launch {
                    challengeManager.updateProgressAndGetNewChallengeIfNeeded()
                }

                val intent = Intent(this, MainActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                startActivity(intent)
                finish()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "記録の保存に失敗しました: ${e.message}", Toast.LENGTH_LONG).show()
                e.printStackTrace()
            }
    }

    override fun onSupportNavigateUp(): Boolean {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(intent)
        finish()
        return true
    }
}
