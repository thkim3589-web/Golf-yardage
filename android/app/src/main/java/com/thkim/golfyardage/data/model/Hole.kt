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
    val strategyTip: String? = null, // 홀 공략법 메모
    /** 정규 티 기준 홀 전장(m). 골프장 공식 자료가 있을 때만 채워진다. */
    val yardageMeters: Int? = null,
    /** 핸디캡(스트로크 인덱스). 낮을수록 어려운 홀. */
    val hdcp: Int? = null,
    /** 그린 크기 메모(예: "31m×20m"). 골프장 그린 지도 등을 보고 눈대중으로 채워 넣는다. */
    val greenSizeNote: String? = null,
    /** assets/courses/illustrations/ 아래의 홀 다이어그램 파일명(위성사진 화질 한계를 보완하기 위한 자체 제작 일러스트) */
    val illustrationFile: String? = null
) {
    /** 이 홀의 그린 좌표가 하나라도 입력되었는지 */
    val hasGreenData: Boolean
        get() = greenFront != null || greenCenter != null || greenBack != null
}
