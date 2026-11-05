package com.thkim.golfyardage.data

import android.content.Context

/**
 * 사용자가 매긴 골프장 별점(1~5)을 기기 로컬에 저장한다.
 * 서버·계정이 없는 앱이라 "여러 사용자의 평균 별점"이 아니라 "내가 매긴 별점"만 존재한다.
 */
object RatingStore {

    private const val PREFS_NAME = "ratings"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getRating(context: Context, courseId: String): Int =
        prefs(context).getInt(courseId, 0)

    fun setRating(context: Context, courseId: String, stars: Int) {
        prefs(context).edit().putInt(courseId, stars.coerceIn(0, 5)).apply()
    }
}
