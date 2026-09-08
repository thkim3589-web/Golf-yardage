package com.thkim.golfyardage.ui.round

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.kakao.vectormap.KakaoMap
import com.kakao.vectormap.KakaoMapReadyCallback
import com.kakao.vectormap.LatLng
import com.kakao.vectormap.MapLifeCycleCallback
import com.kakao.vectormap.camera.CameraAnimation
import com.kakao.vectormap.camera.CameraUpdateFactory
import com.kakao.vectormap.label.Label
import com.kakao.vectormap.label.LabelOptions
import com.kakao.vectormap.label.LabelStyle
import com.kakao.vectormap.label.LabelStyles
import com.kakao.vectormap.shape.DotPoints
import com.kakao.vectormap.shape.Polygon
import com.kakao.vectormap.shape.PolygonOptions
import com.kakao.vectormap.shape.PolygonStyles
import com.kakao.vectormap.shape.PolygonStylesSet
import com.thkim.golfyardage.R
import com.thkim.golfyardage.data.ClubSettingsRepository
import com.thkim.golfyardage.data.CourseRepository
import com.thkim.golfyardage.data.WeatherRepository
import com.thkim.golfyardage.data.model.Course
import com.thkim.golfyardage.data.model.GeoPoint
import com.thkim.golfyardage.data.model.Hole
import com.thkim.golfyardage.databinding.ActivityMainBinding
import com.thkim.golfyardage.location.GpsFix
import com.thkim.golfyardage.location.LocationTracker
import com.thkim.golfyardage.ui.settings.ClubSettingsActivity
import com.thkim.golfyardage.util.DistanceUnit
import com.thkim.golfyardage.util.DistanceUtils
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_COURSE_ID = "extra_course_id"
    }

    private lateinit var binding: ActivityMainBinding
    private lateinit var course: Course
    private var holeIndex = 0
    private var unit = DistanceUnit.METER

    private lateinit var locationTracker: LocationTracker
    private var kakaoMap: KakaoMap? = null
    private var lastFix: GpsFix? = null
    private var savedShotPoint: GeoPoint? = null
    private var targetPoint: GeoPoint? = null

    private var playerLabel: Label? = null
    private var pinLabel: Label? = null
    private var shotLabel: Label? = null
    private var targetLabel: Label? = null
    private var accuracyPolygon: Polygon? = null

    private var playerStyles: LabelStyles? = null
    private var pinStyles: LabelStyles? = null
    private var shotStyles: LabelStyles? = null
    private var targetStyles: LabelStyles? = null

    private val requestLocationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) startLocationUpdates()
            else Toast.makeText(this, getString(R.string.permission_needed_body), Toast.LENGTH_LONG).show()
        }

    private val distanceOrigin: GeoPoint?
        get() = savedShotPoint ?: lastFix?.point

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val courseId = intent.getStringExtra(EXTRA_COURSE_ID)
        if (courseId == null) {
            finish()
            return
        }
        course = CourseRepository.loadCourse(this, courseId)
        unit = loadUnitPref()
        binding.switchUnit.isChecked = unit == DistanceUnit.YARD
        binding.textCourseName.text = course.name

        locationTracker = LocationTracker(this)

        binding.mapView.start(mapLifeCycleCallback, mapReadyCallback)

        binding.btnBack.setOnClickListener { finish() }
        binding.btnPrevHole.setOnClickListener { changeHole(-1) }
        binding.btnNextHole.setOnClickListener { changeHole(1) }
        binding.switchUnit.setOnCheckedChangeListener { _, checked ->
            unit = if (checked) DistanceUnit.YARD else DistanceUnit.METER
            saveUnitPref(unit)
            updateDistanceTexts()
        }
        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, ClubSettingsActivity::class.java))
        }
        binding.btnSaveShot.setOnClickListener { toggleSaveShot() }
        binding.btnStrategy.setOnClickListener { toggleStrategyPanel() }

        updateHoleHeader()
        updateDistanceTexts()
        loadWeather()
        ensureLocationPermission()
    }

    override fun onResume() {
        super.onResume()
        binding.mapView.resume()
    }

    override fun onPause() {
        binding.mapView.pause()
        super.onPause()
    }

    override fun onDestroy() {
        locationTracker.stop()
        super.onDestroy()
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
        if (savedShotPoint == null) updateDistanceTexts()
    }

    // ---------- 지도 ----------

    private val mapLifeCycleCallback = object : MapLifeCycleCallback() {
        override fun onMapDestroy() {}
        override fun onMapError(error: Exception) {}
    }

    private val mapReadyCallback = object : KakaoMapReadyCallback() {
        override fun onMapReady(map: KakaoMap) {
            kakaoMap = map
            setupLabelStyles(map)
            map.setOnMapClickListener { _, latLng, _, _ -> onMapTapped(latLng) }
            renderPinLabel()
            renderShotLabel()
            renderTargetLabel()
            moveCameraToHole(animate = false)
        }
    }

    private fun setupLabelStyles(map: KakaoMap) {
        val labelManager = map.labelManager ?: return
        playerStyles = labelManager.addLabelStyles(LabelStyles.from(LabelStyle.from(R.drawable.ic_marker_player)))
        pinStyles = labelManager.addLabelStyles(LabelStyles.from(LabelStyle.from(R.drawable.ic_marker_pin)))
        shotStyles = labelManager.addLabelStyles(LabelStyles.from(LabelStyle.from(R.drawable.ic_marker_shot)))
        targetStyles = labelManager.addLabelStyles(LabelStyles.from(LabelStyle.from(R.drawable.ic_marker_target)))
    }

    private fun onMapTapped(latLng: LatLng) {
        targetPoint = GeoPoint(latLng.latitude, latLng.longitude)
        renderTargetLabel()
        updateDistanceTexts()
    }

    private fun moveCameraToHole(animate: Boolean) {
        val map = kakaoMap ?: return
        val hole = currentHole()
        val focus = hole.pin ?: hole.greenCenter ?: hole.tee ?: course.location ?: return
        val update = CameraUpdateFactory.newCenterPosition(focus.toKakaoLatLng())
        if (animate) map.moveCamera(update, CameraAnimation.from(500, true, true)) else map.moveCamera(update)
    }

    private fun renderPlayerLabel(fix: GpsFix) {
        val map = kakaoMap ?: return
        val layer = map.labelManager?.layer ?: return
        val latLng = fix.point.toKakaoLatLng()

        val label = playerLabel
        if (label == null) {
            playerLabel = layer.addLabel(LabelOptions.from(latLng).setStyles(playerStyles))
        } else {
            label.moveTo(latLng)
        }

        map.shapeManager?.layer?.let { shapeLayer ->
            accuracyPolygon?.let { shapeLayer.remove(it) }
            val options = PolygonOptions.from(DotPoints.fromCircle(latLng, fix.accuracyMeters))
                .setStylesSet(PolygonStylesSet.from(PolygonStyles.from(Color.parseColor("#334C8C4A"))))
            accuracyPolygon = shapeLayer.addPolygon(options)
        }
    }

    private fun renderPinLabel() {
        val map = kakaoMap ?: return
        val layer = map.labelManager?.layer ?: return
        val pin = currentHole().pin
        if (pin == null) {
            pinLabel?.hide()
            return
        }
        val latLng = pin.toKakaoLatLng()
        val label = pinLabel
        if (label == null) pinLabel = layer.addLabel(LabelOptions.from(latLng).setStyles(pinStyles))
        else {
            label.show()
            label.moveTo(latLng)
        }
    }

    private fun renderShotLabel() {
        val map = kakaoMap ?: return
        val layer = map.labelManager?.layer ?: return
        val point = savedShotPoint
        if (point == null) {
            shotLabel?.hide()
            return
        }
        val latLng = point.toKakaoLatLng()
        val label = shotLabel
        if (label == null) shotLabel = layer.addLabel(LabelOptions.from(latLng).setStyles(shotStyles))
        else {
            label.show()
            label.moveTo(latLng)
        }
    }

    private fun renderTargetLabel() {
        val map = kakaoMap ?: return
        val layer = map.labelManager?.layer ?: return
        val point = targetPoint
        if (point == null) {
            targetLabel?.hide()
            return
        }
        val latLng = point.toKakaoLatLng()
        val label = targetLabel
        if (label == null) targetLabel = layer.addLabel(LabelOptions.from(latLng).setStyles(targetStyles))
        else {
            label.show()
            label.moveTo(latLng)
        }
    }

    // ---------- 홀 ----------

    private fun currentHole(): Hole = course.holes[holeIndex]

    private fun changeHole(delta: Int) {
        val newIndex = (holeIndex + delta).coerceIn(0, course.holes.size - 1)
        if (newIndex == holeIndex) return
        holeIndex = newIndex
        savedShotPoint = null
        targetPoint = null
        binding.textStrategyTip.visibility = View.GONE

        updateHoleHeader()
        renderPinLabel()
        renderShotLabel()
        renderTargetLabel()
        moveCameraToHole(animate = true)
        updateSaveShotButtonText()
        updateDistanceTexts()
    }

    private fun updateHoleHeader() {
        val hole = currentHole()
        binding.textHoleInfo.text =
            "${getString(R.string.hole_format, hole.number)} · ${getString(R.string.par_format, hole.par)}"
        binding.textStrategyTip.text = hole.strategyTip?.takeIf { it.isNotBlank() }
            ?: getString(R.string.strategy_tip_empty)
    }

    // ---------- 샷 위치 저장 ----------

    private fun toggleSaveShot() {
        if (savedShotPoint == null) {
            val fix = lastFix
            if (fix == null) {
                Toast.makeText(this, getString(R.string.gps_waiting), Toast.LENGTH_SHORT).show()
                return
            }
            savedShotPoint = fix.point.copy(elevation = fix.altitudeMeters)
        } else {
            savedShotPoint = null
        }
        renderShotLabel()
        updateSaveShotButtonText()
        updateDistanceTexts()
    }

    private fun updateSaveShotButtonText() {
        binding.btnSaveShot.text =
            getString(if (savedShotPoint != null) R.string.action_clear_shot else R.string.action_save_shot)
    }

    private fun toggleStrategyPanel() {
        binding.textStrategyTip.visibility =
            if (binding.textStrategyTip.visibility == View.VISIBLE) View.GONE else View.VISIBLE
    }

    // ---------- 거리 계산/표시 ----------

    private fun updateDistanceTexts() {
        val hole = currentHole()
        val origin = distanceOrigin

        binding.textDistanceBasis.text = getString(
            if (savedShotPoint != null) R.string.shot_saved_from else R.string.live_gps_basis
        )

        showDistance(binding.textFront, origin, hole.greenFront)
        showDistance(binding.textCenter, origin, hole.greenCenter)
        showDistance(binding.textBack, origin, hole.greenBack)

        val referenceTarget = hole.pin ?: hole.greenCenter
        updatePlaysLike(origin, referenceTarget)
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

    private fun showDistance(view: TextView, origin: GeoPoint?, point: GeoPoint?) {
        if (origin == null || point == null) {
            view.text = getString(R.string.missing_coordinates)
            return
        }
        val meters = DistanceUtils.distanceMeters(origin, point)
        view.text = DistanceUtils.format(meters, unit)
    }

    private fun updatePlaysLike(origin: GeoPoint?, target: GeoPoint?) {
        val originElevation = origin?.elevation
        val targetElevation = target?.elevation
        if (origin == null || target == null || originElevation == null || targetElevation == null) {
            binding.textPlaysLike.visibility = View.GONE
            return
        }
        val horizontal = DistanceUtils.distanceMeters(origin, target)
        val playsLike = DistanceUtils.playsLikeMeters(horizontal, targetElevation - originElevation)
        binding.textPlaysLike.text = getString(
            R.string.plays_like_format,
            DistanceUtils.format(playsLike, unit),
            unitSuffix()
        )
        binding.textPlaysLike.visibility = View.VISIBLE
    }

    private fun updateRecommendedClub(origin: GeoPoint?, target: GeoPoint?) {
        if (origin == null || target == null) {
            binding.textRecommendedClub.text = ""
            return
        }
        val meters = DistanceUtils.distanceMeters(origin, target)
        val club = ClubSettingsRepository.recommendClub(this, meters)
        binding.textRecommendedClub.text = club?.let { getString(R.string.recommended_club_format, it.clubName) }
            ?: getString(R.string.recommended_club_none)
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
