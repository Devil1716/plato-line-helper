package com.linehelper

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs

class ControlBubbleView(
    context: Context,
    private val onAimToggle: () -> Unit,
    private val onStop: () -> Unit,
    private val onDrag: (Int, Int) -> Unit
) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        textSize = 18f
        isFakeBoldText = true
    }
    private val aimRect = RectF()
    private val stopRect = RectF()
    private var aimModeEnabled = false
    private var downX = 0f
    private var downY = 0f
    private var lastRawX = 0f
    private var lastRawY = 0f
    private var moved = false

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val density = resources.displayMetrics.density
        setMeasuredDimension((58 * density).toInt(), (106 * density).toInt())
    }

    fun setAimModeEnabled(enabled: Boolean) {
        aimModeEnabled = enabled
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val gap = 8f
        val buttonHeight = (height - gap) / 2f

        aimRect.set(0f, 0f, width.toFloat(), buttonHeight)
        stopRect.set(0f, buttonHeight + gap, width.toFloat(), height.toFloat())

        paint.style = Paint.Style.FILL
        paint.color = if (aimModeEnabled) {
            Color.argb(220, 72, 210, 111)
        } else {
            Color.argb(190, 30, 144, 255)
        }
        canvas.drawRoundRect(aimRect, 14f, 14f, paint)

        paint.color = Color.argb(205, 220, 54, 54)
        canvas.drawRoundRect(stopRect, 14f, 14f, paint)

        canvas.drawText(if (aimModeEnabled) "ON" else "AIM", aimRect.centerX(), aimRect.centerY() + 7f, labelPaint)
        canvas.drawText("STOP", stopRect.centerX(), stopRect.centerY() + 7f, labelPaint)
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
                if (abs(event.x - downX) > 8f || abs(event.y - downY) > 8f) {
                    moved = true
                }
                lastRawX = event.rawX
                lastRawY = event.rawY
                onDrag(dx.toInt(), dy.toInt())
                return true
            }

            MotionEvent.ACTION_UP -> {
                if (!moved) {
                    when {
                        aimRect.contains(event.x, event.y) -> onAimToggle()
                        stopRect.contains(event.x, event.y) -> onStop()
                    }
                }
                return true
            }
        }
        return true
    }
}
