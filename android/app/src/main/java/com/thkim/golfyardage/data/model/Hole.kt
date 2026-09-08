package com.thkim.golfyardage.data.model

/** 그린 경사 정보 (사용자가 직접 입력) */
data class GreenSlope(
    val direction: String?,   // 예: "왼쪽에서 오른쪽", "뒤에서 앞"
    val degreePercent: Double? // 경사도(%), 예: 3.5
)

data class Hole(
    val number: Int,
    val par: Int,
    val tee: GeoPoint?,
    val greenFront: GeoPoint?,
    val greenCenter: GeoPoint?,
    val greenBack: GeoPoint?,
    val pin: GeoPoint?,
    val greenBoundary: List<GeoPoint> = emptyList(),
    val slope: GreenSlope? = null,
    val strategyTip: String? = null // 홀 공략법 메모
) {
    /** 이 홀의 그린 좌표가 하나라도 입력되었는지 */
    val hasGreenData: Boolean
        get() = greenFront != null || greenCenter != null || greenBack != null
}
