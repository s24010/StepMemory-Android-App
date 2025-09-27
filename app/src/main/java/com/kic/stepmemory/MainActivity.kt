package com.kic.stepmemory

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        challengeManager = ChallengeManager(this)
        firestore = FirebaseFirestore.getInstance() // Firestoreの初期化

        binding.btnStartRecording.setOnClickListener {
            val intent = Intent(this, RecordingActivity::class.java)
            startActivity(intent)
        }

        binding.btnViewHistory.setOnClickListener {
            val intent = Intent(this, HistoryActivity::class.java)
            startActivity(intent)
        }

        // binding.btnViewAuraMap.text = "絆をヒートマップで見る" // この行を削除またはコメントアウト
        binding.btnViewAuraMap.setOnClickListener {
            // HeatmapActivity を汎用的なマップ表示画面として拡張する方針なので、遷移先はそのまま
            val intent = Intent(this, HeatmapActivity::class.java)
            startActivity(intent)
        }

        // ▼▼▼ 思い出の一枚カードのクリックリスナー ▼▼▼
        binding.cardFlashback.setOnClickListener {
            flashbackRecord?.let { record ->
                // ▼▼▼ idUUIDのチェックを修正 ▼▼▼
                if (record.idUUID.isNotEmpty()) {
                    val intent = Intent(this, ReviewActivity::class.java)
                    intent.putExtra("RECORD_ID", record.idUUID)
                    startActivity(intent)
                } else {
                    Log.w("MainActivity", "Flashback record ID is empty, cannot navigate.")
                    // 必要であればユーザーにエラーメッセージを表示
                }
                // ▲▲▲ 修正ここまで ▲▲▲
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateChallengeView()
        updateFlashbackCardView() // ▼▼▼ フラッシュバックカードの更新処理を呼び出し ▼▼▼
    }

    private fun updateChallengeView() {
        CoroutineScope(Dispatchers.IO).launch {
            val challenge = challengeManager.updateProgressAndGetNewChallengeIfNeeded()
            withContext(Dispatchers.Main) {
                displayChallenge(challenge)
            }
        }
    }

    // ▼▼▼ 思い出の一枚カードの更新処理 (修正版) ▼▼▼
    private fun updateFlashbackCardView() {
        CoroutineScope(Dispatchers.IO).launch {
            if (challengeManager.isFlashbackFeatureUnlocked()) {
                // 1年前 ±15日 の日付範囲を計算
                val calendar = Calendar.getInstance()
                // val today = calendar.time // Log用

                calendar.add(Calendar.YEAR, -1) // 1年前に設定
                val targetDateForLog = calendar.time // Log用
                calendar.add(Calendar.DAY_OF_YEAR, -15) // 15日前
                val startDate = calendar.timeInMillis

                calendar.add(Calendar.DAY_OF_YEAR, 30) // さらに30日後 (合計±15日の範囲)
                val endDate = calendar.timeInMillis

                Log.d("MainActivity", "Flashback target date (1 year ago): ${SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(targetDateForLog)}")
                Log.d("MainActivity", "Flashback search range: from ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(startDate))} to ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(endDate))}")

                try {
                    val querySnapshot = firestore.collection("records")
                        .whereGreaterThanOrEqualTo("startTime", startDate)
                        .whereLessThanOrEqualTo("startTime", endDate)
                        .get()
                        .await()

                    // ▼▼▼ Recordオブジェクトのリスト作成とidUUIDの設定方法を改善 ▼▼▼
                    val fetchedRecords = mutableListOf<Record>()
                    for (document in querySnapshot.documents) {
                        val record = document.toObject(Record::class.java)
                        if (record != null) {
                            record.idUUID = document.id // ドキュメントIDをidUUIDに設定
                            fetchedRecords.add(record)
                        }
                    }
                    Log.d("MainActivity", "Found ${fetchedRecords.size} records for flashback with ID.")
                    // ▲▲▲ 改善ここまで ▲▲▲

                    if (fetchedRecords.isNotEmpty()) {
                        flashbackRecord = fetchedRecords.randomOrNull() // ランダムに1件選択
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
    // ▲▲▲ ここまで追加 ▲▲▲

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
