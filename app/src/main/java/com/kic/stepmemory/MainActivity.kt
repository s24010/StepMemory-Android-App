package com.kic.stepmemory

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.kic.stepmemory.databinding.ActivityMainBinding // View Bindingによって自動生成されるクラス
import com.kic.stepmemory.ui.recording.RecordingActivity // 記録画面への遷移用
import com.kic.stepmemory.ui.history.HistoryActivity     // 履歴画面への遷移用

/**
 * アプリケーションの初期画面を担当するアクティビティです。
 * ここから記録開始画面や履歴画面へ遷移します。
 */
class MainActivity : AppCompatActivity() {

    // View Bindingインスタンス。レイアウトのビューに型安全にアクセスするために使用します。
    // lateinit を使用することで、onCreate() 内で初期化されることを保証します。
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // View Bindingを初期化します。
        // activity_main.xml に対応するActivityMainBindingクラスが自動生成されます。
        binding = ActivityMainBinding.inflate(layoutInflater)
        // このアクティビティのレイアウトとしてView Bindingのルートビューを設定します。
        setContentView(binding.root)

        // 「記録を開始する」ボタンがクリックされた時の処理を設定します。
        binding.btnStartRecording.setOnClickListener {
            // RecordingActivityへのIntentを作成します。
            // Intentは、アプリのコンポーネント間（ここではMainActivityからRecordingActivity）で
            // 処理を要求するために使用されるメッセージオブジェクトです。
            val intent = Intent(this, RecordingActivity::class.java)
            // 作成したIntentでRecordingActivityを開始します。
            startActivity(intent)
        }

        // 「履歴を見る」ボタンがクリックされた時の処理を設定します。
        binding.btnViewHistory.setOnClickListener {
            // HistoryActivityへのIntentを作成します。
            val intent = Intent(this, HistoryActivity::class.java)
            // 作成したIntentでHistoryActivityを開始します。
            startActivity(intent)
        }
    }
}