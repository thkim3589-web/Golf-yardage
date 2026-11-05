package com.thkim.golfyardage.ui.notifications

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.thkim.golfyardage.R
import com.thkim.golfyardage.data.ActivityLogStore
import com.thkim.golfyardage.data.CourseRepository
import com.thkim.golfyardage.data.FavoritesStore
import com.thkim.golfyardage.data.WeatherRepository
import com.thkim.golfyardage.databinding.FragmentNotificationsBinding
import com.thkim.golfyardage.databinding.ItemNotificationBinding
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class NotificationsFragment : Fragment() {

    private var _binding: FragmentNotificationsBinding? = null
    private val binding get() = _binding!!
    private val timeFormat = SimpleDateFormat("MM.dd HH:mm", Locale.KOREA)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentNotificationsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadNotifications()
    }

    override fun onResume() {
        super.onResume()
        loadNotifications()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun loadNotifications() {
        val context = requireContext()
        binding.containerNotifications.removeAllViews()

        val activityEntries = ActivityLogStore.loadAll(context)
        activityEntries.forEach { entry ->
            addRow(entry.message, timeFormat.format(Date(entry.dateMillis)), R.color.explore_text_secondary)
        }
        binding.textEmpty.visibility = if (activityEntries.isEmpty()) View.VISIBLE else View.GONE

        val favoriteIds = FavoritesStore.all(context)
        val favorites = CourseRepository.loadCourseSummaries(context).filter { it.id in favoriteIds }
        favorites.forEach { course ->
            val location = course.location ?: return@forEach
            viewLifecycleOwner.lifecycleScope.launch {
                val weather = WeatherRepository.fetchCurrentWeather(location) ?: return@launch
                if (_binding == null) return@launch
                val alert = when {
                    weather.conditionText in listOf("이슬비", "비", "소나기", "눈 소나기", "뇌우") ->
                        getString(R.string.weather_alert_rain)
                    weather.windSpeedMs >= 8.0 -> getString(R.string.weather_alert_wind)
                    else -> null
                } ?: return@launch
                binding.textEmpty.visibility = View.GONE
                addRow(
                    getString(R.string.notification_weather_alert_format, course.name, alert),
                    timeFormat.format(Date()),
                    R.color.accent_orange,
                    atTop = true
                )
            }
        }
    }

    private fun addRow(message: String, time: String, dotColorRes: Int, atTop: Boolean = false) {
        val item = ItemNotificationBinding.inflate(LayoutInflater.from(requireContext()), binding.containerNotifications, false)
        item.textMessage.text = message
        item.textTime.text = time
        item.dotIndicator.backgroundTintList = ColorStateList.valueOf(resources.getColor(dotColorRes, null))
        if (atTop) {
            binding.containerNotifications.addView(item.root, 0)
        } else {
            binding.containerNotifications.addView(item.root)
        }
    }
}
