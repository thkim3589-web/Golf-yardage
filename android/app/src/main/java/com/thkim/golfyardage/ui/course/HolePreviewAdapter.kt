package com.thkim.golfyardage.ui.course

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.thkim.golfyardage.R
import com.thkim.golfyardage.data.model.Hole
import com.thkim.golfyardage.databinding.ItemHolePreviewBinding

class HolePreviewAdapter(
    private var holes: List<Hole>,
    private val onHoleClick: (Int) -> Unit,
    private val onHoleLongClick: (Int) -> Unit,
    private val onManageClick: (Int) -> Unit
) : RecyclerView.Adapter<HolePreviewAdapter.ViewHolder>() {

    private var selectedPosition = 0

    inner class ViewHolder(val binding: ItemHolePreviewBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemHolePreviewBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun getItemCount(): Int = holes.size

    fun updateHoles(newHoles: List<Hole>) {
        holes = newHoles
        notifyDataSetChanged()
    }

    fun setSelected(position: Int) {
        if (position == selectedPosition) return
        val previous = selectedPosition
        selectedPosition = position
        notifyItemChanged(previous)
        notifyItemChanged(selectedPosition)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val hole = holes[position]
        val b = holder.binding
        val context = holder.itemView.context

        b.textHoleNumber.text = hole.number.toString()
        val parText = context.getString(R.string.par_format, hole.par)
        b.textHolePar.text = buildString {
            append(parText)
            hole.yardageMeters?.let { append(" · ").append(context.getString(R.string.hole_yardage_format, it)) }
            hole.hdcp?.let { append(" · ").append(context.getString(R.string.hole_hdcp_format, it)) }
        }

        if (hole.tee != null || hole.hasGreenData) {
            b.textCoordStatus.text = context.getString(R.string.course_preview_hole_coord_done)
            b.dotCoordStatus.backgroundTintList =
                ColorStateList.valueOf(ContextCompat.getColor(context, R.color.fairway_green_light))
        } else {
            b.textCoordStatus.text = context.getString(R.string.course_preview_hole_coord_missing)
            b.dotCoordStatus.backgroundTintList =
                ColorStateList.valueOf(ContextCompat.getColor(context, R.color.status_coord_missing))
        }

        val isSelected = position == selectedPosition
        holder.itemView.setBackgroundColor(
            if (isSelected) Color.parseColor("#FFE7D9") else Color.TRANSPARENT
        )
        b.selectionBar.visibility = if (isSelected) View.VISIBLE else View.INVISIBLE
        b.textHolePar.setTypeface(null, if (isSelected) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)

        b.layoutHoleDetail.visibility = if (isSelected) View.VISIBLE else View.GONE
        if (isSelected) {
            val strategy = hole.strategyTip?.takeIf { it.isNotBlank() }
                ?: context.getString(R.string.strategy_tip_empty)
            b.textHoleStrategy.text = context.getString(R.string.hole_strategy_format, strategy)

            val slope = hole.slope
            val direction = slope?.direction?.takeIf { it.isNotBlank() }
            val slopeDescription = when {
                direction != null && slope?.degreePercent != null -> "$direction ${slope.degreePercent}%"
                direction != null -> direction
                slope?.degreePercent != null -> "${slope.degreePercent}%"
                else -> context.getString(R.string.hole_slope_none)
            }
            b.textHoleSlope.text = context.getString(R.string.hole_slope_format, slopeDescription)

            b.textHoleGreenSize.visibility = if (hole.greenSizeNote != null) View.VISIBLE else View.GONE
            hole.greenSizeNote?.let {
                b.textHoleGreenSize.text = context.getString(R.string.hole_green_size_format, it)
            }

            if (hole.illustrationFile != null) {
                b.imageHoleIllustration.visibility = View.VISIBLE
                b.imageHoleIllustration.load("file:///android_asset/courses/illustrations/${hole.illustrationFile}")
            } else {
                b.imageHoleIllustration.visibility = View.GONE
            }

            b.btnManageHole.setOnClickListener { onManageClick(position) }
        }

        holder.itemView.setOnClickListener {
            onHoleClick(position)
            setSelected(position)
        }
        holder.itemView.setOnLongClickListener {
            onHoleLongClick(position)
            true
        }
    }
}
