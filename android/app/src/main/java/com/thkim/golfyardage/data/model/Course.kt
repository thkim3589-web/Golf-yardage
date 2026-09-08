package com.thkim.golfyardage.data.model

data class CourseSummary(
    val id: String,
    val name: String,
    val region: String,
    /** assets/courses/images/ 아래의 파일명. 없으면 기본 플레이스홀더 이미지 사용 */
    val imageFile: String?,
    /** 클럽하우스 등 대표 좌표. 날씨 조회 및 초기 지도 위치에 사용 */
    val location: GeoPoint?
)

data class Course(
    val id: String,
    val name: String,
    val region: String,
    val imageFile: String?,
    val location: GeoPoint?,
    val holes: List<Hole>
)
