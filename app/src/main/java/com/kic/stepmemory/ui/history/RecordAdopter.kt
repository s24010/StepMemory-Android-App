package com.kic.stepmemory.ui.history

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.kic.stepmemory.data.Record
import com.kic.stepmemory.databinding.ItemRecordBinding // item_record.xml のView Binding
import java.text.SimpleDateFormat
import java.util.*

/**
 * 記録履歴リストを表示するためのRecyclerViewのアダプターです。
 * 各記録データをRecyclerViewの項目にバインドします。
 */
class RecordAdapter(
    private val records: MutableList<Record>, // 記録データのリスト
    private val onItemClick: (Record) -> Unit // アイテムクリック時のコールバック関数
) : RecyclerView.Adapter<RecordAdapter.RecordViewHolder>() {

    /**
     * 各リストアイテムのビューを保持するViewHolderクラスです。
     * View Bindingを使用してレイアウトのビューにアクセスします。
     */
    class RecordViewHolder(private val binding: ItemRecordBinding) : RecyclerView.ViewHolder(binding.root) {
        // SimpleDateFormatはスレッドセーフではないので、formatter関数内で毎回インスタンスを作成します。
        // または、スレッドセーフなDateTimeFormatter (Java 8以降) を使用します。
        private val dateFormatter = SimpleDateFormat("yyyy/MM/dd (E)", Locale.getDefault())
        private val timeFormatter = SimpleDateFormat("HH:mm", Locale.getDefault())

        /**
         * Recordデータに基づいてリストアイテムのUIを更新します。
         */
        fun bind(record: Record) {
            // 記録名が存在すれば表示、なければ日付と開始時刻を組み合わせたものを表示
            if (record.name.isNullOrEmpty()) {
                val date = Date(record.startTime)
                binding.tvRecordNameOrDate.text = "${dateFormatter.format(date)} ${timeFormatter.format(date)}"
            } else {
                binding.tvRecordNameOrDate.text = record.name
            }

            // 開始時刻と終了時刻を表示
            val startTimeDate = Date(record.startTime)
            val endTimeDate = Date(record.endTime)
            binding.tvRecordTimes.text = "開始: ${timeFormatter.format(startTimeDate)} - 終了: ${timeFormatter.format(endTimeDate)}"

            // メモプレビューを表示
            if (record.memo.isNullOrEmpty()) {
                binding.tvRecordMemoPreview.text = "メモはありません"
            } else {
                binding.tvRecordMemoPreview.text = record.memo
            }
        }
    }

    /**
     * ViewHolderが作成される際に呼び出されます。
     * item_record.xml レイアウトをインフレートし、ViewHolderを返します。
     */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecordViewHolder {
        val binding = ItemRecordBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return RecordViewHolder(binding)
    }

    /**
     * 指定された位置のデータをViewHolderにバインドする際に呼び出されます。
     */
    override fun onBindViewHolder(holder: RecordViewHolder, position: Int) {
        val record = records[position]
        holder.bind(record) // ViewHolderにデータをバインド

        // アイテム全体のクリックリスナーを設定
        holder.itemView.setOnClickListener {
            onItemClick(record) // コールバック関数を呼び出す
        }
    }

    /**
     * リスト内のアイテムの総数を返します。
     */
    override fun getItemCount(): Int {
        return records.size
    }

    /**
     * 新しい記録リストでアダプターのデータを更新します。
     */
    fun updateRecords(newRecords: List<Record>) {
        records.clear()
        records.addAll(newRecords)
        notifyDataSetChanged() // データ変更をRecyclerViewに通知してUIを更新
    }
}