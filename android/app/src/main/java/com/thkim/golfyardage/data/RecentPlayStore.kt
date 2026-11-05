package com.thkim.golfyardage.data

import android.content.Context

/** 최근에 라운드를 시작한 코스와 시각을 기기 로컬에 기록한다. */
object RecentPlayStore {

    private const val PREFS_NAME = "recent_play"
    private const val KEY_LAST_COURSE_ID = "last_course_id"
    private const val KEY_LAST_PLAYED_AT = "last_played_at"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun recordPlay(context: Context, courseId: String) {
        prefs(context).edit()
            .putString(KEY_LAST_COURSE_ID, courseId)
            .putLong(KEY_LAST_PLAYED_AT, System.currentTimeMillis())
            .apply()
    }

    fun lastPlayedCourseId(context: Context): String? =
        prefs(context).getString(KEY_LAST_COURSE_ID, null)

    fun lastPlayedAt(context: Context): Long =
        prefs(context).getLong(KEY_LAST_PLAYED_AT, 0L)
}
