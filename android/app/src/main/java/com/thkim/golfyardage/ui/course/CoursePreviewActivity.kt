package com.thkim.golfyardage.ui.course

import android.app.AlertDialog
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.thkim.golfyardage.R
import com.thkim.golfyardage.data.ActivityLogStore
import com.thkim.golfyardage.data.CourseOverrideStore
import com.thkim.golfyardage.data.CourseRepository
import com.thkim.golfyardage.data.KakaoLocalRepository
import com.thkim.golfyardage.data.RatingStore
import com.thkim.golfyardage.data.RecentPlayStore
import com.thkim.golfyardage.data.model.Course
import com.thkim.golfyardage.data.model.GeoPoint
import com.thkim.golfyardage.databinding.ActivityCoursePreviewBinding
import com.thkim.golfyardage.ui.round.MainActivity
import kotlinx.coroutines.launch

class CoursePreviewActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_COURSE_ID = "extra_course_id"
    }

    private lateinit var binding: ActivityCoursePreviewBinding
    private lateinit var course: Course
    private lateinit var holeAdapter: HolePreviewAdapter
    private var selectedHoleIndex = 0
    private var isRegisterMode = false
    private var isSatelliteMap = true
    private var googleMap: GoogleMap? = null
    private val holeMarkers = mutableMapOf<Int, Marker>()
    private var searchedLocation: GeoPoint? = null

    /** 좌표가 하나도 입력되지 않은 코스를 열었을 때 지도의 기본 중심(서울 시청 부근) */
    private val defaultFocus = GeoPoint(37.5665, 126.9780)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCoursePreviewBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.mapView.onCreate(savedInstanceState)

        val courseId = intent.getStringExtra(EXTRA_COURSE_ID)
        if (courseId == null) {
            finish()
            return
        }
        course = CourseRepository.loadCourse(this, courseId)

        binding.toolbar.title = course.name
        binding.toolbar.setNavigationOnClickListener { finish() }

        updateSummaryText()

        holeAdapter = HolePreviewAdapter(
            holes = course.holes,
            onHoleClick = { position ->
                selectedHoleIndex = position
                updateHoleStats(position)
            },
            onHoleLongClick = { position -> confirmClearTee(position) },
            onManageClick = { position ->
                com.thkim.golfyardage.ui.management.CourseManagementActivity.start(
                    this, course.id, course.holes[position].number
                )
            }
        )
        binding.recyclerHoles.layoutManager = LinearLayoutManager(this)
        binding.recyclerHoles.adapter = holeAdapter
        updateHoleStats(selectedHoleIndex)

        binding.btnRegisterTee.setOnClickListener { toggleRegisterMode() }

        binding.editLocationSearch.setText(course.name)
        binding.btnSearchLocation.setOnClickListener { searchLocation() }
        binding.editLocationSearch.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH ||
                (event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)
            ) {
                searchLocation()
                true
            } else {
                false
            }
        }
        binding.btnSaveSearchedLocation.setOnClickListener { saveSearchedLocation() }

        setupRatingStars()

        binding.btnStartRound.setOnClickListener {
            RecentPlayStore.recordPlay(this, course.id)
            ActivityLogStore.log(this, getString(R.string.activity_log_round_started_format, course.name))
            startActivity(
                Intent(this, MainActivity::class.java)
                    .putExtra(MainActivity.EXTRA_COURSE_ID, course.id)
                    .putExtra(MainActivity.EXTRA_START_HOLE_INDEX, selectedHoleIndex)
            )
        }

        setupMap()
    }

    override fun onStart() {
        super.onStart()
        binding.mapView.onStart()
    }

    override fun onResume() {
        super.onResume()
        binding.mapView.onResume()
    }

    override fun onPause() {
        binding.mapView.onPause()
        super.onPause()
    }

    override fun onStop() {
        binding.mapView.onStop()
        super.onStop()
    }

    override fun onDestroy() {
        binding.mapView.onDestroy()
        super.onDestroy()
    }

    private fun updateSummaryText() {
        val totalPar = course.holes.sumOf { it.par }
        var text = "${course.region} · " +
            getString(R.string.course_preview_summary_format, course.holes.size, totalPar)

        val holesWithYardage = course.holes.filter { it.yardageMeters != null }
        if (holesWithYardage.isNotEmpty()) {
            val totalYardage = holesWithYardage.sumOf { it.yardageMeters!! }
            text += if (holesWithYardage.size == course.holes.size) {
                " · " + getString(R.string.course_preview_yardage_format, totalYardage)
            } else {
                " · " + getString(R.string.course_preview_yardage_partial_format, totalYardage, holesWithYardage.size)
            }
        }
        binding.textSummary.text = text
    }

    private fun updateHoleStats(position: Int) {
        val hole = course.holes.getOrNull(position) ?: return
        val none = getString(R.string.stat_value_none)
        binding.containerHoleStats.visibility = View.VISIBLE
        binding.textStatPar.text = hole.par.toString()
        binding.textStatYardage.text = hole.yardageMeters?.let { getString(R.string.hole_yardage_format, it) } ?: none
        binding.textStatHdcp.text = hole.hdcp?.toString() ?: none
    }

    private fun toggleMapType() {
        isSatelliteMap = !isSatelliteMap
        saveMapTypePref(isSatelliteMap)
        binding.btnMapType.text = getString(if (isSatelliteMap) R.string.map_type_satellite else R.string.map_type_normal)
        googleMap?.mapType = if (isSatelliteMap) GoogleMap.MAP_TYPE_HYBRID else GoogleMap.MAP_TYPE_NORMAL
    }

    private fun loadMapTypePref(): Boolean =
        getSharedPreferences("settings", MODE_PRIVATE).getBoolean("map_satellite", true)

    private fun saveMapTypePref(value: Boolean) {
        getSharedPreferences("settings", MODE_PRIVATE).edit().putBoolean("map_satellite", value).apply()
    }

    private fun mapFocusPoint(): GeoPoint? =
        course.location
            ?: course.holes.firstNotNullOfOrNull { it.tee ?: it.greenCenter }

    /**
     * 좌표가 하나도 없어도 지도는 항상 띄운다(기본 위치에서 자유롭게 패닝/줌 가능).
     * 코스 좌표가 있으면 그 지점으로, 없으면 기본 위치로 카메라를 맞춘다.
     */
    private fun setupMap() {
        val focus = mapFocusPoint()
        binding.textMapPlaceholder.visibility = if (focus == null) View.VISIBLE else View.GONE

        binding.btnMapType.setOnClickListener { toggleMapType() }
        isSatelliteMap = loadMapTypePref()
        binding.btnMapType.text = getString(if (isSatelliteMap) R.string.map_type_satellite else R.string.map_type_normal)

        binding.mapView.getMapAsync { map ->
            googleMap = map
            map.mapType = if (isSatelliteMap) GoogleMap.MAP_TYPE_HYBRID else GoogleMap.MAP_TYPE_NORMAL
            syncHoleMarkers(map)
            map.moveCamera(CameraUpdateFactory.newLatLngZoom((focus ?: defaultFocus).toLatLng(), 17f))
            map.setOnMapClickListener { latLng ->
                if (isRegisterMode) {
                    registerTeeForSelectedHole(GeoPoint(latLng.latitude, latLng.longitude))
                }
            }
        }
    }

    private fun syncHoleMarkers(map: GoogleMap) {
        course.holes.forEach { hole ->
            val point = hole.tee ?: hole.greenCenter
            val existing = holeMarkers[hole.number]
            when {
                point == null -> existing?.isVisible = false
                existing == null -> {
                    val marker = map.addMarker(
                        MarkerOptions().position(point.toLatLng()).title("${hole.number}번 홀")
                    )
                    if (marker != null) holeMarkers[hole.number] = marker
                }
                else -> {
                    existing.isVisible = true
                    existing.position = point.toLatLng()
                }
            }
        }
    }

    // ---------- 내 별점 ----------

    private fun setupRatingStars() {
        renderRatingStars()
    }

    private fun renderRatingStars() {
        val container = binding.containerRatingStars
        container.removeAllViews()
        val myRating = RatingStore.getRating(this, course.id)

        binding.textMyRatingLabel.text =
            if (myRating > 0) getString(R.string.rating_my_rating) else getString(R.string.rating_not_rated)

        for (star in 1..5) {
            val starView = android.widget.ImageView(this)
            val size = (20 * resources.displayMetrics.density).toInt()
            val margin = (2 * resources.displayMetrics.density).toInt()
            val params = android.widget.LinearLayout.LayoutParams(size, size)
            params.marginEnd = margin
            starView.layoutParams = params

            if (star <= myRating) {
                starView.setImageResource(R.drawable.ic_star_filled)
                starView.imageTintList = null
            } else {
                starView.setImageResource(R.drawable.ic_star_outline)
                starView.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.explore_text_secondary))
            }
            starView.setOnClickListener {
                val newRating = if (star == myRating) 0 else star
                RatingStore.setRating(this, course.id, newRating)
                renderRatingStars()
            }
            container.addView(starView)
        }
    }

    // ---------- 위치 검색 ----------

    private fun searchLocation() {
        val query = binding.editLocationSearch.text?.toString()?.trim().orEmpty()
        if (query.isEmpty()) return
        lifecycleScope.launch {
            val results = KakaoLocalRepository.searchKeyword(query)
            val top = results.firstOrNull()
            if (top == null) {
                Toast.makeText(this@CoursePreviewActivity, getString(R.string.location_search_not_found), Toast.LENGTH_SHORT).show()
                return@launch
            }
            searchedLocation = top.point
            googleMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(top.point.toLatLng(), 17f))
            binding.textMapPlaceholder.visibility = View.GONE
            binding.btnSaveSearchedLocation.visibility = View.VISIBLE
            Toast.makeText(this@CoursePreviewActivity, getString(R.string.location_search_moved, top.name), Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveSearchedLocation() {
        val point = searchedLocation ?: return
        CourseOverrideStore.saveLocation(this, course.id, point)
        binding.btnSaveSearchedLocation.visibility = View.GONE
        reloadCourse()
        Toast.makeText(this, getString(R.string.location_saved_as_course), Toast.LENGTH_SHORT).show()
    }

    // ---------- 티 좌표 등록 ----------

    private fun toggleRegisterMode() {
        isRegisterMode = !isRegisterMode
        if (isRegisterMode) {
            binding.btnRegisterTee.text = getString(R.string.action_register_tee_active)
            binding.btnRegisterTee.backgroundTintList =
                ColorStateList.valueOf(ContextCompat.getColor(this, R.color.accent_orange))
            val holeNumber = course.holes[selectedHoleIndex].number
            Toast.makeText(this, getString(R.string.register_tee_hint, holeNumber), Toast.LENGTH_SHORT).show()
        } else {
            binding.btnRegisterTee.text = getString(R.string.action_register_tee)
            binding.btnRegisterTee.backgroundTintList =
                ColorStateList.valueOf(ContextCompat.getColor(this, R.color.explore_surface))
        }
    }

    private fun registerTeeForSelectedHole(point: GeoPoint) {
        val hole = course.holes[selectedHoleIndex]
        CourseOverrideStore.saveTee(this, course.id, hole.number, point)
        if (course.location == null) {
            CourseOverrideStore.saveLocation(this, course.id, point)
        }
        reloadCourse()
        ActivityLogStore.log(this, getString(R.string.activity_log_tee_registered_format, course.name, hole.number))
        Toast.makeText(this, getString(R.string.register_tee_saved, hole.number), Toast.LENGTH_SHORT).show()

        val nextIndex = selectedHoleIndex + 1
        if (nextIndex < course.holes.size) {
            selectedHoleIndex = nextIndex
            holeAdapter.setSelected(nextIndex)
        }
    }

    private fun confirmClearTee(position: Int) {
        val hole = course.holes[position]
        if (hole.tee == null) return
        AlertDialog.Builder(this)
            .setTitle(R.string.register_tee_clear_confirm_title)
            .setMessage(getString(R.string.register_tee_clear_confirm_message, hole.number))
            .setPositiveButton(android.R.string.ok) { _, _ ->
                CourseOverrideStore.clearTee(this, course.id, hole.number)
                reloadCourse()
                Toast.makeText(this, getString(R.string.register_tee_cleared, hole.number), Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun reloadCourse() {
        course = CourseRepository.loadCourse(this, course.id)
        holeAdapter.updateHoles(course.holes)
        updateSummaryText()
        updateHoleStats(selectedHoleIndex)
        binding.textMapPlaceholder.visibility = if (mapFocusPoint() == null) View.VISIBLE else View.GONE
        googleMap?.let { syncHoleMarkers(it) }
    }
}
