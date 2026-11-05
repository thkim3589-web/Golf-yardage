package com.thkim.golfyardage.data

import android.content.Context
import com.thkim.golfyardage.data.model.HoleScore
import com.thkim.golfyardage.data.model.RoundRecord
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** 완료(또는 진행 중 저장)된 라운드의 홀별 타수 기록을 기기 로컬에 보관한다. */
object RoundHistoryStore {

    private const val PREFS_NAME = "round_history"
    private const val KEY_RECORDS = "records"
    private const val MAX_RECORDS = 100

    fun loadAll(context: Context): List<RoundRecord> {
        val text = prefs(context).getString(KEY_RECORDS, null) ?: return emptyList()
        val array = JSONArray(text)
        return (0 until array.length()).map { i -> parseRecord(array.getJSONObject(i)) }
            .sortedByDescending { it.dateMillis }
    }

    fun save(context: Context, courseId: String, courseName: String, holeScores: List<HoleScore>) {
        if (holeScores.isEmpty()) return
        saveRecord(
            context,
            RoundRecord(
                id = UUID.randomUUID().toString(),
                courseId = courseId,
                courseName = courseName,
                dateMillis = System.currentTimeMillis(),
                holeScores = holeScores
            )
        )
    }

    /** QR 가져오기 등 완성된 기록을 그대로 저장할 때 사용 */
    fun saveRecord(context: Context, record: RoundRecord) {
        val existing = loadAll(context).toMutableList()
        existing.add(0, record)
        val trimmed = existing.take(MAX_RECORDS)

        val array = JSONArray()
        trimmed.forEach { array.put(toJson(it)) }
        prefs(context).edit().putString(KEY_RECORDS, array.toString()).apply()
    }

    fun delete(context: Context, recordId: String) {
        val remaining = loadAll(context).filter { it.id != recordId }
        val array = JSONArray()
        remaining.forEach { array.put(toJson(it)) }
        prefs(context).edit().putString(KEY_RECORDS, array.toString()).apply()
    }

    private fun toJson(record: RoundRecord): JSONObject {
        val holesArray = JSONArray()
        record.holeScores.forEach { hole ->
            holesArray.put(JSONObject().apply {
                put("number", hole.holeNumber)
                put("strokes", hole.strokes)
            })
        }
        return JSONObject().apply {
            put("id", record.id)
            put("courseId", record.courseId)
            put("courseName", record.courseName)
            put("dateMillis", record.dateMillis)
            put("holes", holesArray)
        }
    }

    private fun parseRecord(obj: JSONObject): RoundRecord {
        val holesArray = obj.getJSONArray("holes")
        val holes = (0 until holesArray.length()).map { i ->
            val h = holesArray.getJSONObject(i)
            HoleScore(h.getInt("number"), h.getInt("strokes"))
        }
        return RoundRecord(
            id = obj.getString("id"),
            courseId = obj.getString("courseId"),
            courseName = obj.getString("courseName"),
            dateMillis = obj.getLong("dateMillis"),
            holeScores = holes
        )
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
