package com.thkim.golfyardage.data

import android.content.Context
import com.thkim.golfyardage.data.model.Course
import com.thkim.golfyardage.data.model.CourseSummary
import com.thkim.golfyardage.data.model.GeoPoint
import com.thkim.golfyardage.data.model.GreenSlope
import com.thkim.golfyardage.data.model.Hole
import org.json.JSONArray
import org.json.JSONObject

/**
 * 골프장 데이터를 assets/courses/ 아래의 json 파일에서 읽어온다.
 * 좌표는 사용자가 위성지도 등을 참고해 직접 JSON 파일에 입력하는 방식이며,
 * 아직 입력되지 않은 좌표 필드는 null(누락)로 두면 앱이 "좌표 미입력"으로 표시한다.
 */
object CourseRepository {

    private const val COURSES_DIR = "courses"
    private const val INDEX_FILE = "$COURSES_DIR/index.json"

    fun loadCourseSummaries(context: Context): List<CourseSummary> {
        val text = context.assets.open(INDEX_FILE).bufferedReader().use { it.readText() }
        val array = JSONArray(text)
        return (0 until array.length()).map { i ->
            val obj = array.getJSONObject(i)
            CourseSummary(
                id = obj.getString("id"),
                name = obj.getString("name"),
                region = obj.optString("region", ""),
                imageFile = obj.optStringOrNull("imageFile"),
                location = parseGeoPoint(obj.optJSONObject("location"))
            )
        }
    }

    fun loadCourse(context: Context, courseId: String): Course {
        val path = "$COURSES_DIR/$courseId.json"
        val text = context.assets.open(path).bufferedReader().use { it.readText() }
        val obj = JSONObject(text)

        val holesArray = obj.getJSONArray("holes")
        val holes = (0 until holesArray.length()).map { i -> parseHole(holesArray.getJSONObject(i)) }

        return Course(
            id = obj.getString("id"),
            name = obj.getString("name"),
            region = obj.optString("region", ""),
            imageFile = obj.optStringOrNull("imageFile"),
            location = parseGeoPoint(obj.optJSONObject("location")),
            holes = holes
        )
    }

    private fun parseHole(obj: JSONObject): Hole {
        val boundaryArray = obj.optJSONArray("greenBoundary")
        val boundary = if (boundaryArray != null) {
            (0 until boundaryArray.length()).mapNotNull { i -> parseGeoPoint(boundaryArray.optJSONObject(i)) }
        } else emptyList()

        val slopeObj = obj.optJSONObject("slope")
        val slope = if (slopeObj != null) {
            GreenSlope(
                direction = slopeObj.optStringOrNull("direction"),
                degreePercent = if (slopeObj.has("degreePercent") && !slopeObj.isNull("degreePercent")) {
                    slopeObj.optDouble("degreePercent")
                } else null
            )
        } else null

        return Hole(
            number = obj.getInt("number"),
            par = obj.optInt("par", 4),
            tee = parseGeoPoint(obj.optJSONObject("tee")),
            greenFront = parseGeoPoint(obj.optJSONObject("greenFront")),
            greenCenter = parseGeoPoint(obj.optJSONObject("greenCenter")),
            greenBack = parseGeoPoint(obj.optJSONObject("greenBack")),
            pin = parseGeoPoint(obj.optJSONObject("pin")),
            greenBoundary = boundary,
            slope = slope,
            strategyTip = obj.optStringOrNull("strategyTip")
        )
    }

    private fun parseGeoPoint(obj: JSONObject?): GeoPoint? {
        if (obj == null) return null
        if (!obj.has("lat") || !obj.has("lng")) return null
        if (obj.isNull("lat") || obj.isNull("lng")) return null
        val elevation = if (obj.has("elevation") && !obj.isNull("elevation")) obj.optDouble("elevation") else null
        return GeoPoint(obj.getDouble("lat"), obj.getDouble("lng"), elevation)
    }

    private fun JSONObject.optStringOrNull(key: String): String? {
        if (!has(key) || isNull(key)) return null
        val v = optString(key)
        return v.ifBlank { null }
    }
}
