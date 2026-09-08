package com.thkim.golfyardage

import android.app.Application
import android.content.Intent
import android.util.Log
import com.kakao.vectormap.KakaoMapSdk
import com.thkim.golfyardage.ui.crash.CrashActivity
import kotlin.system.exitProcess

class GolfYardageApp : Application() {
    override fun onCreate() {
        super.onCreate()

        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val intent = Intent(this, CrashActivity::class.java).apply {
                    putExtra(CrashActivity.EXTRA_STACK_TRACE, Log.getStackTraceString(throwable))
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
                startActivity(intent)
            } catch (e: Exception) {
                // 크래시 화면 자체가 실패하면 기본 처리로 넘긴다
            }
            android.os.Process.killProcess(android.os.Process.myPid())
            exitProcess(10)
        }

        try {
            KakaoMapSdk.init(this, BuildConfig.KAKAO_NATIVE_APP_KEY)
        } catch (e: Exception) {
            Log.e("GolfYardageApp", "Kakao Maps SDK 초기화 실패", e)
        }
    }
}
