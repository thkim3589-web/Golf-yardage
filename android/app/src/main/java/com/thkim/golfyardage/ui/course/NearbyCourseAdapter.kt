package com.thkim.golfyardage.ui.course

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.thkim.golfyardage.data.model.CourseSummary
import com.thkim.golfyardage.databinding.ItemNearbyCourseCardBinding

data class NearbyCourse(val summary: CourseSummary, val distanceMeters: Double)

class NearbyCourseAdapter(
    private var items: List<NearbyCourse>,
    private val onClick: (CourseSummary) -> Unit
) : RecyclerView.Adapter<NearbyCourseAdapter.ViewHolder>() {

    inner class ViewHolder(val binding: ItemNearbyCourseCardBinding) : RecyclerView.ViewHolder(binding.root)

    fun updateItems(newItems: List<NearbyCourse>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemNearbyCourseCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        val b = holder.binding

        b.textCourseName.text = item.summary.name
        b.textCourseRegion.text = item.summary.region
        b.textDistance.text = if (item.distanceMeters >= 1000) {
            "%.1fkm".format(item.distanceMeters / 1000)
        } else {
            "%.0fm".format(item.distanceMeters)
        }

        val logoPath = item.summary.logoFile?.let { "file:///android_asset/courses/logos/$it" }
        val density = b.imageCourse.resources.displayMetrics.density
        if (logoPath != null) {
            b.frameCourseLogo.setBackgroundColor(android.graphics.Color.WHITE)
            val padding = (12 * density).toInt()
            b.imageCourse.setPadding(padding, padding, padding, padding)
            b.imageCourse.scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
            b.imageCourse.load(logoPath) {
                error(CourseCardArt.build(item.summary.name))
                fallback(CourseCardArt.build(item.summary.name))
            }
        } else {
            b.frameCourseLogo.background = null
            b.imageCourse.setPadding(0, 0, 0, 0)
            b.imageCourse.scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
            b.imageCourse.setImageDrawable(CourseCardArt.build(item.summary.name))
        }

        holder.itemView.setOnClickListener { onClick(item.summary) }
    }
}
