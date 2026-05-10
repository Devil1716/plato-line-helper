package com.linehelper

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

class AimPadView(
    context: Context,
    private val onAimChanged: (dirX: Float, dirY: Float, power: Float, active: Boolean) -> Unit
) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        textSize = 15f
        isFakeBoldText = true
    }
    private var knobX = 0f
    private var knobY = 0f
    private var dragging = false

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val size = (112 * resources.displayMetrics.density).toInt()
        setMeasuredDimension(size, size)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val radius = min(width, height) * 0.42f

        paint.style = Paint.Style.FILL
        paint.color = Color.argb(72, 0, 0, 0)
        canvas.drawCircle(cx, cy, radius, paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        paint.color = Color.argb(160, 255, 255, 255)
        canvas.drawCircle(cx, cy, radius, paint)
        canvas.drawLine(cx - radius, cy, cx + radius, cy, paint)
        canvas.drawLine(cx, cy - radius, cx, cy + radius, paint)

        val kx = if (dragging) knobX else cx
        val ky = if (dragging) knobY else cy
        paint.style = Paint.Style.FILL
        paint.color = Color.argb(210, 80, 255, 130)
        canvas.drawCircle(kx, ky, 10f, paint)
        canvas.drawText("AIM", cx, height - 10f, textPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val cx = width / 2f
        val cy = height / 2f
        val maxRadius = min(width, height) * 0.42f

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                dragging = true
                val dx = event.x - cx
                val dy = event.y - cy
                val distance = hypot(dx, dy).coerceAtMost(maxRadius)
                val angle = atan2(dy, dx)
                knobX = cx + cos(angle) * distance
                knobY = cy + sin(angle) * distance

                val power = (distance / maxRadius).coerceIn(0f, 1f)
                if (power > 0.08f) {
                    onAimChanged(-cos(angle), -sin(angle), power, true)
                }
                invalidate()
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                dragging = false
                onAimChanged(0f, 0f, 0f, false)
                invalidate()
                return true
            }
        }
        return true
    }
}
