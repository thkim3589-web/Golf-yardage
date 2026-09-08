package com.thkim.golfyardage.data

import com.thkim.golfyardage.data.model.GeoPoint
import com.thkim.golfyardage.data.model.WeatherInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Open-Meteo(https://open-meteo.com)의 무료·키 불필요 API로 현재 날씨를 조회한다.
 * 골프장 상공 실시간 관측이 아닌 격자 기반 예보값이므로 참고용으로 안내한다.
 */
object WeatherRepository {

    suspend fun fetchCurrentWeather(location: GeoPoint): WeatherInfo? = withContext(Dispatchers.IO) {
        try {
            val url = URL(
                "https://api.open-meteo.com/v1/forecast" +
                    "?latitude=${location.lat}&longitude=${location.lng}" +
                    "&current=temperature_2m,wind_speed_10m,wind_direction_10m,weather_code" +
                    "&wind_speed_unit=ms"
            )
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 8000
            connection.readTimeout = 8000
            connection.requestMethod = "GET"

            val code = connection.responseCode
            if (code != HttpURLConnection.HTTP_OK) return@withContext null

            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val current = JSONObject(body).optJSONObject("current") ?: return@withContext null

            WeatherInfo(
                temperatureC = current.optDouble("temperature_2m"),
                windSpeedMs = current.optDouble("wind_speed_10m"),
                windDirectionDeg = current.optDouble("wind_direction_10m"),
                conditionText = weatherCodeToText(current.optInt("weather_code", -1))
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun weatherCodeToText(code: Int): String = when (code) {
        0 -> "맑음"
        1, 2 -> "대체로 맑음"
        3 -> "흐림"
        45, 48 -> "안개"
        51, 53, 55, 56, 57 -> "이슬비"
        61, 63, 65, 66, 67 -> "비"
        71, 73, 75, 77 -> "눈"
        80, 81, 82 -> "소나기"
        85, 86 -> "눈 소나기"
        95, 96, 99 -> "뇌우"
        else -> "-"
    }
}
