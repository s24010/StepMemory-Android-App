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

        binding.btnStartRecording.setOnClickListener {
            val intent = Intent(this, RecordingActivity::class.java)
            startActivity(intent)
        }

        binding.btnViewHistory.setOnClickListener {
            val intent = Intent(this, HistoryActivity::class.java)
            startActivity(intent)
        }

        binding.btnViewAuraMap.text = "絆をヒートマップで見る"
        binding.btnViewAuraMap.setOnClickListener {
            val intent = Intent(this, HeatmapActivity::class.java)
            startActivity(intent)
        }
    }

    override fun onResume() {
        super.onResume()
        // 画面が表示されるたびにチャレンジを更新
        updateChallengeView()
    }

    private fun updateChallengeView() {
        CoroutineScope(Dispatchers.IO).launch {
            val challenge = challengeManager.getCurrentChallenge()
            withContext(Dispatchers.Main) {
                displayChallenge(challenge)
            }
        }
    }

    private fun displayChallenge(challenge: Challenge) {
        binding.tvChallengeTitle.text = challenge.title
        binding.tvChallengeDescription.text = challenge.description
        binding.progressChallenge.max = challenge.goal
        binding.progressChallenge.progress = challenge.currentProgress

        val progressText = when (challenge.type) {
            ChallengeType.TOTAL_DURATION -> "${challenge.currentProgress} / ${challenge.goal} 分"
            else -> "${challenge.currentProgress} / ${challenge.goal}"
        }
        binding.tvChallengeProgress.text = progressText
    }
}