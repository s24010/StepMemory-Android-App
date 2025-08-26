package com.kic.stepmemory

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.kic.stepmemory.databinding.ActivityMainBinding
import com.kic.stepmemory.ui.aura.AuraMapActivity // ★★★ 新しいActivityをインポート ★★★
import com.kic.stepmemory.ui.recording.RecordingActivity
import com.kic.stepmemory.ui.history.HistoryActivity

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnStartRecording.setOnClickListener {
            val intent = Intent(this, RecordingActivity::class.java)
            startActivity(intent)
        }

        binding.btnViewHistory.setOnClickListener {
            val intent = Intent(this, HistoryActivity::class.java)
            startActivity(intent)
        }

        // ★★★ 新しいボタンのクリック処理を追加 ★★★
        binding.btnViewAuraMap.setOnClickListener {
            val intent = Intent(this, AuraMapActivity::class.java)
            startActivity(intent)
        }
    }
}