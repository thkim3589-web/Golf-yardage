package com.thkim.golfyardage.data

import com.thkim.golfyardage.BuildConfig
import com.thkim.golfyardage.data.model.GeoPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class PlaceResult(val name: String, val address: String, val point: GeoPoint)

/** 카카오 로컬(키워드 장소 검색) API로 골프장 이름을 실제 위치로 찾아준다. */
object KakaoLocalRepository {

    suspend fun searchKeyword(query: String): List<PlaceResult> = withContext(Dispatchers.IO) {
        if (BuildConfig.KAKAO_REST_API_KEY.isBlank()) return@withContext emptyList()
        try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val url = URL("https://dapi.kakao.com/v2/local/search/keyword.json?query=$encoded&size=5")
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 8000
            connection.readTimeout = 8000
            connection.requestMethod = "GET"
            connection.setRequestProperty("Authorization", "KakaoAK ${BuildConfig.KAKAO_REST_API_KEY}")

            if (connection.responseCode != HttpURLConnection.HTTP_OK) return@withContext emptyList()

            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val documents = JSONObject(body).optJSONArray("documents") ?: return@withContext emptyList()

            (0 until documents.length()).mapNotNull { i ->
                val doc = documents.getJSONObject(i)
                val lat = doc.optString("y").toDoubleOrNull() ?: return@mapNotNull null
                val lng = doc.optString("x").toDoubleOrNull() ?: return@mapNotNull null
                PlaceResult(
                    name = doc.optString("place_name"),
                    address = doc.optString("road_address_name").ifBlank { doc.optString("address_name") },
                    point = GeoPoint(lat, lng)
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
