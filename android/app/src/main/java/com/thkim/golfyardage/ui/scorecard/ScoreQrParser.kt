package com.thkim.golfyardage.ui.scorecard

import com.thkim.golfyardage.data.model.HoleScore
import com.thkim.golfyardage.data.model.RoundRecord
import org.json.JSONObject
import java.util.UUID

sealed class QrParseResult {
    data class UrlLink(val url: String) : QrParseResult()
    data class Parsed(val record: RoundRecord) : QrParseResult()
    data class Unrecognized(val rawText: String) : QrParseResult()
}

/**
 * 캐디/골프장 스코어 서비스마다 QR 데이터 형식이 달라서, 아직 정확한 스펙을 모른다.
 * 우선 흔히 쓰일 법한 형태(웹 링크, 단순 JSON)만 시도해보고, 못 알아들으면 원문을 그대로 보여준다.
 */
object ScoreQrParser {

    fun parse(raw: String): QrParseResult {
        val trimmed = raw.trim()
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            return QrParseResult.UrlLink(trimmed)
        }

        parseAsJson(trimmed)?.let { return QrParseResult.Parsed(it) }

        return QrParseResult.Unrecognized(trimmed)
    }

    private fun parseAsJson(text: String): RoundRecord? {
        return try {
            val obj = JSONObject(text)
            val courseName = obj.optString("courseName").ifBlank { obj.optString("course") }
                .ifBlank { return null }

            val holes = mutableListOf<HoleScore>()
            val holesArray = obj.optJSONArray("holes")
            if (holesArray != null) {
                for (i in 0 until holesArray.length()) {
                    val h = holesArray.getJSONObject(i)
                    val number = h.optInt("number", h.optInt("hole", -1))
                    val strokes = h.optInt("strokes", h.optInt("score", -1))
                    if (number > 0 && strokes > 0) holes.add(HoleScore(number, strokes))
                }
            } else {
                val scoresObj = obj.optJSONObject("scores") ?: return null
                scoresObj.keys().forEach { key ->
                    val number = key.toIntOrNull() ?: return@forEach
                    val strokes = scoresObj.optInt(key, -1)
                    if (strokes > 0) holes.add(HoleScore(number, strokes))
                }
            }
            if (holes.isEmpty()) return null

            RoundRecord(
                id = UUID.randomUUID().toString(),
                courseId = "",
                courseName = courseName,
                dateMillis = obj.optLong("dateMillis", System.currentTimeMillis()),
                holeScores = holes.sortedBy { it.holeNumber }
            )
        } catch (e: Exception) {
            null
        }
    }
}
