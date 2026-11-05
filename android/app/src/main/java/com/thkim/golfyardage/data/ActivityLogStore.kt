package com.thkim.golfyardage.data

import android.content.Context
import com.thkim.golfyardage.data.model.ActivityLogEntry
import org.json.JSONArray
import org.json.JSONObject

/** 라운드 시작, 티 좌표 등록 같은 앱 내 활동을 시간순으로 기록한다(서버 없이 기기 내 로그). */
object ActivityLogStore {

    private const val PREFS_NAME = "activity_log"
    private const val KEY_ENTRIES = "entries"
    private const val MAX_ENTRIES = 50

    fun log(context: Context, message: String) {
        val entries = loadAll(context).toMutableList()
        entries.add(0, ActivityLogEntry(message, System.currentTimeMillis()))
        val trimmed = entries.take(MAX_ENTRIES)

        val array = JSONArray()
        trimmed.forEach { entry ->
            array.put(JSONObject().apply {
                put("message", entry.message)
                put("dateMillis", entry.dateMillis)
            })
        }
        prefs(context).edit().putString(KEY_ENTRIES, array.toString()).apply()
    }

    fun loadAll(context: Context): List<ActivityLogEntry> {
        val text = prefs(context).getString(KEY_ENTRIES, null) ?: return emptyList()
        val array = JSONArray(text)
        return (0 until array.length()).map { i ->
            val obj = array.getJSONObject(i)
            ActivityLogEntry(obj.getString("message"), obj.getLong("dateMillis"))
        }
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
