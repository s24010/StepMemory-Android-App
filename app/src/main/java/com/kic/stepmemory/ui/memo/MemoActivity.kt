package com.kic.stepmemory.ui.memo

import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.kic.stepmemory.MainActivity
import com.kic.stepmemory.R
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

class MemoActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMemoBinding
    private lateinit var firestore: FirebaseFirestore
    private lateinit var challengeManager: ChallengeManager
    private lateinit var auth: FirebaseAuth
    private var userId: String? = null

    private var recordStartTime: Long = 0L
    private var recordEndTime: Long = 0L
    private var recordDurationMs: Long = 0L

    private var audioPins: List<AudioPin> = listOf()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMemoBinding.inflate(layoutInflater)
        setContentView(binding.root)

        firestore = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()
        userId = auth.currentUser?.uid

        if (userId == null) {
            Toast.makeText(this, "記録を保存するにはログインが必要です。", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        challengeManager = ChallengeManager(this, userId!!)

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

        ArrayAdapter.createFromResource(
            this,
            R.array.weather_options,
            android.R.layout.simple_spinner_item
        ).also { adapter ->
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            binding.spinnerWeather.adapter = adapter
        }

        binding.btnSaveMemo.setOnClickListener {
            saveRecordToFirestore()
        }
    }

    private fun saveRecordToFirestore() {
        val currentUserId = userId
        if (currentUserId == null) {
            Toast.makeText(this, "ユーザー情報が取得できませんでした。再度お試しください。", Toast.LENGTH_SHORT).show()
            return
        }

        val recordName = binding.etRecordName.text.toString().trim()
        val memoContent = binding.etMemoContent.text.toString().trim()

        val selectedWeatherPosition = binding.spinnerWeather.selectedItemPosition
        val selectedWeatherString = if (selectedWeatherPosition > 0) {
            binding.spinnerWeather.selectedItem.toString()
        } else {
            null
        }

        if (recordName.isEmpty() && memoContent.isEmpty()) {
            Toast.makeText(this, "記録名またはメモ内容を入力してください。", Toast.LENGTH_SHORT).show()
            return
        }

        val pathPoints = LocationTrackingService.currentPathPoints.map { latLng ->
            GeoPoint(latLng.latitude, latLng.longitude)
        }

        val newRecord = Record(
            userId = currentUserId,
            name = if (recordName.isNotEmpty()) recordName else null,
            memo = if (memoContent.isNotEmpty()) memoContent else null,
            startTime = recordStartTime,
            endTime = recordEndTime,
            durationMs = recordDurationMs,
            pathPoints = pathPoints,
            audioPins = audioPins,
            weather = selectedWeatherString,
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
