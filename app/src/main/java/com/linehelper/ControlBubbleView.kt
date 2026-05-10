package com.linehelper

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View

class ControlBubbleView(
    context: Context,
    private val onAimToggle: () -> Unit,
    private val onStop: () -> Unit
) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        textSize = 24f
        isFakeBoldText = true
    }
    private val aimRect = RectF()
    private val stopRect = RectF()
    private var aimModeEnabled = false

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val density = resources.displayMetrics.density
        setMeasuredDimension((74 * density).toInt(), (132 * density).toInt())
    }

    fun setAimModeEnabled(enabled: Boolean) {
        aimModeEnabled = enabled
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val gap = 10f
        val buttonHeight = (height - gap) / 2f

        aimRect.set(0f, 0f, width.toFloat(), buttonHeight)
        stopRect.set(0f, buttonHeight + gap, width.toFloat(), height.toFloat())

        paint.style = Paint.Style.FILL
        paint.color = if (aimModeEnabled) Color.rgb(72, 210, 111) else Color.rgb(30, 144, 255)
        canvas.drawRoundRect(aimRect, 18f, 18f, paint)

        paint.color = Color.rgb(220, 54, 54)
        canvas.drawRoundRect(stopRect, 18f, 18f, paint)

        canvas.drawText(if (aimModeEnabled) "ON" else "AIM", aimRect.centerX(), aimRect.centerY() + 9f, labelPaint)
        canvas.drawText("STOP", stopRect.centerX(), stopRect.centerY() + 9f, labelPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked != MotionEvent.ACTION_UP) return true

        when {
            aimRect.contains(event.x, event.y) -> onAimToggle()
            stopRect.contains(event.x, event.y) -> onStop()
        }
        return true
    }
}
