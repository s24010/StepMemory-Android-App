package com.kic.stepmemory.ui.memo

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore
import com.kic.stepmemory.MainActivity
import com.kic.stepmemory.challenge.ChallengeManager
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

    private var recordStartTime: Long = 0L
    private var recordEndTime: Long = 0L
    private var recordDurationMs: Long = 0L

    private var uploadedAudioUrl: String? = null

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
        uploadedAudioUrl = intent.getStringExtra("AUDIO_URL")

        binding.btnSaveMemo.setOnClickListener {
            saveRecordToFirestore()
        }
    }

    private fun saveRecordToFirestore() {
        val recordName = binding.etRecordName.text.toString().trim()
        val memoContent = binding.etMemoContent.text.toString().trim()

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
            audioUrl = uploadedAudioUrl,
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