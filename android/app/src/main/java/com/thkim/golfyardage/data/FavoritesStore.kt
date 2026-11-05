package com.thkim.golfyardage.data

import android.content.Context

/** 즐겨찾기한 골프장 id 목록을 기기 로컬에 저장한다. */
object FavoritesStore {

    private const val PREFS_NAME = "favorites"
    private const val KEY_IDS = "course_ids"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isFavorite(context: Context, courseId: String): Boolean =
        prefs(context).getStringSet(KEY_IDS, emptySet())?.contains(courseId) == true

    fun toggle(context: Context, courseId: String): Boolean {
        val p = prefs(context)
        val current = HashSet(p.getStringSet(KEY_IDS, emptySet()) ?: emptySet())
        val nowFavorite = if (current.contains(courseId)) {
            current.remove(courseId)
            false
        } else {
            current.add(courseId)
            true
        }
        p.edit().putStringSet(KEY_IDS, current).apply()
        return nowFavorite
    }

    fun all(context: Context): Set<String> =
        prefs(context).getStringSet(KEY_IDS, emptySet()) ?: emptySet()
}
