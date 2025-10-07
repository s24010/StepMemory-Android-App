package com.kic.stepmemory

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.toObjects // FirestoreのtoObjectsのため
import com.kic.stepmemory.challenge.ChallengeManager
import com.kic.stepmemory.data.Challenge
import com.kic.stepmemory.data.ChallengeType
import com.kic.stepmemory.data.Record // Recordデータクラスのimport
import com.kic.stepmemory.databinding.ActivityMainBinding
import com.kic.stepmemory.ui.heatmap.HeatmapActivity
import com.kic.stepmemory.ui.history.HistoryActivity
import com.kic.stepmemory.ui.recording.RecordingActivity
import com.kic.stepmemory.ui.review.ReviewActivity // ReviewActivityのimport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.* // CalendarとDateのため

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var challengeManager: ChallengeManager
    private lateinit var firestore: FirebaseFirestore // Firestoreインスタンス
    private var flashbackRecord: Record? = null // 選択されたフラッシュバック記録
    private lateinit var auth: FirebaseAuth
    private var userId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        firestore = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        if (auth.currentUser == null) {
            auth.signInAnonymously()
                .addOnCompleteListener(this) { task ->
                    if (task.isSuccessful) {
                        Log.d("MainActivity", "signInAnonymously:success")
                        userId = auth.currentUser?.uid
                        initializeApp()
                    } else {
                        Log.w("MainActivity", "signInAnonymously:failure", task.exception)
                        Toast.makeText(this, "ユーザー情報の初期化に失敗しました。", Toast.LENGTH_LONG).show()
                        finish()
                    }
                }
        } else {
            Log.d("MainActivity", "User already signed in.")
            userId = auth.currentUser?.uid
            initializeApp()
        }
    }

    private fun initializeApp() {
        if (userId == null) {
            Log.e("MainActivity", "Initialization failed: userId is null.")
            Toast.makeText(this, "アプリの初期化に失敗しました。", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        challengeManager = ChallengeManager(this, userId!!)

        binding.btnStartRecording.setOnClickListener {
            val intent = Intent(this, RecordingActivity::class.java)
            startActivity(intent)
        }

        binding.btnViewHistory.setOnClickListener {
            val intent = Intent(this, HistoryActivity::class.java)
            startActivity(intent)
        }

        binding.btnViewAuraMap.setOnClickListener {
            val intent = Intent(this, HeatmapActivity::class.java)
            startActivity(intent)
        }

        binding.cardFlashback.setOnClickListener {
            flashbackRecord?.let { record ->
                if (record.idUUID.isNotEmpty()) {
                    val intent = Intent(this, ReviewActivity::class.java)
                    intent.putExtra("RECORD_ID", record.idUUID)
                    startActivity(intent)
                } else {
                    Log.w("MainActivity", "Flashback record ID is empty, cannot navigate.")
                }
            }
        }
        // onResumeはライフサイクルで自動的に呼ばれるので、ここでUI更新を呼ぶ必要はない
    }


    override fun onResume() {
        super.onResume()
        // userIdがセットされてからUI更新がかかるようにする
        if(userId != null) {
            updateChallengeView()
            updateFlashbackCardView()
        }
    }

    private fun updateChallengeView() {
        userId ?: return
        CoroutineScope(Dispatchers.IO).launch {
            val challenge = challengeManager.updateProgressAndGetNewChallengeIfNeeded()
            withContext(Dispatchers.Main) {
                displayChallenge(challenge)
            }
        }
    }

    private fun updateFlashbackCardView() {
        val currentUserId = userId ?: return

        CoroutineScope(Dispatchers.IO).launch {
            if (BuildConfig.ALL_FEATURES_UNLOCKED || challengeManager.isFlashbackFeatureUnlocked()) {
                try {
                    val querySnapshot = firestore.collection("records")
                        .whereEqualTo("userId", currentUserId)
                        .get()
                        .await()

                    val fetchedRecords = mutableListOf<Record>()
                    for (document in querySnapshot.documents) {
                        val record = document.toObject(Record::class.java)
                        if (record != null) {
                            record.idUUID = document.id // ドキュメントIDをidUUIDに設定
                            fetchedRecords.add(record)
                        }
                    }
                    Log.d("MainActivity", "Found ${fetchedRecords.size} total records for flashback for user $currentUserId.")

                    if (fetchedRecords.isNotEmpty()) {
                        // 記録の中からランダムに1件選択
                        flashbackRecord = fetchedRecords.randomOrNull()
                        flashbackRecord?.let { record ->
                            withContext(Dispatchers.Main) {
                                binding.tvFlashbackRecordTitle.text = record.name ?: "名称未設定の記録"
                                val sdf = SimpleDateFormat("yyyy年M月d日", Locale.JAPAN)
                                binding.tvFlashbackRecordDate.text = sdf.format(Date(record.startTime))
                                binding.cardFlashback.visibility = View.VISIBLE
                                Log.d("MainActivity", "Displaying flashback: ${record.name} (ID: ${record.idUUID})")
                            }
                        } ?: run { // ランダム選択に失敗した場合
                            withContext(Dispatchers.Main) {
                                binding.cardFlashback.visibility = View.GONE
                            }
                        }
                    } else {
                        // 表示できる記録がない場合はカードを非表示
                        flashbackRecord = null
                        withContext(Dispatchers.Main) {
                            binding.cardFlashback.visibility = View.GONE
                        }
                    }
                } catch (e: Exception) {
                    Log.e("MainActivity", "Error fetching flashback records", e)
                    flashbackRecord = null
                    withContext(Dispatchers.Main) {
                        binding.cardFlashback.visibility = View.GONE
                    }
                }
            } else {
                flashbackRecord = null
                withContext(Dispatchers.Main) {
                    binding.cardFlashback.visibility = View.GONE
                    Log.d("MainActivity", "Flashback feature is locked.")
                }
            }
        }
    }

    private fun displayChallenge(challenge: Challenge) {
        binding.tvChallengeTitle.text = challenge.title
        binding.tvChallengeDescription.text = challenge.description
        binding.progressChallenge.max = if(challenge.goal > 0) challenge.goal else 1
        binding.progressChallenge.progress = challenge.currentProgress

        val progressText = when (challenge.type) {
            ChallengeType.TOTAL_DURATION -> "${challenge.currentProgress} / ${challenge.goal} 分"
            ChallengeType.SINGLE_RECORD_DURATION -> "目標: ${challenge.goal} 分"
            else -> "${challenge.currentProgress} / ${challenge.goal}"
        }
        binding.tvChallengeProgress.text = progressText
    }
}
