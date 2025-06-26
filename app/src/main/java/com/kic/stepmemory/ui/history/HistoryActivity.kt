package com.kic.stepmemory.ui.history

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query // Firestoreのクエリ用
import com.kic.stepmemory.MainActivity // 初期画面への遷移用
import com.kic.stepmemory.data.Record // データモデル
import com.kic.stepmemory.databinding.ActivityHistoryBinding // View Binding
import com.kic.stepmemory.ui.review.ReviewActivity // 振り返り画面への遷移用

/**
 * 履歴画面のアクティビティです。
 * Firebase Firestoreから記録データを取得し、リストで表示します。
 * 各アイテムをクリックすると振り返り画面に遷移します。
 */
class HistoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHistoryBinding
    private lateinit var recordAdapter: RecordAdapter
    private val recordsList: MutableList<Record> = mutableListOf()
    private lateinit var firestore: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // ActionBarに「戻る」ボタンを表示
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "記録履歴"

        // Firestoreインスタンスを取得
        firestore = FirebaseFirestore.getInstance()

        // RecyclerViewのセットアップ
        recordAdapter = RecordAdapter(recordsList) { record ->
            // リストアイテムがクリックされた時の処理
            navigateToReviewScreen(record)
        }
        binding.rvRecords.apply {
            layoutManager = LinearLayoutManager(this@HistoryActivity) // リストを垂直に並べる
            adapter = recordAdapter
        }

        // 記録データをFirestoreから読み込む
        fetchRecordsFromFirestore()
    }

    /**
     * Firebase Firestoreから記録データを取得し、RecyclerViewに表示します。
     */
    private fun fetchRecordsFromFirestore() {
        binding.progressBar.visibility = View.VISIBLE // ローディング表示
        binding.tvNoRecords.visibility = View.GONE    // 「記録なし」を非表示
        binding.rvRecords.visibility = View.GONE      // リストを非表示

        firestore.collection("records")
            // Firestoreからデータを降順（新しいものが上）で取得
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .get() // ドキュメントを一度だけ取得
            .addOnSuccessListener { querySnapshot ->
                binding.progressBar.visibility = View.GONE // ローディング非表示
                recordsList.clear() // 既存のリストをクリア

                // 取得した各ドキュメントをRecordオブジェクトに変換し、リストに追加
                for (document in querySnapshot.documents) {
                    val record = document.toObject(Record::class.java)
                    record?.let {
                        // ドキュメントIDをRecordオブジェクトのidUUIDに設定
                        it.idUUID = document.id
                        recordsList.add(it)
                    }
                }

                // データの有無に応じてUIを更新
                if (recordsList.isEmpty()) {
                    binding.tvNoRecords.visibility = View.VISIBLE
                    binding.rvRecords.visibility = View.GONE
                } else {
                    recordAdapter.updateRecords(recordsList) // アダプターにデータを渡し、更新
                    binding.rvRecords.visibility = View.VISIBLE
                    binding.tvNoRecords.visibility = View.GONE
                }
            }
            .addOnFailureListener { e ->
                // データ取得失敗時の処理
                binding.progressBar.visibility = View.GONE // ローディング非表示
                binding.tvNoRecords.visibility = View.VISIBLE // エラー表示として利用
                binding.tvNoRecords.text = "記録の読み込みに失敗しました: ${e.message}"
                Toast.makeText(this, "記録の読み込みに失敗しました: ${e.message}", Toast.LENGTH_LONG).show()
                e.printStackTrace()
            }
    }

    /**
     * 選択された記録データを振り返り画面に渡して遷移します。
     */
    private fun navigateToReviewScreen(record: Record) {
        val intent = Intent(this, ReviewActivity::class.java).apply {
            // RecordオブジェクトのidUUIDを渡すことで、ReviewActivityで詳細データを取得できるようにします。
            // あるいは、Recordオブジェクト全体をParcelable/Serializableで渡すことも可能ですが、
            // 今回はIDを渡してReviewActivityで改めてFirestoreから取得する方式にします。
            // これは、データが大きくなる可能性がある場合や、最新の状態を保証したい場合に推奨されます。
            putExtra("RECORD_ID", record.idUUID)
        }
        startActivity(intent)
    }

    // ActionBarの戻るボタンが押された時の処理
    override fun onSupportNavigateUp(): Boolean {
        // HistoryActivityから戻る場合はMainActivityに戻る
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(intent)
        finish()
        return true
    }
}