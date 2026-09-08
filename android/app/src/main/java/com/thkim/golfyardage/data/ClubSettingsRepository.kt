package com.thkim.golfyardage.data

import android.content.Context
import com.thkim.golfyardage.data.model.ClubDistance
import com.thkim.golfyardage.data.model.DEFAULT_CLUBS

/** 클럽별 평균 비거리를 기기 로컬(SharedPreferences)에 저장한다. */
object ClubSettingsRepository {

    private const val PREFS_NAME = "club_distances"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun loadAll(context: Context): List<ClubDistance> {
        val p = prefs(context)
        return DEFAULT_CLUBS.map { club ->
            val value = p.getFloat(club, -1f)
            ClubDistance(club, if (value < 0f) null else value.toDouble())
        }
    }

    fun save(context: Context, clubName: String, meters: Double?) {
        val editor = prefs(context).edit()
        if (meters == null) editor.remove(clubName) else editor.putFloat(clubName, meters.toFloat())
        editor.apply()
    }

    /** 남은 거리(m)에 가장 가까운 클럽을 추천한다. 입력된 클럽이 하나도 없으면 null. */
    fun recommendClub(context: Context, remainingMeters: Double): ClubDistance? {
        val candidates = loadAll(context).filter { it.averageMeters != null }
        if (candidates.isEmpty()) return null
        return candidates.minByOrNull { kotlin.math.abs(it.averageMeters!! - remainingMeters) }
    }
}
