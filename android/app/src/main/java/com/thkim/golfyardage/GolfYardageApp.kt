package com.thkim.golfyardage

import android.app.Application
import android.content.Intent
import android.util.Log
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.SvgDecoder
import com.thkim.golfyardage.ui.crash.CrashActivity
import kotlin.system.exitProcess

class GolfYardageApp : Application(), ImageLoaderFactory {
    override fun newImageLoader(): ImageLoader {
        // 골프장 로고 중 SVG 벡터 파일(assets/courses/logos/*.svg)을 디코딩하기 위한 설정
        return ImageLoader.Builder(this)
            .components { add(SvgDecoder.Factory()) }
            .build()
    }

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
    }
}
