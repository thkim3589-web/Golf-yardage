package com.thkim.golfyardage.ui.mypage

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.thkim.golfyardage.BuildConfig
import com.thkim.golfyardage.R
import com.thkim.golfyardage.data.CourseRepository
import com.thkim.golfyardage.data.FavoritesStore
import com.thkim.golfyardage.databinding.FragmentMypageBinding
import com.thkim.golfyardage.databinding.ItemFavoriteWeatherBinding
import com.thkim.golfyardage.ui.course.CoursePreviewActivity
import com.thkim.golfyardage.ui.settings.ClubSettingsActivity
import com.thkim.golfyardage.util.DistanceUnit

class MyPageFragment : Fragment() {

    private var _binding: FragmentMypageBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMypageBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val context = requireContext()

        binding.switchUnit.isChecked = loadUnitPref() == DistanceUnit.YARD
        updateUnitSwitchText(binding.switchUnit.isChecked)
        binding.switchUnit.setOnCheckedChangeListener { _, checked ->
            saveUnitPref(if (checked) DistanceUnit.YARD else DistanceUnit.METER)
            updateUnitSwitchText(checked)
        }

        binding.menuClubs.setOnClickListener {
            startActivity(Intent(context, ClubSettingsActivity::class.java))
        }

        binding.textAppVersion.text = getString(R.string.mypage_app_version_format, BuildConfig.VERSION_NAME)

        loadFavorites()
    }

    override fun onResume() {
        super.onResume()
        loadFavorites()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun loadFavorites() {
        val context = requireContext()
        val favoriteIds = FavoritesStore.all(context)
        val favorites = CourseRepository.loadCourseSummaries(context).filter { it.id in favoriteIds }

        binding.containerFavorites.removeAllViews()
        binding.textFavoritesEmpty.visibility = if (favorites.isEmpty()) View.VISIBLE else View.GONE

        favorites.forEach { course ->
            val item = ItemFavoriteWeatherBinding.inflate(LayoutInflater.from(context), binding.containerFavorites, false)
            item.textCourseName.text = course.name
            item.textWeather.text = course.region
            item.root.setOnClickListener {
                startActivity(
                    Intent(context, CoursePreviewActivity::class.java)
                        .putExtra(CoursePreviewActivity.EXTRA_COURSE_ID, course.id)
                )
            }
            binding.containerFavorites.addView(item.root)
        }
    }

    private fun updateUnitSwitchText(isYard: Boolean) {
        val label = if (isYard) getString(R.string.unit_yard) else getString(R.string.unit_meter)
        binding.switchUnit.text = label.uppercase()
    }

    private fun loadUnitPref(): DistanceUnit {
        val prefs = requireContext().getSharedPreferences("settings", 0)
        return if (prefs.getBoolean("unit_yard", false)) DistanceUnit.YARD else DistanceUnit.METER
    }

    private fun saveUnitPref(unit: DistanceUnit) {
        requireContext().getSharedPreferences("settings", 0).edit()
            .putBoolean("unit_yard", unit == DistanceUnit.YARD)
            .apply()
    }
}
