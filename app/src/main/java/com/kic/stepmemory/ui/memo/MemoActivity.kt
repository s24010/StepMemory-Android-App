package com.kic.stepmemory.ui.memo

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore // Firebase Firestore SDK
import com.kic.stepmemory.MainActivity // メイン画面への遷移用
import com.kic.stepmemory.data.Record // データモデル
import com.kic.stepmemory.databinding.ActivityMemoBinding // View Binding

/**
 * メモ画面のアクティビティです。
 * 記録された道のデータと共に、ユーザーがメモと記録名を記入し、Firebase Firestoreに保存します。
 */
class MemoActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMemoBinding
    private lateinit var firestore: FirebaseFirestore // Firestoreインスタンス

    // RecordingActivityから受け取るデータ
    private var recordPathDataJson: String? = null
    private var recordStartTime: Long = 0L
    private var recordEndTime: Long = 0L
    private var recordDurationMs: Long = 0L


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMemoBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Firestoreインスタンスを取得
        firestore = FirebaseFirestore.getInstance()

        // ActionBarに「戻る」ボタンを表示
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "記録の詳細"

        // Intentから記録データを取得
        recordPathDataJson = intent.getStringExtra("RECORD_PATH_DATA")
        recordStartTime = intent.getLongExtra("RECORD_START_TIME", 0L)
        recordEndTime = intent.getLongExtra("RECORD_END_TIME", 0L)
        recordDurationMs = intent.getLongExtra("RECORD_DURATION_MS", 0L)

        // 「記録を保存」ボタンのクリックリスナーを設定
        binding.btnSaveMemo.setOnClickListener {
            saveRecordToFirestore() // 記録データをFirestoreに保存する関数を呼び出し
        }
    }

    /**
     * ユーザーが入力したメモと記録データ、およびパスデータをFirebase Firestoreに保存します。
     */
    private fun saveRecordToFirestore() {
        val recordName = binding.etRecordName.text.toString().trim() // 記録名を取得
        val memoContent = binding.etMemoContent.text.toString().trim() // メモ内容を取得

        // 記録名とメモ内容が両方とも空の場合は警告
        if (recordName.isEmpty() && memoContent.isEmpty()) {
            Toast.makeText(this, "記録名またはメモ内容を入力してください。", Toast.LENGTH_SHORT).show()
            return
        }

        // Recordデータクラスのインスタンスを作成
        val newRecord = Record(
            name = if (recordName.isNotEmpty()) recordName else null, // 空文字列ならnull
            memo = if (memoContent.isNotEmpty()) memoContent else null, // 空文字列ならnull
            startTime = recordStartTime,
            endTime = recordEndTime,
            durationMs = recordDurationMs,
            pathData = recordPathDataJson ?: "[]", // nullの場合は空のJSON配列
            createdAt = System.currentTimeMillis(), // 作成日時
            updatedAt = System.currentTimeMillis() // 更新日時
        )

        // Firestoreのコレクション 'records' に新しいドキュメントを追加
        // add() メソッドは自動でドキュメントIDを生成してくれます。
        firestore.collection("records")
            .add(newRecord) // RecordオブジェクトをFirestoreに保存
            .addOnSuccessListener { documentReference ->
                // 保存成功時の処理
                Toast.makeText(this, "記録を保存しました！", Toast.LENGTH_SHORT).show()
                // idUUID をFirestoreが生成したIDで更新（後続の画面で参照する場合に備えて）
                newRecord.idUUID = documentReference.id
                // 初期画面に戻る
                val intent = Intent(this, MainActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                startActivity(intent)
                finish() // 現在のアクティビティを終了
            }
            .addOnFailureListener { e ->
                // 保存失敗時の処理
                Toast.makeText(this, "記録の保存に失敗しました: ${e.message}", Toast.LENGTH_LONG).show()
                e.printStackTrace()
            }
    }

    // ActionBarの戻るボタンが押された時の処理
    override fun onSupportNavigateUp(): Boolean {
        // メモ画面から戻る場合は、MainActivityに戻る
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(intent)
        finish()
        return true
    }
}