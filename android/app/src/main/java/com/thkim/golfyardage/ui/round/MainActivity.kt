package com.thkim.golfyardage.ui.round

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.Gravity
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.Circle
import com.google.android.gms.maps.model.CircleOptions
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.thkim.golfyardage.R
import com.thkim.golfyardage.data.ClubSettingsRepository
import com.thkim.golfyardage.data.CourseOverrideStore
import com.thkim.golfyardage.data.CourseRepository
import com.thkim.golfyardage.data.RoundHistoryStore
import com.thkim.golfyardage.data.WeatherRepository
import com.thkim.golfyardage.data.model.Course
import com.thkim.golfyardage.data.model.GeoPoint
import com.thkim.golfyardage.data.model.Hole
import com.thkim.golfyardage.data.model.HoleScore
import com.thkim.golfyardage.databinding.ActivityMainBinding
import com.thkim.golfyardage.location.GpsFix
import com.thkim.golfyardage.location.LocationTracker
import com.thkim.golfyardage.ui.management.CourseManagementActivity
import com.thkim.golfyardage.ui.settings.ClubSettingsActivity
import com.thkim.golfyardage.util.DistanceUnit
import com.thkim.golfyardage.util.DistanceUtils
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_COURSE_ID = "extra_course_id"
        const val EXTRA_START_HOLE_INDEX = "extra_start_hole_index"
    }

    private enum class GreenTarget { PIN, FRONT, CENTER, BACK }
    private enum class DistanceMode { REMAINING, SHOT }

    private lateinit var binding: ActivityMainBinding
    private lateinit var course: Course
    private var holeIndex = 0
    private var unit = DistanceUnit.METER
    private var greenTarget = GreenTarget.PIN
    private var distanceMode = DistanceMode.REMAINING
    private var isTouchLocked = false
    private var isSatelliteMap = true

    private lateinit var locationTracker: LocationTracker
    private var googleMap: GoogleMap? = null
    private var lastFix: GpsFix? = null

    /** 이번 홀에서 기록한 샷 위치들(순서대로). 홀을 바꾸면 초기화된다. */
    private val shots = mutableListOf<GeoPoint>()
    private var targetPoint: GeoPoint? = null

    /** 홀 번호 -> 타수. 0이면 아직 입력 안 한 것으로 취급해 저장하지 않는다. */
    private val holeScores = mutableMapOf<Int, Int>()

    private var playerMarker: Marker? = null
    private var pinMarker: Marker? = null
    private val shotMarkers = mutableListOf<Marker>()
    private var targetMarker: Marker? = null
    private var accuracyCircle: Circle? = null

    /** 추천 클럽의 평균 비거리를 현재 위치 기준 원으로 보여주는 오버레이 */
    private var clubRangeCircle: Circle? = null

    private val requestLocationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) startLocationUpdates()
            else Toast.makeText(this, getString(R.string.permission_needed_body), Toast.LENGTH_LONG).show()
        }

    private val distanceOrigin: GeoPoint?
        get() = shots.lastOrNull() ?: lastFix?.point

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_GolfYardage_Round)
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val courseId = intent.getStringExtra(EXTRA_COURSE_ID)
        if (courseId == null) {
            finish()
            return
        }
        course = CourseRepository.loadCourse(this, courseId)
        holeIndex = intent.getIntExtra(EXTRA_START_HOLE_INDEX, 0).coerceIn(0, course.holes.size - 1)
        unit = loadUnitPref()

        locationTracker = LocationTracker(this)

        binding.mapView.onCreate(savedInstanceState)
        binding.mapView.getMapAsync { map -> onGoogleMapReady(map) }

        binding.badgeCourseHole.setOnClickListener { showHolePicker() }
        binding.btnGps.setOnClickListener { recenterOnMe() }
        binding.btnMapType.setOnClickListener { toggleMapType() }
        isSatelliteMap = loadMapTypePref()
        binding.btnMapType.text = getString(if (isSatelliteMap) R.string.map_type_satellite else R.string.map_type_normal)
        binding.btnScore.setOnClickListener { showScoreDialog() }
        binding.btnMemo.setOnClickListener {
            CourseManagementActivity.start(this, course.id, currentHole().number)
        }
        binding.btnHelp.setOnClickListener { showHelpDialog() }
        binding.btnStrategy.setOnClickListener { toggleStrategyPanel() }
        binding.btnTarget.setOnClickListener { clearTargetPoint() }
        binding.btnPin.setOnClickListener { savePinFromCurrentLocation() }
        binding.btnGreenSelect.setOnClickListener { cycleGreenTarget() }
        binding.btnTouchLock.setOnClickListener { toggleTouchLock() }
        binding.btnShotDistance.setOnClickListener { selectDistanceMode(DistanceMode.SHOT) }
        binding.btnRemainingDistance.setOnClickListener { selectDistanceMode(DistanceMode.REMAINING) }
        binding.btnMore.setOnClickListener { showOverflowMenu(it) }

        updateHoleHeader()
        updateGreenSelectLabel()
        selectDistanceMode(DistanceMode.REMAINING)
        updateDistanceTexts()
        loadWeather()
        ensureLocationPermission()
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
        saveRoundIfAny()
        locationTracker.stop()
        binding.mapView.onDestroy()
        super.onDestroy()
    }

    // ---------- 종료 ----------

    private fun confirmEndRound() {
        AlertDialog.Builder(this)
            .setTitle(R.string.end_round_confirm_title)
            .setMessage(R.string.end_round_confirm_message)
            .setPositiveButton(R.string.action_end_round) { _, _ -> finish() }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showOverflowMenu(anchor: View) {
        val popup = PopupMenu(this, anchor, Gravity.END)
        val unitLabel = getString(if (unit == DistanceUnit.YARD) R.string.unit_yard else R.string.unit_meter).uppercase()
        popup.menu.add(0, 1, 0, getString(R.string.round_overflow_settings))
        popup.menu.add(0, 2, 1, getString(R.string.round_overflow_unit_format, unitLabel))
        popup.menu.add(0, 3, 2, getString(R.string.round_overflow_end))
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> startActivity(Intent(this, ClubSettingsActivity::class.java))
                2 -> {
                    unit = if (unit == DistanceUnit.YARD) DistanceUnit.METER else DistanceUnit.YARD
                    saveUnitPref(unit)
                    updateDistanceTexts()
                }
                3 -> confirmEndRound()
            }
            true
        }
        popup.show()
    }

    // ---------- 스코어 ----------

    private fun adjustScore(delta: Int) {
        val hole = currentHole()
        val current = holeScores[hole.number] ?: 0
        holeScores[hole.number] = (current + delta).coerceIn(0, 15)
    }

    private fun saveRoundIfAny() {
        val scores = holeScores.filter { it.value > 0 }.map { (number, strokes) -> HoleScore(number, strokes) }
        if (scores.isEmpty()) return
        RoundHistoryStore.save(this, course.id, course.name, scores)
    }

    private fun showScoreDialog() {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_score_entry, null)
        val textTitle = view.findViewById<TextView>(R.id.text_dialog_hole_title)
        val btnMinus = view.findViewById<TextView>(R.id.dialog_btn_score_minus)
        val textScore = view.findViewById<TextView>(R.id.dialog_text_score)
        val btnPlus = view.findViewById<TextView>(R.id.dialog_btn_score_plus)
        val btnSaveShot = view.findViewById<com.google.android.material.button.MaterialButton>(R.id.dialog_btn_save_shot)
        val textBasis = view.findViewById<TextView>(R.id.dialog_text_distance_basis)

        val hole = currentHole()
        textTitle.text = "${getString(R.string.hole_format, hole.number)} · ${getString(R.string.par_format, hole.par)}"

        fun refreshScore() {
            textScore.text = (holeScores[hole.number] ?: 0).toString()
        }
        fun refreshBasis() {
            val basis = if (shots.isNotEmpty()) getString(R.string.shot_saved_from_format, shots.size)
            else getString(R.string.live_gps_basis)
            textBasis.text = getString(R.string.round_score_dialog_hint_format, basis)
            btnSaveShot.text = getString(R.string.action_record_shot_format, shots.size + 1)
        }
        refreshScore()
        refreshBasis()

        btnMinus.setOnClickListener { adjustScore(-1); refreshScore() }
        btnPlus.setOnClickListener { adjustScore(1); refreshScore() }
        btnSaveShot.setOnClickListener { recordShot(); refreshBasis() }
        btnSaveShot.setOnLongClickListener { undoLastShot(); refreshBasis(); true }

        AlertDialog.Builder(this).setView(view).show()
    }

    private fun showHelpDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.round_menu_help)
            .setMessage(R.string.round_help_message)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    // ---------- 권한 / 위치 ----------

    private fun ensureLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            startLocationUpdates()
        } else {
            requestLocationPermission.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    private fun startLocationUpdates() {
        locationTracker.start { fix -> onGpsFix(fix) }
    }

    private fun onGpsFix(fix: GpsFix) {
        lastFix = fix
        binding.textGpsAccuracy.text = getString(R.string.gps_accuracy_format, fix.accuracyMeters.toInt())
        renderPlayerLabel(fix)
        if (shots.isEmpty()) updateDistanceTexts()
    }

    private fun recenterOnMe() {
        val point = lastFix?.point
        if (point == null) {
            Toast.makeText(this, getString(R.string.round_pin_needs_gps), Toast.LENGTH_SHORT).show()
            return
        }
        googleMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(point.toLatLng(), 18f))
    }

    // ---------- 지도 ----------

    private fun onGoogleMapReady(map: GoogleMap) {
        googleMap = map
        isSatelliteMap = loadMapTypePref()
        applyMapType()
        map.setOnMapClickListener { latLng -> if (!isTouchLocked) onMapTapped(latLng) }
        renderPinLabel()
        renderTargetLabel()
        moveCameraToHole(animate = false)
    }

    private fun applyMapType() {
        googleMap?.mapType = if (isSatelliteMap) GoogleMap.MAP_TYPE_HYBRID else GoogleMap.MAP_TYPE_NORMAL
        binding.btnMapType.text = getString(if (isSatelliteMap) R.string.map_type_satellite else R.string.map_type_normal)
    }

    private fun toggleMapType() {
        isSatelliteMap = !isSatelliteMap
        saveMapTypePref(isSatelliteMap)
        applyMapType()
    }

    private fun loadMapTypePref(): Boolean =
        getSharedPreferences("settings", MODE_PRIVATE).getBoolean("map_satellite", true)

    private fun saveMapTypePref(value: Boolean) {
        getSharedPreferences("settings", MODE_PRIVATE).edit().putBoolean("map_satellite", value).apply()
    }

    private fun onMapTapped(latLng: LatLng) {
        targetPoint = GeoPoint(latLng.latitude, latLng.longitude)
        renderTargetLabel()
        updateDistanceTexts()
    }

    private fun clearTargetPoint() {
        targetPoint = null
        renderTargetLabel()
        updateDistanceTexts()
        Toast.makeText(this, getString(R.string.round_target_cleared), Toast.LENGTH_SHORT).show()
    }

    private fun toggleTouchLock() {
        isTouchLocked = !isTouchLocked
        binding.btnTouchLock.text = getString(if (isTouchLocked) R.string.round_menu_lock_on else R.string.round_menu_lock)
        Toast.makeText(
            this,
            getString(if (isTouchLocked) R.string.round_touch_locked else R.string.round_touch_unlocked),
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun savePinFromCurrentLocation() {
        val point = lastFix?.point
        if (point == null) {
            Toast.makeText(this, getString(R.string.round_pin_needs_gps), Toast.LENGTH_SHORT).show()
            return
        }
        val hole = currentHole()
        CourseOverrideStore.savePin(this, course.id, hole.number, point)
        course = CourseRepository.loadCourse(this, course.id)
        renderPinLabel()
        updateDistanceTexts()
        Toast.makeText(this, getString(R.string.round_pin_saved, hole.number), Toast.LENGTH_SHORT).show()
    }

    private fun moveCameraToHole(animate: Boolean) {
        val map = googleMap ?: return
        val hole = currentHole()
        val focus = hole.pin ?: hole.greenCenter ?: hole.tee ?: course.location ?: return
        val update = CameraUpdateFactory.newLatLngZoom(focus.toLatLng(), 17f)
        if (animate) map.animateCamera(update) else map.moveCamera(update)
    }

    private fun renderPlayerLabel(fix: GpsFix) {
        val map = googleMap ?: return
        val latLng = fix.point.toLatLng()

        val marker = playerMarker
        if (marker == null) {
            playerMarker = map.addMarker(
                MarkerOptions().position(latLng)
                    .icon(com.thkim.golfyardage.util.MapIcons.from(this, R.drawable.ic_marker_player))
                    .anchor(0.5f, 0.5f)
            )
        } else {
            marker.position = latLng
        }

        accuracyCircle?.remove()
        accuracyCircle = map.addCircle(
            CircleOptions().center(latLng).radius(fix.accuracyMeters.toDouble())
                .fillColor(Color.parseColor("#334C8C4A"))
                .strokeColor(Color.parseColor("#4C8C4A"))
                .strokeWidth(1f)
        )
    }

    private fun renderPinLabel() {
        val map = googleMap ?: return
        val pin = currentHole().pin
        if (pin == null) {
            pinMarker?.isVisible = false
            return
        }
        val latLng = pin.toLatLng()
        val marker = pinMarker
        if (marker == null) {
            pinMarker = map.addMarker(
                MarkerOptions().position(latLng)
                    .icon(com.thkim.golfyardage.util.MapIcons.from(this, R.drawable.ic_marker_pin))
                    .anchor(0.5f, 1f)
            )
        } else {
            marker.isVisible = true
            marker.position = latLng
        }
    }

    private fun addShotLabel(point: GeoPoint) {
        val map = googleMap ?: return
        val marker = map.addMarker(
            MarkerOptions().position(point.toLatLng())
                .icon(com.thkim.golfyardage.util.MapIcons.from(this, R.drawable.ic_marker_shot))
                .anchor(0.5f, 0.5f)
        )
        if (marker != null) shotMarkers.add(marker)
    }

    private fun removeLastShotLabel() {
        shotMarkers.removeLastOrNull()?.remove()
    }

    private fun clearShotLabels() {
        shotMarkers.forEach { it.remove() }
        shotMarkers.clear()
    }

    private fun renderTargetLabel() {
        val map = googleMap ?: return
        val point = targetPoint
        if (point == null) {
            targetMarker?.isVisible = false
            return
        }
        val latLng = point.toLatLng()
        val marker = targetMarker
        if (marker == null) {
            targetMarker = map.addMarker(
                MarkerOptions().position(latLng)
                    .icon(com.thkim.golfyardage.util.MapIcons.from(this, R.drawable.ic_marker_target))
                    .anchor(0.5f, 0.5f)
            )
        } else {
            marker.isVisible = true
            marker.position = latLng
        }
    }

    // ---------- 홀 ----------

    private fun currentHole(): Hole = course.holes[holeIndex]

    private fun showHolePicker() {
        val labels = course.holes.map {
            "${getString(R.string.hole_format, it.number)} · ${getString(R.string.par_format, it.par)}"
        }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(R.string.round_hole_picker_title)
            .setSingleChoiceItems(labels, holeIndex) { dialog, which ->
                selectHole(which)
                dialog.dismiss()
            }
            .show()
    }

    private fun selectHole(newIndex: Int) {
        if (newIndex == holeIndex || newIndex !in course.holes.indices) return
        holeIndex = newIndex
        shots.clear()
        clearShotLabels()
        targetPoint = null
        binding.textStrategyTip.visibility = View.GONE

        updateHoleHeader()
        renderPinLabel()
        renderTargetLabel()
        moveCameraToHole(animate = true)
        updateDistanceTexts()
    }

    private fun updateHoleHeader() {
        val hole = currentHole()
        binding.textHoleInfo.text = getString(R.string.round_badge_format, course.name, hole.number)
        binding.textStrategyTip.text = hole.strategyTip?.takeIf { it.isNotBlank() }
            ?: getString(R.string.strategy_tip_empty)
    }

    private fun updateGreenSelectLabel() {
        val label = when (greenTarget) {
            GreenTarget.PIN -> getString(R.string.round_green_pin)
            GreenTarget.FRONT -> getString(R.string.label_front)
            GreenTarget.CENTER -> getString(R.string.label_center)
            GreenTarget.BACK -> getString(R.string.label_back)
        }
        binding.btnGreenSelect.text = getString(R.string.round_green_select_format, label)
    }

    private fun cycleGreenTarget() {
        greenTarget = when (greenTarget) {
            GreenTarget.PIN -> GreenTarget.FRONT
            GreenTarget.FRONT -> GreenTarget.CENTER
            GreenTarget.CENTER -> GreenTarget.BACK
            GreenTarget.BACK -> GreenTarget.PIN
        }
        updateGreenSelectLabel()
        updateDistanceTexts()
    }

    private fun referenceTargetPoint(hole: Hole): GeoPoint? = when (greenTarget) {
        GreenTarget.PIN -> hole.pin ?: hole.greenCenter
        GreenTarget.FRONT -> hole.greenFront ?: hole.greenCenter
        GreenTarget.CENTER -> hole.greenCenter
        GreenTarget.BACK -> hole.greenBack ?: hole.greenCenter
    }

    private fun selectDistanceMode(mode: DistanceMode) {
        distanceMode = mode
        val activeColor = ContextCompat.getColor(this, R.color.accent_orange)
        val inactiveColor = ContextCompat.getColor(this, R.color.explore_surface)
        binding.btnShotDistance.backgroundTintList =
            android.content.res.ColorStateList.valueOf(if (mode == DistanceMode.SHOT) activeColor else inactiveColor)
        binding.btnRemainingDistance.backgroundTintList =
            android.content.res.ColorStateList.valueOf(if (mode == DistanceMode.REMAINING) activeColor else inactiveColor)
        updateDistanceTexts()
    }

    // ---------- 샷 트래킹 ----------

    /** 현재 GPS 위치를 새 샷 지점으로 기록한다(길게 누르면 마지막 기록 취소). */
    private fun recordShot() {
        val fix = lastFix
        if (fix == null) {
            Toast.makeText(this, getString(R.string.gps_waiting), Toast.LENGTH_SHORT).show()
            return
        }
        val point = fix.point.copy(elevation = fix.altitudeMeters)
        shots.add(point)
        addShotLabel(point)
        updateDistanceTexts()
    }

    private fun undoLastShot() {
        if (shots.isEmpty()) return
        shots.removeAt(shots.lastIndex)
        removeLastShotLabel()
        updateDistanceTexts()
        Toast.makeText(this, getString(R.string.shot_undo_message), Toast.LENGTH_SHORT).show()
    }

    private fun toggleStrategyPanel() {
        binding.textStrategyTip.visibility =
            if (binding.textStrategyTip.visibility == View.VISIBLE) View.GONE else View.VISIBLE
    }

    // ---------- 거리 계산/표시 ----------

    private fun updateDistanceTexts() {
        val hole = currentHole()
        val origin = distanceOrigin
        val referenceTarget = referenceTargetPoint(hole)

        when (distanceMode) {
            DistanceMode.REMAINING -> updateCorrectedDistanceRemaining(origin, referenceTarget, hole)
            DistanceMode.SHOT -> updateCorrectedDistanceShot(hole)
        }

        updateRecommendedClub(origin, referenceTarget)

        val target = targetPoint
        if (origin != null && target != null) {
            val meters = DistanceUtils.distanceMeters(origin, target)
            binding.textTargetDistance.text = getString(
                R.string.target_distance_format,
                DistanceUtils.format(meters, unit),
                unitSuffix()
            )
            binding.textTargetDistance.visibility = View.VISIBLE
        } else {
            binding.textTargetDistance.visibility = View.GONE
        }
    }

    private fun updateCorrectedDistanceRemaining(origin: GeoPoint?, target: GeoPoint?, hole: Hole) {
        if (origin == null || target == null) {
            binding.textCorrectedDistance.text = getString(R.string.stat_value_none)
            binding.textDistanceDetail.text = getString(R.string.par_format, hole.par)
            return
        }
        val horizontal = DistanceUtils.distanceMeters(origin, target)
        val originElevation = origin.elevation
        val targetElevation = target.elevation
        val corrected = if (originElevation != null && targetElevation != null) {
            DistanceUtils.playsLikeMeters(horizontal, targetElevation - originElevation)
        } else {
            horizontal
        }
        binding.textCorrectedDistance.text = "${DistanceUtils.format(corrected, unit)}${unitSuffix()}"

        val detail = StringBuilder()
        detail.append(getString(R.string.round_detail_horizontal_format, DistanceUtils.format(horizontal, unit), unitSuffix()))
        if (originElevation != null && targetElevation != null) {
            val diff = targetElevation - originElevation
            val sign = if (diff >= 0) "+" else ""
            detail.append(" | ").append(getString(R.string.round_detail_elevation_format, sign, "%.1f".format(diff)))
        }
        detail.append(" | ").append(getString(R.string.par_format, hole.par))
        binding.textDistanceDetail.text = detail.toString()
    }

    private fun updateCorrectedDistanceShot(hole: Hole) {
        if (shots.size < 2) {
            binding.textCorrectedDistance.text = getString(R.string.stat_value_none)
            binding.textDistanceDetail.text = getString(R.string.round_detail_shot_hint)
            return
        }
        val from = shots[shots.size - 2]
        val to = shots[shots.size - 1]
        val meters = DistanceUtils.distanceMeters(from, to)
        binding.textCorrectedDistance.text = "${DistanceUtils.format(meters, unit)}${unitSuffix()}"
        binding.textDistanceDetail.text = getString(R.string.round_detail_shot_format, shots.size) +
            " | " + getString(R.string.par_format, hole.par)
    }

    private fun updateRecommendedClub(origin: GeoPoint?, target: GeoPoint?) {
        if (origin == null || target == null) {
            binding.textRecommendedClub.text = ""
            renderClubRangeCircle(null, null)
            return
        }
        val meters = DistanceUtils.distanceMeters(origin, target)
        val club = ClubSettingsRepository.recommendClub(this, meters)
        binding.textRecommendedClub.text = club?.let { getString(R.string.recommended_club_format, it.clubName) }
            ?: getString(R.string.recommended_club_none)
        renderClubRangeCircle(origin, club?.averageMeters)
    }

    /** 추천 클럽의 평균 비거리를 현재 위치 기준 반경 원으로 지도에 표시한다. */
    private fun renderClubRangeCircle(origin: GeoPoint?, radiusMeters: Double?) {
        clubRangeCircle?.remove()
        clubRangeCircle = null

        val map = googleMap
        if (origin == null || radiusMeters == null || map == null) return

        clubRangeCircle = map.addCircle(
            CircleOptions().center(origin.toLatLng()).radius(radiusMeters)
                .fillColor(Color.parseColor("#1AFF6B35"))
                .strokeColor(Color.parseColor("#FF6B35"))
                .strokeWidth(1f)
        )
    }

    private fun unitSuffix(): String =
        getString(if (unit == DistanceUnit.YARD) R.string.unit_yard else R.string.unit_meter)

    // ---------- 날씨 ----------

    private fun loadWeather() {
        val location = course.location
        if (location == null) {
            binding.textWeather.visibility = View.GONE
            return
        }
        binding.textWeather.text = getString(R.string.weather_loading)
        lifecycleScope.launch {
            val weather = WeatherRepository.fetchCurrentWeather(location)
            binding.textWeather.text = weather?.let {
                getString(R.string.weather_format, it.temperatureC.toInt(), it.conditionText, it.windSpeedMs)
            } ?: getString(R.string.weather_unavailable)
        }
    }

    // ---------- 설정 저장 ----------

    private fun loadUnitPref(): DistanceUnit {
        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        return if (prefs.getBoolean("unit_yard", false)) DistanceUnit.YARD else DistanceUnit.METER
    }

    private fun saveUnitPref(unit: DistanceUnit) {
        getSharedPreferences("settings", MODE_PRIVATE).edit()
            .putBoolean("unit_yard", unit == DistanceUnit.YARD)
            .apply()
    }
}
