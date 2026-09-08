package com.thkim.golfyardage.ui.settings

import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.thkim.golfyardage.data.model.ClubDistance
import com.thkim.golfyardage.databinding.ItemClubDistanceBinding

class ClubDistanceAdapter(
    private val items: List<ClubDistance>,
    private val onChanged: (clubName: String, meters: Double?) -> Unit
) : RecyclerView.Adapter<ClubDistanceAdapter.ViewHolder>() {

    inner class ViewHolder(val binding: ItemClubDistanceBinding) : RecyclerView.ViewHolder(binding.root) {
        var watcher: TextWatcher? = null
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemClubDistanceBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        val b = holder.binding

        holder.watcher?.let { b.editDistance.removeTextChangedListener(it) }
        b.textClubName.text = item.clubName
        b.editDistance.setText(item.averageMeters?.toInt()?.toString() ?: "")

        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val meters = s?.toString()?.trim()?.toDoubleOrNull()
                onChanged(item.clubName, meters)
            }
        }
        holder.watcher = watcher
        b.editDistance.addTextChangedListener(watcher)
    }
}
