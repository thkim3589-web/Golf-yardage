package com.thkim.golfyardage.ui.scorecard

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.thkim.golfyardage.R
import com.thkim.golfyardage.data.model.RoundRecord
import com.thkim.golfyardage.databinding.ItemRoundRecordBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RoundHistoryAdapter(
    private var items: List<RoundRecord>,
    private val onLongClick: (RoundRecord) -> Unit
) : RecyclerView.Adapter<RoundHistoryAdapter.ViewHolder>() {

    private val dateFormat = SimpleDateFormat("yyyy.MM.dd", Locale.KOREA)

    inner class ViewHolder(val binding: ItemRoundRecordBinding) : RecyclerView.ViewHolder(binding.root)

    fun updateItems(newItems: List<RoundRecord>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemRoundRecordBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val record = items[position]
        val b = holder.binding
        val context = holder.itemView.context

        b.textCourseName.text = record.courseName
        b.textDate.text = "${dateFormat.format(Date(record.dateMillis))} · ${record.holeScores.size}홀 플레이"
        b.textTotal.text = context.getString(R.string.scorecard_total_format, record.totalStrokes)

        holder.itemView.setOnLongClickListener {
            onLongClick(record)
            true
        }
    }
}
