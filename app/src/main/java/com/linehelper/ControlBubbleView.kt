package com.linehelper

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs

class ControlBubbleView(
    context: Context,
    private val onGuideToggle: () -> Unit,
    private val onDrag: (Int, Int) -> Unit
) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        textSize = 15f
        isFakeBoldText = true
    }
    private var guideEnabled = true
    private var downX = 0f
    private var downY = 0f
    private var lastRawX = 0f
    private var lastRawY = 0f
    private var moved = false

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val size = (42 * resources.displayMetrics.density).toInt()
        setMeasuredDimension(size, size)
    }

    fun setGuideEnabled(enabled: Boolean) {
        guideEnabled = enabled
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val radius = width / 2f
        paint.style = Paint.Style.FILL
        paint.color = if (guideEnabled) {
            Color.argb(178, 60, 190, 95)
        } else {
            Color.argb(150, 70, 70, 70)
        }
        canvas.drawCircle(radius, radius, radius - 2f, paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        paint.color = Color.argb(180, 255, 255, 255)
        canvas.drawCircle(radius, radius, radius - 3f, paint)

        canvas.drawText(if (guideEnabled) "LH" else "OFF", radius, radius + 5f, labelPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                lastRawX = event.rawX
                lastRawY = event.rawY
                moved = false
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - lastRawX
                val dy = event.rawY - lastRawY
                if (abs(event.x - downX) > 6f || abs(event.y - downY) > 6f) {
                    moved = true
                }
                lastRawX = event.rawX
                lastRawY = event.rawY
                onDrag(dx.toInt(), dy.toInt())
                return true
            }

            MotionEvent.ACTION_UP -> {
                if (!moved) onGuideToggle()
                return true
            }
        }
        return true
    }
}
