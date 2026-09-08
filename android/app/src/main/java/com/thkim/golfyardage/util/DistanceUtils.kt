package com.thkim.golfyardage.util

import com.thkim.golfyardage.data.model.GeoPoint
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

enum class DistanceUnit { METER, YARD }

object DistanceUtils {

    private const val EARTH_RADIUS_M = 6371000.0
    private const val METERS_TO_YARDS = 1.09361

    /** 두 좌표 사이 거리(m), Haversine 공식 */
    fun distanceMeters(from: GeoPoint, to: GeoPoint): Double {
        val lat1 = Math.toRadians(from.lat)
        val lat2 = Math.toRadians(to.lat)
        val dLat = Math.toRadians(to.lat - from.lat)
        val dLng = Math.toRadians(to.lng - from.lng)

        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(lat1) * cos(lat2) * sin(dLng / 2) * sin(dLng / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return EARTH_RADIUS_M * c
    }

    fun toDisplayUnit(meters: Double, unit: DistanceUnit): Double =
        if (unit == DistanceUnit.YARD) meters * METERS_TO_YARDS else meters

    fun format(meters: Double, unit: DistanceUnit): String {
        val value = toDisplayUnit(meters, unit)
        return value.toInt().toString()
    }

    /**
     * 경사 반영(plays-like) 거리 근사치.
     * 오르막이면 실거리보다 더 멀게, 내리막이면 더 가깝게 느껴진다는 경험칙(1m 고도차 ≈ 1m 가감)을 적용한다.
     * GPS 고도는 오차가 크므로(±10m 내외) 참고용 수치임을 UI에서 함께 안내해야 한다.
     */
    fun playsLikeMeters(horizontalMeters: Double, elevationDiffMeters: Double): Double =
        (horizontalMeters + elevationDiffMeters).coerceAtLeast(0.0)
}
