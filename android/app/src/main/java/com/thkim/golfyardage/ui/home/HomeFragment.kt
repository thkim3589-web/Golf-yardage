package com.thkim.golfyardage.ui.home

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.thkim.golfyardage.R
import com.thkim.golfyardage.data.CourseRepository
import com.thkim.golfyardage.data.FavoritesStore
import com.thkim.golfyardage.data.RecentPlayStore
import com.thkim.golfyardage.data.WeatherRepository
import com.thkim.golfyardage.databinding.FragmentHomeBinding
import com.thkim.golfyardage.databinding.ItemFavoriteWeatherBinding
import com.thkim.golfyardage.ui.course.CoursePreviewActivity
import com.thkim.golfyardage.ui.main.MainTabActivity
import kotlinx.coroutines.launch

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnGoExplore.setOnClickListener {
            (activity as? MainTabActivity)?.switchToTab(R.id.nav_explore)
        }

        loadRecentPlay()
        loadFavoritesWeather()
    }

    override fun onResume() {
        super.onResume()
        loadRecentPlay()
        loadFavoritesWeather()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun loadRecentPlay() {
        val context = requireContext()
        val lastId = RecentPlayStore.lastPlayedCourseId(context)
        val course = lastId?.let { id -> CourseRepository.loadCourseSummaries(context).find { it.id == id } }

        if (course == null) {
            binding.cardRecentPlay.visibility = View.GONE
            binding.textRecentPlayEmpty.visibility = View.VISIBLE
            return
        }
        binding.textRecentPlayEmpty.visibility = View.GONE
        binding.cardRecentPlay.visibility = View.VISIBLE
        binding.textRecentCourseName.text = course.name
        binding.textRecentWeather.text = ""
        binding.btnResumeRound.setOnClickListener {
            startActivity(
                Intent(context, CoursePreviewActivity::class.java)
                    .putExtra(CoursePreviewActivity.EXTRA_COURSE_ID, course.id)
            )
        }

        val location = course.location ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            val weather = WeatherRepository.fetchCurrentWeather(location)
            if (_binding == null) return@launch
            binding.textRecentWeather.text = weather?.let {
                getString(R.string.weather_format, it.temperatureC.toInt(), it.conditionText, it.windSpeedMs)
            } ?: getString(R.string.weather_unavailable)
        }
    }

    private fun loadFavoritesWeather() {
        val context = requireContext()
        val favoriteIds = FavoritesStore.all(context)
        val favorites = CourseRepository.loadCourseSummaries(context).filter { it.id in favoriteIds }

        binding.containerFavoritesWeather.removeAllViews()
        if (favorites.isEmpty()) {
            binding.textFavoritesEmpty.visibility = View.VISIBLE
            return
        }
        binding.textFavoritesEmpty.visibility = View.GONE

        favorites.forEach { course ->
            val item = ItemFavoriteWeatherBinding.inflate(LayoutInflater.from(context), binding.containerFavoritesWeather, false)
            item.textCourseName.text = course.name
            item.textWeather.text = getString(R.string.weather_loading)
            binding.containerFavoritesWeather.addView(item.root)

            val location = course.location
            if (location == null) {
                item.textWeather.text = ""
                return@forEach
            }
            viewLifecycleOwner.lifecycleScope.launch {
                val weather = WeatherRepository.fetchCurrentWeather(location)
                if (_binding == null) return@launch
                item.textWeather.text = weather?.let {
                    getString(R.string.weather_format, it.temperatureC.toInt(), it.conditionText, it.windSpeedMs)
                } ?: getString(R.string.weather_unavailable)
            }
        }
    }
}
