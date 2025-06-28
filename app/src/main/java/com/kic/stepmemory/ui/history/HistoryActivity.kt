package com.kic.stepmemory.ui.history

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.kic.stepmemory.MainActivity
import com.kic.stepmemory.data.Record
import com.kic.stepmemory.databinding.ActivityHistoryBinding
import com.kic.stepmemory.ui.review.ReviewActivity

class HistoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHistoryBinding
    private lateinit var recordAdapter: RecordAdapter
    private val recordsList: MutableList<Record> = mutableListOf()
    private lateinit var firestore: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "記録履歴"

        firestore = FirebaseFirestore.getInstance()

        // ★★★ Adapterの初期化を修正 ★★★
        recordAdapter = RecordAdapter(
            onItemClick = { record ->
                navigateToReviewScreen(record)
            },
            onDeleteClick = { record ->
                showDeleteConfirmationDialog(record)
            }
        )

        binding.rvRecords.apply {
            layoutManager = LinearLayoutManager(this@HistoryActivity)
            adapter = recordAdapter
        }

        fetchRecordsFromFirestore()
    }

    private fun fetchRecordsFromFirestore() {
        binding.progressBar.visibility = View.VISIBLE
        binding.tvNoRecords.visibility = View.GONE
        binding.rvRecords.visibility = View.GONE

        firestore.collection("records")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { querySnapshot ->
                binding.progressBar.visibility = View.GONE
                recordsList.clear()
                for (document in querySnapshot.documents) {
                    val record = document.toObject(Record::class.java)
                    record?.let {
                        it.idUUID = document.id
                        recordsList.add(it)
                    }
                }

                Log.d("HistoryActivity", "取得した記録の件数: ${recordsList.size}件")

                if (recordsList.isEmpty()) {
                    Log.d("HistoryActivity", "分岐: リストは空です。'記録がありません'を表示します。")
                    binding.tvNoRecords.visibility = View.VISIBLE
                    binding.rvRecords.visibility = View.GONE
                } else {
                    Log.d("HistoryActivity", "分岐: リストにデータがあります。リストを表示します。")
                    recordAdapter.updateRecords(recordsList) // Adapterにデータを渡す
                    binding.rvRecords.visibility = View.VISIBLE
                    binding.tvNoRecords.visibility = View.GONE
                }
            }
            .addOnFailureListener { e ->
                binding.progressBar.visibility = View.GONE
                binding.tvNoRecords.visibility = View.VISIBLE
                binding.tvNoRecords.text = "記録の読み込みに失敗しました: ${e.message}"
                Toast.makeText(this, "記録の読み込みに失敗しました: ${e.message}", Toast.LENGTH_LONG).show()
                Log.e("HistoryActivity", "データ取得失敗", e)
            }
    }

    // ★★★ 削除確認ダイアログを表示する関数 ★★★
    private fun showDeleteConfirmationDialog(record: Record) {
        AlertDialog.Builder(this)
            .setTitle("記録の削除")
            .setMessage("「${record.name ?: "この記録"}」を削除しますか？\nこの操作は元に戻せません。")
            .setPositiveButton("削除") { _, _ ->
                deleteRecordFromFirestore(record)
            }
            .setNegativeButton("キャンセル", null)
            .show()
    }

    // ★★★ Firestoreから記録を削除する関数 ★★★
    private fun deleteRecordFromFirestore(record: Record) {
        if (record.idUUID.isEmpty()) {
            Toast.makeText(this, "削除エラー: 記録IDが見つかりません。", Toast.LENGTH_SHORT).show()
            return
        }
        binding.progressBar.visibility = View.VISIBLE
        firestore.collection("records").document(record.idUUID).delete()
            .addOnSuccessListener {
                binding.progressBar.visibility = View.GONE
                Toast.makeText(this, "記録を削除しました。", Toast.LENGTH_SHORT).show()
                // データを再読み込みしてリストを更新
                fetchRecordsFromFirestore()
            }
            .addOnFailureListener { e ->
                binding.progressBar.visibility = View.GONE
                Toast.makeText(this, "削除に失敗しました: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun navigateToReviewScreen(record: Record) {
        val intent = Intent(this, ReviewActivity::class.java).apply {
            putExtra("RECORD_ID", record.idUUID)
        }
        startActivity(intent)
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}