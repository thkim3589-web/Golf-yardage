package com.thkim.golfyardage.data.model

data class ClubDistance(
    val clubName: String,
    /** 사용자가 입력한 평균 비거리(m). 미입력이면 null */
    val averageMeters: Double?
)

/** 기본 클럽 목록 (한국 골퍼들에게 익숙한 표기) */
val DEFAULT_CLUBS: List<String> = listOf(
    "드라이버", "3번 우드", "5번 우드", "4번 아이언", "5번 아이언",
    "6번 아이언", "7번 아이언", "8번 아이언", "9번 아이언",
    "피칭웨지(PW)", "어프로치웨지(AW)", "샌드웨지(SW)", "로브웨지(LW)"
)
