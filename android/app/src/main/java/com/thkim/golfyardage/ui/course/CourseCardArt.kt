package com.thkim.golfyardage.ui.course

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import kotlin.math.abs

/**
 * 실제 골프장 사진/로고 없이도 코스마다 서로 다른 카드처럼 보이도록,
 * 코스 이름을 시드로 색상과 이니셜을 결정해 그려주는 자체 제작 카드 배경.
 */
object CourseCardArt {

    private val palettes = listOf(
        intArrayOf(0xFF1B4332.toInt(), 0xFF2D6A4F.toInt()),
        intArrayOf(0xFF1D3557.toInt(), 0xFF457B9D.toInt()),
        intArrayOf(0xFF6B4226.toInt(), 0xFF9C6644.toInt()),
        intArrayOf(0xFF3D348B.toInt(), 0xFF7678ED.toInt()),
        intArrayOf(0xFF2F3E46.toInt(), 0xFF52796F.toInt()),
        intArrayOf(0xFF6A040F.toInt(), 0xFFAD2831.toInt()),
        intArrayOf(0xFF073B4C.toInt(), 0xFF118AB2.toInt()),
        intArrayOf(0xFF582F0E.toInt(), 0xFF7F4F24.toInt()),
        intArrayOf(0xFF344E41.toInt(), 0xFF588157.toInt()),
        intArrayOf(0xFF3A0CA3.toInt(), 0xFF7209B7.toInt())
    )

    private fun paletteFor(seed: String): IntArray {
        val hash = seed.fold(0) { acc, c -> acc * 31 + c.code }
        return palettes[abs(hash) % palettes.size]
    }

    private fun initial(name: String): String {
        val stripped = name.trim()
            .removePrefix("골프존카운티").trim()
        val target = stripped.ifEmpty { name.trim() }
        return target.take(1).ifEmpty { "G" }
    }

    fun build(name: String): Drawable = object : Drawable() {
        private val colors = paletteFor(name)
        private val label = initial(name)

        private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
        }
        private val flagLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(90, 255, 255, 255)
            style = Paint.Style.STROKE
        }
        private val flagFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(90, 255, 255, 255)
            style = Paint.Style.FILL
        }
        private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)

        override fun draw(canvas: Canvas) {
            val w = bounds.width().toFloat()
            val h = bounds.height().toFloat()
            if (w <= 0f || h <= 0f) return

            bgPaint.shader = LinearGradient(0f, 0f, w, h, colors[0], colors[1], Shader.TileMode.CLAMP)
            canvas.drawRect(bounds, bgPaint)

            val flagX = w * 0.82f
            val flagTopY = h * 0.40f
            val flagBaseY = h * 0.82f
            flagLinePaint.strokeWidth = w * 0.012f
            canvas.drawLine(flagX, flagTopY, flagX, flagBaseY, flagLinePaint)
            val flagPath = Path().apply {
                moveTo(flagX, flagTopY)
                lineTo(flagX + w * 0.10f, flagTopY + h * 0.055f)
                lineTo(flagX, flagTopY + h * 0.11f)
                close()
            }
            canvas.drawPath(flagPath, flagFillPaint)

            textPaint.textSize = h * 0.42f
            val fm = textPaint.fontMetrics
            val textY = h / 2f - (fm.ascent + fm.descent) / 2f
            canvas.drawText(label, w * 0.36f, textY, textPaint)
        }

        override fun setAlpha(alpha: Int) {
            textPaint.alpha = alpha
        }

        override fun setColorFilter(colorFilter: ColorFilter?) {
            bgPaint.colorFilter = colorFilter
        }

        @Deprecated("Deprecated in Java", ReplaceWith("PixelFormat.OPAQUE"))
        override fun getOpacity(): Int = PixelFormat.OPAQUE
    }
}
