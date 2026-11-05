package com.thkim.golfyardage.ui.course

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.thkim.golfyardage.data.FavoritesStore
import com.thkim.golfyardage.data.RatingStore
import com.thkim.golfyardage.data.model.CourseSummary
import com.thkim.golfyardage.data.model.WeatherInfo
import com.thkim.golfyardage.databinding.ItemCourseCardBinding
import com.thkim.golfyardage.databinding.ItemRegionHeaderBinding

/** 지역별로 구분해 보여주기 위한 목록 항목. 헤더와 코스 카드가 섞여 있다. */
sealed class CourseListItem {
    data class Header(val region: String) : CourseListItem()
    data class Row(val summary: CourseSummary, val distanceMeters: Double? = null) : CourseListItem()
}

class CourseAdapter(
    private var items: List<CourseListItem>,
    private val weatherCache: Map<String, WeatherInfo?>,
    private val onClick: (CourseSummary) -> Unit,
    private val onNeedWeather: (CourseSummary, Int) -> Unit,
    private val onFavoriteToggle: (CourseSummary) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    fun updateItems(newItems: List<CourseListItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    private companion object {
        const val TYPE_HEADER = 0
        const val TYPE_COURSE = 1
    }

    inner class HeaderViewHolder(val binding: ItemRegionHeaderBinding) : RecyclerView.ViewHolder(binding.root)
    inner class CourseViewHolder(val binding: ItemCourseCardBinding) : RecyclerView.ViewHolder(binding.root)

    override fun getItemViewType(position: Int): Int = when (items[position]) {
        is CourseListItem.Header -> TYPE_HEADER
        is CourseListItem.Row -> TYPE_COURSE
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_HEADER) {
            HeaderViewHolder(ItemRegionHeaderBinding.inflate(inflater, parent, false))
        } else {
            CourseViewHolder(ItemCourseCardBinding.inflate(inflater, parent, false))
        }
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is CourseListItem.Header -> {
                (holder as HeaderViewHolder).binding.textRegionHeader.text = item.region
            }
            is CourseListItem.Row -> bindCourse(holder as CourseViewHolder, item, position)
        }
    }

    private fun bindCourse(holder: CourseViewHolder, row: CourseListItem.Row, position: Int) {
        val b = holder.binding
        val item = row.summary
        val context = holder.itemView.context

        b.textCourseName.text = item.name
        b.textCourseRegion.text = "${item.region} · 18홀"

        val myRating = RatingStore.getRating(context, item.id)
        if (myRating > 0) {
            b.textMyRating.text = "★ $myRating"
            b.textMyRating.visibility = View.VISIBLE
        } else {
            b.textMyRating.visibility = View.GONE
        }

        if (row.distanceMeters != null) {
            b.textDistance.text = if (row.distanceMeters >= 1000) {
                "%.1fkm".format(row.distanceMeters / 1000)
            } else {
                "%.0fm".format(row.distanceMeters)
            }
            b.textDistance.visibility = View.VISIBLE
        } else {
            b.textDistance.visibility = View.GONE
        }

        val logoPath = item.logoFile?.let { "file:///android_asset/courses/logos/$it" }
        val density = context.resources.displayMetrics.density
        if (logoPath != null) {
            val padding = (7 * density).toInt()
            b.imageCourse.setPadding(padding, padding, padding, padding)
            b.imageCourse.scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
            b.imageCourse.load(logoPath) {
                error(CourseCardArt.build(item.name))
                fallback(CourseCardArt.build(item.name))
            }
        } else {
            b.imageCourse.setPadding(0, 0, 0, 0)
            b.imageCourse.scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
            b.imageCourse.setImageDrawable(CourseCardArt.build(item.name))
        }

        when {
            item.location == null -> b.textCourseWeather.text = ""
            weatherCache.containsKey(item.id) -> {
                val weather = weatherCache[item.id]
                b.textCourseWeather.text = weather?.let {
                    context.getString(
                        com.thkim.golfyardage.R.string.weather_format,
                        it.temperatureC.toInt(),
                        it.conditionText,
                        it.windSpeedMs
                    )
                } ?: context.getString(com.thkim.golfyardage.R.string.weather_unavailable)
            }
            else -> {
                b.textCourseWeather.text = context.getString(com.thkim.golfyardage.R.string.weather_loading)
                onNeedWeather(item, position)
            }
        }

        val isFavorite = FavoritesStore.isFavorite(context, item.id)
        if (isFavorite) {
            b.btnFavorite.imageTintList = null
            b.btnFavorite.setImageResource(com.thkim.golfyardage.R.drawable.ic_star_filled)
        } else {
            b.btnFavorite.setImageResource(com.thkim.golfyardage.R.drawable.ic_star_outline)
            b.btnFavorite.imageTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.WHITE)
        }
        b.btnFavorite.setOnClickListener {
            FavoritesStore.toggle(context, item.id)
            notifyItemChanged(position)
            onFavoriteToggle(item)
        }

        holder.itemView.setOnClickListener { onClick(item) }
    }
}
