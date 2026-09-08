package com.thkim.golfyardage.ui.crash

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.NestedScrollView

class CrashActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_STACK_TRACE = "extra_stack_trace"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val scroll = NestedScrollView(this)
        val textView = TextView(this).apply {
            setPadding(32, 64, 32, 64)
            textSize = 12f
            setTextIsSelectable(true)
            text = "앱 오류가 발생했습니다. 이 화면을 캡처해서 보내주세요.\n\n" +
                (intent.getStringExtra(EXTRA_STACK_TRACE) ?: "(오류 정보 없음)")
        }
        scroll.addView(textView)
        setContentView(scroll)
    }
}
