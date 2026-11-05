package com.thkim.golfyardage.ui.management

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.thkim.golfyardage.data.StrategyDrawing
import com.thkim.golfyardage.data.StrategyPoint
import com.thkim.golfyardage.data.StrategyStroke

enum class StrategyTool { DRAW, HAZARD }

/**
 * 홀 배경(페어웨이/그린/벙커/핀)을 그린 위에, 사용자가 손가락으로 공략선을 그리거나
 * 위험(X) 표시를 남길 수 있는 캔버스. 좌표는 뷰 크기와 무관하게 0~1 비율로 저장한다.
 */
class StrategyCanvasView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    var tool: StrategyTool = StrategyTool.DRAW

    private val strokes = mutableListOf<MutableList<StrategyPoint>>()
    private var currentStroke: MutableList<StrategyPoint>? = null
    private val hazards = mutableListOf<StrategyPoint>()

    private val fairwayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#2E4A32") }
    private val greenPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#7FC96B") }
    private val bunkerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#D4AC0D") }
    private val pinPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF6B35")
        strokeWidth = 3f
    }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#4FC3F7")
        style = Paint.Style.STROKE
        strokeWidth = 7f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val hazardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF6B35")
        style = Paint.Style.STROKE
        strokeWidth = 5f
        strokeCap = Paint.Cap.ROUND
    }

    private val fairwayPath = Path()

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        buildFairwayPath(w.toFloat(), h.toFloat())
    }

    private fun buildFairwayPath(w: Float, h: Float) {
        fairwayPath.reset()
        fairwayPath.moveTo(w * 0.3f, h)
        fairwayPath.cubicTo(w * 0.2f, h * 0.6f, w * 0.6f, h * 0.4f, w * 0.5f, h * 0.15f)
        fairwayPath.cubicTo(w * 0.4f, h * 0.15f, w * 0.7f, h * 0.6f, w * 0.7f, h)
        fairwayPath.close()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        canvas.drawPath(fairwayPath, fairwayPaint)
        canvas.drawOval(w * 0.5f - 35f, h * 0.18f - 25f, w * 0.5f + 35f, h * 0.18f + 25f, greenPaint)

        canvas.drawLine(w * 0.5f, h * 0.12f, w * 0.5f, h * 0.12f + 15f, pinPaint)
        canvas.drawCircle(w * 0.5f, h * 0.12f, 3f, pinPaint)

        canvas.save()
        canvas.rotate(45f, w * 0.38f, h * 0.45f)
        canvas.drawOval(w * 0.38f - 15f, h * 0.45f - 10f, w * 0.38f + 15f, h * 0.45f + 10f, bunkerPaint)
        canvas.restore()

        strokes.forEach { drawStroke(canvas, it, w, h) }
        currentStroke?.let { drawStroke(canvas, it, w, h) }

        hazards.forEach { p ->
            val x = p.x * w
            val y = p.y * h
            canvas.drawLine(x - 8f, y - 8f, x + 8f, y + 8f, hazardPaint)
            canvas.drawLine(x + 8f, y - 8f, x - 8f, y + 8f, hazardPaint)
        }
    }

    private fun drawStroke(canvas: Canvas, points: List<StrategyPoint>, w: Float, h: Float) {
        if (points.size < 2) return
        val path = Path()
        path.moveTo(points[0].x * w, points[0].y * h)
        for (i in 1 until points.size) path.lineTo(points[i].x * w, points[i].y * h)
        canvas.drawPath(path, strokePaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (width == 0 || height == 0) return true
        val nx = (event.x / width).coerceIn(0f, 1f)
        val ny = (event.y / height).coerceIn(0f, 1f)

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                if (tool == StrategyTool.HAZARD) {
                    hazards.add(StrategyPoint(nx, ny))
                    invalidate()
                } else {
                    currentStroke = mutableListOf(StrategyPoint(nx, ny))
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (tool == StrategyTool.DRAW) {
                    currentStroke?.add(StrategyPoint(nx, ny))
                    invalidate()
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                currentStroke?.let { if (it.size >= 2) strokes.add(it) }
                currentStroke = null
                invalidate()
            }
        }
        return true
    }

    fun clearAll() {
        strokes.clear()
        hazards.clear()
        currentStroke = null
        invalidate()
    }

    fun getDrawing(): StrategyDrawing =
        StrategyDrawing(strokes.map { StrategyStroke(it.toList()) }, hazards.toList())

    fun setDrawing(drawing: StrategyDrawing) {
        strokes.clear()
        strokes.addAll(drawing.strokes.map { it.points.toMutableList() })
        hazards.clear()
        hazards.addAll(drawing.hazards)
        invalidate()
    }
}
