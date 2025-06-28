// app/src/main/java/com/kic/stepmemory/ui/history/RecordAdapter.kt

package com.kic.stepmemory.ui.history

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.kic.stepmemory.data.Record
import com.kic.stepmemory.databinding.ItemRecordBinding
import java.text.SimpleDateFormat
import java.util.*

class RecordAdapter(
    // ★ Activityからリストを直接受け取るのではなく、コールバックのみを受け取るように変更
    private val onItemClick: (Record) -> Unit,
    private val onDeleteClick: (Record) -> Unit
) : RecyclerView.Adapter<RecordAdapter.RecordViewHolder>() {

    // ★★★ Adapterが自分自身のリストを持つように変更 ★★★
    private val records = mutableListOf<Record>()

    class RecordViewHolder(val binding: ItemRecordBinding) : RecyclerView.ViewHolder(binding.root) {
        private val dateFormatter = SimpleDateFormat("yyyy/MM/dd (E)", Locale.getDefault())
        private val timeFormatter = SimpleDateFormat("HH:mm", Locale.getDefault())

        fun bind(record: Record) {
            binding.tvRecordNameOrDate.text = if (record.name.isNullOrEmpty()) {
                val date = Date(record.startTime)
                "${dateFormatter.format(date)} ${timeFormatter.format(date)}"
            } else {
                record.name
            }

            val startTimeDate = Date(record.startTime)
            val endTimeDate = Date(record.endTime)
            binding.tvRecordTimes.text = "開始: ${timeFormatter.format(startTimeDate)} - 終了: ${timeFormatter.format(endTimeDate)}"

            binding.tvRecordMemoPreview.text = if (record.memo.isNullOrEmpty()) {
                "メモはありません"
            } else {
                record.memo
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecordViewHolder {
        val binding = ItemRecordBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return RecordViewHolder(binding)
    }

    override fun onBindViewHolder(holder: RecordViewHolder, position: Int) {
        val record = records[position]
        holder.bind(record)

        holder.itemView.setOnClickListener {
            onItemClick(record)
        }

        holder.binding.ivDeleteRecord.setOnClickListener {
            onDeleteClick(record)
        }
    }

    override fun getItemCount(): Int {
        return records.size
    }

    /**
     * ★★★ データを更新するための関数を修正 ★★★
     * 新しいリストを受け取り、Adapter内部のリストを更新して、変更を通知する
     */
    fun updateRecords(newRecords: List<Record>) {
        records.clear()
        records.addAll(newRecords)
        notifyDataSetChanged() // RecyclerViewに更新を通知
    }
}