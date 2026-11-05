package com.thkim.golfyardage.data

import android.content.Context
import com.thkim.golfyardage.data.model.GeoPoint

/**
 * 앱 안에서 직접 등록한 좌표(현재는 티박스 기준)를 기기 로컬에 저장한다.
 * assets의 코스 JSON은 앱 번들에 포함된 읽기 전용 데이터라 런타임에 고칠 수 없으므로,
 * 여기 저장된 값을 CourseRepository가 불러올 때 덮어씌워 합친다.
 */
object CourseOverrideStore {

    private const val PREFS_NAME = "course_overrides"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun encode(point: GeoPoint): String = "${point.lat},${point.lng}"

    private fun decode(value: String?): GeoPoint? {
        if (value == null) return null
        val parts = value.split(",")
        if (parts.size != 2) return null
        val lat = parts[0].toDoubleOrNull() ?: return null
        val lng = parts[1].toDoubleOrNull() ?: return null
        return GeoPoint(lat, lng)
    }

    fun saveTee(context: Context, courseId: String, holeNumber: Int, point: GeoPoint) {
        prefs(context).edit().putString(teeKey(courseId, holeNumber), encode(point)).apply()
    }

    fun loadTee(context: Context, courseId: String, holeNumber: Int): GeoPoint? =
        decode(prefs(context).getString(teeKey(courseId, holeNumber), null))

    fun clearTee(context: Context, courseId: String, holeNumber: Int) {
        prefs(context).edit().remove(teeKey(courseId, holeNumber)).apply()
    }

    fun savePin(context: Context, courseId: String, holeNumber: Int, point: GeoPoint) {
        prefs(context).edit().putString(pinKey(courseId, holeNumber), encode(point)).apply()
    }

    fun loadPin(context: Context, courseId: String, holeNumber: Int): GeoPoint? =
        decode(prefs(context).getString(pinKey(courseId, holeNumber), null))

    fun saveLocation(context: Context, courseId: String, point: GeoPoint) {
        prefs(context).edit().putString(locationKey(courseId), encode(point)).apply()
    }

    fun loadLocation(context: Context, courseId: String): GeoPoint? =
        decode(prefs(context).getString(locationKey(courseId), null))

    private fun teeKey(courseId: String, holeNumber: Int) = "tee_${courseId}_$holeNumber"
    private fun pinKey(courseId: String, holeNumber: Int) = "pin_${courseId}_$holeNumber"
    private fun locationKey(courseId: String) = "location_$courseId"
}
