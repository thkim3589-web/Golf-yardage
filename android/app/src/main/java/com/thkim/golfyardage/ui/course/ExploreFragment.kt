package com.thkim.golfyardage.ui.course

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.location.LocationServices
import com.thkim.golfyardage.R
import com.thkim.golfyardage.data.CourseRepository
import com.thkim.golfyardage.data.FavoritesStore
import com.thkim.golfyardage.data.RecentPlayStore
import com.thkim.golfyardage.data.WeatherRepository
import com.thkim.golfyardage.data.model.CourseSummary
import com.thkim.golfyardage.data.model.GeoPoint
import com.thkim.golfyardage.data.model.WeatherInfo
import com.thkim.golfyardage.databinding.FragmentExploreBinding
import com.thkim.golfyardage.ui.main.MainTabActivity
import com.thkim.golfyardage.util.DistanceUtils
import kotlinx.coroutines.launch

class ExploreFragment : Fragment() {

    private enum class Filter { ALL, FAVORITES, NEARBY }

    private var _binding: FragmentExploreBinding? = null
    private val binding get() = _binding!!

    private val weatherCache = mutableMapOf<String, WeatherInfo?>()
    private val weatherLoading = mutableSetOf<String>()
    private lateinit var adapter: CourseAdapter
    private lateinit var nearbyAdapter: NearbyCourseAdapter
    private var allCourses: List<CourseSummary> = emptyList()
    private var activeFilter = Filter.ALL
    private var myLocation: GeoPoint? = null

    private val requestLocationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) fetchLocationAndApplyNearby() else activeFilter = Filter.ALL
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentExploreBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val context = requireContext()

        allCourses = CourseRepository.loadCourseSummaries(context)

        adapter = CourseAdapter(
            items = emptyList(),
            weatherCache = weatherCache,
            onClick = { course -> openCourse(course) },
            onNeedWeather = { course, position -> loadWeather(course, position) },
            onFavoriteToggle = {
                if (activeFilter == Filter.FAVORITES) refreshList()
            }
        )
        binding.recyclerCourses.layoutManager = LinearLayoutManager(context)
        binding.recyclerCourses.adapter = adapter

        nearbyAdapter = NearbyCourseAdapter(items = emptyList(), onClick = { course -> openCourse(course) })
        binding.recyclerNearby.layoutManager = LinearLayoutManager(context, RecyclerView.HORIZONTAL, false)
        binding.recyclerNearby.adapter = nearbyAdapter

        binding.editSearchCourse.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                refreshList()
            }
        })

        binding.btnFilterFavorites.setOnClickListener {
            activeFilter = if (activeFilter == Filter.FAVORITES) Filter.ALL else Filter.FAVORITES
            updateFilterButtonStyles()
            refreshList()
        }
        binding.btnFilterNearby.setOnClickListener {
            if (activeFilter == Filter.NEARBY) {
                activeFilter = Filter.ALL
                updateFilterButtonStyles()
                refreshList()
            } else {
                ensureLocationPermissionThenApplyNearby()
            }
        }

        binding.btnOpenSettings.setOnClickListener {
            (activity as? MainTabActivity)?.switchToTab(R.id.nav_mypage)
        }

        updateFilterButtonStyles()
        setupRecentPlayCard()
        refreshList()

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            fetchLocationSilently()
        }
    }

    override fun onResume() {
        super.onResume()
        setupRecentPlayCard()
        if (activeFilter == Filter.FAVORITES) refreshList()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun setupRecentPlayCard() {
        val lastId = RecentPlayStore.lastPlayedCourseId(requireContext())
        val lastCourse = allCourses.find { it.id == lastId }
        if (lastCourse == null) {
            binding.cardRecentPlay.visibility = View.GONE
            return
        }
        binding.cardRecentPlay.visibility = View.VISIBLE
        binding.textRecentPlayName.text = lastCourse.name
        binding.btnRecentPlayStart.setOnClickListener { openCourse(lastCourse) }
    }

    private fun updateFilterButtonStyles() {
        val context = requireContext()
        val selectedColor = ContextCompat.getColor(context, R.color.explore_accent)
        val unselectedColor = ContextCompat.getColor(context, R.color.explore_surface)
        val selectedText = ContextCompat.getColor(context, R.color.explore_bg)
        val unselectedText = ContextCompat.getColor(context, R.color.explore_text_primary)

        binding.btnFilterFavorites.backgroundTintList = android.content.res.ColorStateList.valueOf(
            if (activeFilter == Filter.FAVORITES) selectedColor else unselectedColor
        )
        binding.btnFilterFavorites.setTextColor(if (activeFilter == Filter.FAVORITES) selectedText else unselectedText)

        binding.btnFilterNearby.backgroundTintList = android.content.res.ColorStateList.valueOf(
            if (activeFilter == Filter.NEARBY) selectedColor else unselectedColor
        )
        binding.btnFilterNearby.setTextColor(if (activeFilter == Filter.NEARBY) selectedText else unselectedText)
    }

    private fun ensureLocationPermissionThenApplyNearby() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            fetchLocationAndApplyNearby()
        } else {
            requestLocationPermission.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    @SuppressLint("MissingPermission")
    private fun fetchLocationAndApplyNearby() {
        val context = requireContext()
        LocationServices.getFusedLocationProviderClient(context).lastLocation
            .addOnSuccessListener { location ->
                if (_binding == null) return@addOnSuccessListener
                if (location == null) {
                    Toast.makeText(context, getString(R.string.gps_waiting), Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }
                myLocation = GeoPoint(location.latitude, location.longitude)
                activeFilter = Filter.NEARBY
                updateFilterButtonStyles()
                refreshList()
                updateNearbySection()
            }
    }

    @SuppressLint("MissingPermission")
    private fun fetchLocationSilently() {
        LocationServices.getFusedLocationProviderClient(requireContext()).lastLocation
            .addOnSuccessListener { location ->
                if (_binding == null || location == null) return@addOnSuccessListener
                myLocation = GeoPoint(location.latitude, location.longitude)
                updateNearbySection()
            }
    }

    private fun updateNearbySection() {
        val origin = myLocation
        val withLocation = allCourses.filter { it.location != null }
        if (origin == null || withLocation.isEmpty()) {
            binding.sectionNearby.visibility = View.GONE
            return
        }
        val nearby = withLocation
            .map { NearbyCourse(it, DistanceUtils.distanceMeters(origin, it.location!!)) }
            .sortedBy { it.distanceMeters }
            .take(10)
        nearbyAdapter.updateItems(nearby)
        binding.sectionNearby.visibility = View.VISIBLE
    }

    private fun refreshList() {
        val context = requireContext()
        val query = binding.editSearchCourse.text?.toString()?.trim().orEmpty()

        val base = when (activeFilter) {
            Filter.ALL -> allCourses
            Filter.FAVORITES -> {
                val favoriteIds = FavoritesStore.all(context)
                allCourses.filter { it.id in favoriteIds }
            }
            Filter.NEARBY -> allCourses.filter { it.location != null }
        }

        val filtered = if (query.isEmpty()) {
            base
        } else {
            base.filter {
                it.name.contains(query, ignoreCase = true) || it.region.contains(query, ignoreCase = true)
            }
        }

        val listItems = mutableListOf<CourseListItem>()
        if (activeFilter == Filter.NEARBY && myLocation != null) {
            val origin = myLocation!!
            filtered
                .map { it to DistanceUtils.distanceMeters(origin, it.location!!) }
                .sortedBy { it.second }
                .forEach { (summary, distance) -> listItems.add(CourseListItem.Row(summary, distance)) }
        } else {
            filtered.groupBy { it.region }.forEach { (region, coursesInRegion) ->
                listItems.add(CourseListItem.Header(region))
                coursesInRegion.forEach { listItems.add(CourseListItem.Row(it)) }
            }
        }
        adapter.updateItems(listItems)

        binding.textEmpty.text = when {
            allCourses.isEmpty() -> getString(R.string.course_list_empty)
            activeFilter == Filter.FAVORITES && FavoritesStore.all(context).isEmpty() -> getString(R.string.favorites_empty)
            activeFilter == Filter.NEARBY && base.isEmpty() -> getString(R.string.nearby_no_location)
            else -> getString(R.string.course_search_no_result)
        }
        binding.textEmpty.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun openCourse(course: CourseSummary) {
        val intent = Intent(requireContext(), CoursePreviewActivity::class.java)
        intent.putExtra(CoursePreviewActivity.EXTRA_COURSE_ID, course.id)
        startActivity(intent)
    }

    private fun loadWeather(course: CourseSummary, position: Int) {
        val location = course.location ?: return
        if (weatherLoading.contains(course.id)) return
        weatherLoading.add(course.id)
        viewLifecycleOwner.lifecycleScope.launch {
            val weather = WeatherRepository.fetchCurrentWeather(location)
            weatherCache[course.id] = weather
            weatherLoading.remove(course.id)
            if (_binding != null) adapter.notifyItemChanged(position)
        }
    }
}
