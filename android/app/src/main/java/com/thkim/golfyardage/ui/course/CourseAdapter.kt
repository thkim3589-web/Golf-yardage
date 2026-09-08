package com.thkim.golfyardage.ui.course

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.thkim.golfyardage.data.model.CourseSummary
import com.thkim.golfyardage.data.model.WeatherInfo
import com.thkim.golfyardage.databinding.ItemCourseCardBinding

class CourseAdapter(
    private val items: List<CourseSummary>,
    private val weatherCache: Map<String, WeatherInfo?>,
    private val onClick: (CourseSummary) -> Unit,
    private val onNeedWeather: (CourseSummary, Int) -> Unit
) : RecyclerView.Adapter<CourseAdapter.ViewHolder>() {

    inner class ViewHolder(val binding: ItemCourseCardBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemCourseCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        val b = holder.binding

        b.textCourseName.text = item.name
        b.textCourseRegion.text = "${item.region} · 18홀"

        val imagePath = item.imageFile?.let { "file:///android_asset/courses/images/$it" }
        b.imageCourse.load(imagePath ?: "") {
            error(com.thkim.golfyardage.R.drawable.ic_course_placeholder)
            placeholder(com.thkim.golfyardage.R.drawable.ic_course_placeholder)
            fallback(com.thkim.golfyardage.R.drawable.ic_course_placeholder)
        }

        when {
            item.location == null -> b.textCourseWeather.text = ""
            weatherCache.containsKey(item.id) -> {
                val weather = weatherCache[item.id]
                b.textCourseWeather.text = weather?.let {
                    holder.itemView.context.getString(
                        com.thkim.golfyardage.R.string.weather_format,
                        it.temperatureC.toInt(),
                        it.conditionText,
                        it.windSpeedMs
                    )
                } ?: holder.itemView.context.getString(com.thkim.golfyardage.R.string.weather_unavailable)
            }
            else -> {
                b.textCourseWeather.text = holder.itemView.context.getString(com.thkim.golfyardage.R.string.weather_loading)
                onNeedWeather(item, position)
            }
        }

        holder.itemView.setOnClickListener { onClick(item) }
    }
}
