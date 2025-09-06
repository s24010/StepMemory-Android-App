package com.kic.stepmemory

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.kic.stepmemory.challenge.ChallengeManager
import com.kic.stepmemory.data.Challenge
import com.kic.stepmemory.data.ChallengeType
import com.kic.stepmemory.databinding.ActivityMainBinding
import com.kic.stepmemory.ui.heatmap.HeatmapActivity
import com.kic.stepmemory.ui.history.HistoryActivity
import com.kic.stepmemory.ui.recording.RecordingActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var challengeManager: ChallengeManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        challengeManager = ChallengeManager(this)

        // ... (ボタンのクリックリスナーは変更なし)
    }

    override fun onResume() {
        super.onResume()
        updateChallengeView()
    }

    private fun updateChallengeView() {
        CoroutineScope(Dispatchers.IO).launch {
            // ★★★ 達成済みの場合、新しいチャレンジを取得し直すように変更 ★★★
            val challenge = challengeManager.updateProgressAndGetNewChallengeIfNeeded()
            withContext(Dispatchers.Main) {
                displayChallenge(challenge)
            }
        }
    }

    private fun displayChallenge(challenge: Challenge) {
        binding.tvChallengeTitle.text = challenge.title
        binding.tvChallengeDescription.text = challenge.description
        binding.progressChallenge.max = if(challenge.goal > 0) challenge.goal else 1
        binding.progressChallenge.progress = challenge.currentProgress

        // ★★★ SINGLE_RECORD_DURATION の表示形式を追加 ★★★
        val progressText = when (challenge.type) {
            ChallengeType.TOTAL_DURATION -> "${challenge.currentProgress} / ${challenge.goal} 分"
            ChallengeType.SINGLE_RECORD_DURATION -> "目標: ${challenge.goal} 分"
            else -> "${challenge.currentProgress} / ${challenge.goal}"
        }
        binding.tvChallengeProgress.text = progressText
    }
}