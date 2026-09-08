package com.thkim.golfyardage.data.model

data class WeatherInfo(
    val temperatureC: Double,
    val windSpeedMs: Double,
    val windDirectionDeg: Double,
    val conditionText: String
)
