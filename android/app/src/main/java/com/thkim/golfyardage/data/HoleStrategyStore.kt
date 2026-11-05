package com.thkim.golfyardage.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class StrategyPoint(val x: Float, val y: Float)
data class StrategyStroke(val points: List<StrategyPoint>)

/** 홀 전략 캔버스에 그린 공략선/위험 표시. 좌표는 캔버스 크기에 대한 0~1 비율로 저장한다. */
data class StrategyDrawing(
    val strokes: List<StrategyStroke> = emptyList(),
    val hazards: List<StrategyPoint> = emptyList()
) {
    val isEmpty: Boolean get() = strokes.isEmpty() && hazards.isEmpty()
}

/** 코스별·홀별 전략 캔버스 드로잉을 기기에 저장한다. */
object HoleStrategyStore {
    private const val PREFS = "hole_strategy"

    private fun key(courseId: String, holeNumber: Int) = "${courseId}_$holeNumber"

    fun load(context: Context, courseId: String, holeNumber: Int): StrategyDrawing {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val json = prefs.getString(key(courseId, holeNumber), null) ?: return StrategyDrawing()
        return try {
            val obj = JSONObject(json)
            val strokesArray = obj.optJSONArray("strokes") ?: JSONArray()
            val strokes = (0 until strokesArray.length()).map { i ->
                val pointsArray = strokesArray.getJSONArray(i)
                StrategyStroke(
                    (0 until pointsArray.length()).map { j ->
                        val p = pointsArray.getJSONArray(j)
                        StrategyPoint(p.getDouble(0).toFloat(), p.getDouble(1).toFloat())
                    }
                )
            }
            val hazardsArray = obj.optJSONArray("hazards") ?: JSONArray()
            val hazards = (0 until hazardsArray.length()).map { i ->
                val p = hazardsArray.getJSONArray(i)
                StrategyPoint(p.getDouble(0).toFloat(), p.getDouble(1).toFloat())
            }
            StrategyDrawing(strokes, hazards)
        } catch (e: Exception) {
            StrategyDrawing()
        }
    }

    fun save(context: Context, courseId: String, holeNumber: Int, drawing: StrategyDrawing) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (drawing.isEmpty) {
            prefs.edit().remove(key(courseId, holeNumber)).apply()
            return
        }
        val strokesArray = JSONArray()
        drawing.strokes.forEach { stroke ->
            val pointsArray = JSONArray()
            stroke.points.forEach { p -> pointsArray.put(JSONArray().put(p.x.toDouble()).put(p.y.toDouble())) }
            strokesArray.put(pointsArray)
        }
        val hazardsArray = JSONArray()
        drawing.hazards.forEach { p -> hazardsArray.put(JSONArray().put(p.x.toDouble()).put(p.y.toDouble())) }
        val obj = JSONObject().put("strokes", strokesArray).put("hazards", hazardsArray)
        prefs.edit().putString(key(courseId, holeNumber), obj.toString()).apply()
    }
}
