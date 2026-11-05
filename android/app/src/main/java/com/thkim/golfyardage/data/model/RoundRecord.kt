package com.thkim.golfyardage.data.model

data class HoleScore(val holeNumber: Int, val strokes: Int)

data class RoundRecord(
    val id: String,
    val courseId: String,
    val courseName: String,
    val dateMillis: Long,
    val holeScores: List<HoleScore>
) {
    val totalStrokes: Int get() = holeScores.sumOf { it.strokes }
}
