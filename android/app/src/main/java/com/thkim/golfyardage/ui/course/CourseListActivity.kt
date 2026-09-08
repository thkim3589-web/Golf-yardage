package com.thkim.golfyardage.ui.course

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.thkim.golfyardage.data.CourseRepository
import com.thkim.golfyardage.data.WeatherRepository
import com.thkim.golfyardage.data.model.CourseSummary
import com.thkim.golfyardage.data.model.WeatherInfo
import com.thkim.golfyardage.databinding.ActivityCourseListBinding
import com.thkim.golfyardage.ui.round.MainActivity
import kotlinx.coroutines.launch

class CourseListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCourseListBinding
    private val weatherCache = mutableMapOf<String, WeatherInfo?>()
    private val weatherLoading = mutableSetOf<String>()
    private lateinit var adapter: CourseAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCourseListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val courses = CourseRepository.loadCourseSummaries(this)
        binding.textEmpty.visibility = if (courses.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE

        adapter = CourseAdapter(
            items = courses,
            weatherCache = weatherCache,
            onClick = { course -> openCourse(course) },
            onNeedWeather = { course, position -> loadWeather(course, position) }
        )
        binding.recyclerCourses.layoutManager = LinearLayoutManager(this)
        binding.recyclerCourses.adapter = adapter
    }

    private fun openCourse(course: CourseSummary) {
        val intent = Intent(this, MainActivity::class.java)
        intent.putExtra(MainActivity.EXTRA_COURSE_ID, course.id)
        startActivity(intent)
    }

    private fun loadWeather(course: CourseSummary, position: Int) {
        val location = course.location ?: return
        if (weatherLoading.contains(course.id)) return
        weatherLoading.add(course.id)
        lifecycleScope.launch {
            val weather = WeatherRepository.fetchCurrentWeather(location)
            weatherCache[course.id] = weather
            weatherLoading.remove(course.id)
            adapter.notifyItemChanged(position)
        }
    }
}
