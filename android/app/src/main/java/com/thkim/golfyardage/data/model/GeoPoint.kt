package com.thkim.golfyardage.data.model

import com.kakao.vectormap.LatLng

/**
 * 위경도 좌표. 아직 입력되지 않은 지점은 null로 취급한다.
 * elevation(고도, m)은 선택 입력값으로, 경사 반영 거리 계산에 사용된다.
 */
data class GeoPoint(val lat: Double, val lng: Double, val elevation: Double? = null) {
    fun toKakaoLatLng(): LatLng = LatLng.from(lat, lng)
}
