package com.linehelper

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.view.MotionEvent
import android.view.View
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

class TrajectoryView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(190, 255, 255, 255)
        textAlign = Paint.Align.CENTER
        textSize = 34f
    }
    private val arrowPath = Path()

    private var fieldLeft = 0f
    private var fieldRight = 0f
    private var fieldTop = 0f
    private var fieldBottom = 0f
    private var goalWidth = 0f
    private var goalLeft = 0f
    private var goalRight = 0f

    private var ballX = 0f
    private var ballY = 0f
    private var touchStartX = 0f
    private var touchStartY = 0f
    private var aimX = 0f
    private var aimY = 0f
    private var isAiming = false

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        fieldLeft = w * 0.06f + 14f
        fieldRight = w * 0.94f - 14f
        fieldTop = h * 0.06f
        fieldBottom = h * 0.92f
        goalWidth = w * 0.32f
        goalLeft = w / 2f - goalWidth / 2f
        goalRight = w / 2f + goalWidth / 2f
        ballX = w / 2f
        ballY = h * 0.55f
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                touchStartX = event.x
                touchStartY = event.y
                aimX = event.x
                aimY = event.y
                isAiming = true
                invalidate()
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                aimX = event.x
                aimY = event.y
                invalidate()
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isAiming = false
                invalidate()
                return true
            }
        }
        return true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        drawGoalZone(canvas)

        if (isAiming) {
            val shot = currentShot()
            if (shot != null) {
                val points = simulatePhysics(ballX, ballY, shot.dirX, shot.dirY, shot.speed)
                drawTrajectory(canvas, points)
                drawPowerBar(canvas, shot.speed)
            }
        } else {
            canvas.drawText("Drag to preview shot", width / 2f, height * 0.82f, textPaint)
        }

        drawBall(canvas)
    }

    private fun currentShot(): Shot? {
        val dragX = aimX - touchStartX
        val dragY = aimY - touchStartY
        val swipeDist = hypot(dragX, dragY)
        if (swipeDist <= 0.01f) return null

        val dirX = -dragX / swipeDist
        val dirY = -dragY / swipeDist
        val initSpeed = (swipeDist / 12f).coerceIn(6f, 22f)
        return Shot(dirX, dirY, initSpeed)
    }

    fun simulatePhysics(
        startX: Float,
        startY: Float,
        dirX: Float,
        dirY: Float,
        initSpeed: Float
    ): List<PhysicsPoint> {
        val points = mutableListOf<PhysicsPoint>()
        var x = startX
        var y = startY
        var vx = dirX * initSpeed
        var vy = dirY * initSpeed

        repeat(MAX_STEPS) {
            val speed = hypot(vx, vy)
            if (speed < MIN_SPEED) return points

            points.add(PhysicsPoint(x, y, speed))

            var nx = x + vx * DT
            var ny = y + vy * DT

            if (nx < fieldLeft) {
                nx = fieldLeft
                vx = -vx * WALL_RESTITUTION
            } else if (nx > fieldRight) {
                nx = fieldRight
                vx = -vx * WALL_RESTITUTION
            }

            if (ny < fieldTop) {
                points.add(PhysicsPoint(nx, fieldTop, hypot(vx, vy)))
                return points
            } else if (ny > fieldBottom) {
                ny = fieldBottom
                vy = -vy * WALL_RESTITUTION
            }

            x = nx
            y = ny
            vx *= FRICTION
            vy *= FRICTION
        }

        return points
    }

    private fun drawTrajectory(canvas: Canvas, points: List<PhysicsPoint>) {
        if (points.size < 2) return

        val maxSpeed = max(points.maxOf { it.speed }, MIN_SPEED)
        var distance = 0f
        var nextDotAt = 0f
        var lastX = points.first().x
        var lastY = points.first().y

        for (point in points) {
            distance += hypot(point.x - lastX, point.y - lastY)
            val speedRatio = (point.speed / maxSpeed).coerceIn(0f, 1f)
            val dotSpacing = 5f + speedRatio * 13f

            if (distance >= nextDotAt) {
                val dotRadius = 2f + speedRatio * 2.5f
                val alpha = ((0.35f + speedRatio * 0.55f) * 255).toInt().coerceIn(0, 255)
                paint.color = Color.argb(alpha, 100, 220, 255)
                paint.style = Paint.Style.FILL
                canvas.drawCircle(point.x, point.y, dotRadius, paint)
                nextDotAt = distance + dotSpacing
            }

            lastX = point.x
            lastY = point.y
        }

        drawArrowhead(canvas, points)
    }

    private fun drawArrowhead(canvas: Canvas, points: List<PhysicsPoint>) {
        val last = points.last()
        val previous = points[points.lastIndex - 1]
        val angle = atan2(last.y - previous.y, last.x - previous.x)
        val size = 22f
        val spread = 0.65f

        arrowPath.reset()
        arrowPath.moveTo(last.x, last.y)
        arrowPath.lineTo(
            last.x - cos(angle - spread) * size,
            last.y - sin(angle - spread) * size
        )
        arrowPath.lineTo(
            last.x - cos(angle + spread) * size,
            last.y - sin(angle + spread) * size
        )
        arrowPath.close()

        paint.color = Color.argb(230, 100, 220, 255)
        paint.style = Paint.Style.FILL
        canvas.drawPath(arrowPath, paint)
    }

    private fun drawPowerBar(canvas: Canvas, initSpeed: Float) {
        val barWidth = width * 0.45f
        val barHeight = 14f
        val left = (width - barWidth) / 2f
        val top = height * 0.875f - barHeight
        val right = left + barWidth
        val bottom = top + barHeight
        val power = ((initSpeed - 6f) / 16f).coerceIn(0f, 1f)

        paint.style = Paint.Style.FILL
        paint.color = Color.argb((0.12f * 255).toInt(), 255, 255, 255)
        canvas.drawRoundRect(left, top, right, bottom, 7f, 7f, paint)

        paint.color = when {
            power < 0.4f -> Color.rgb(58, 224, 117)
            power < 0.75f -> Color.rgb(255, 220, 64)
            else -> Color.rgb(255, 80, 74)
        }
        canvas.drawRoundRect(left, top, left + barWidth * power, bottom, 7f, 7f, paint)

        paint.color = Color.argb(210, 255, 255, 255)
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = 24f
        canvas.drawText("POWER", width / 2f, top - 12f, paint)
    }

    private fun drawGoalZone(canvas: Canvas) {
        paint.style = Paint.Style.FILL
        paint.color = Color.argb(58, 76, 255, 132)
        canvas.drawRect(goalLeft, fieldTop - 12f, goalRight, fieldTop + 22f, paint)
    }

    private fun drawBall(canvas: Canvas) {
        val radius = width * 0.043f
        paint.style = Paint.Style.FILL
        paint.color = Color.WHITE
        canvas.drawCircle(ballX, ballY, radius, paint)

        paint.color = Color.rgb(42, 48, 54)
        drawPatch(canvas, ballX, ballY - radius * 0.24f, radius * 0.26f, -90f)
        drawPatch(canvas, ballX - radius * 0.36f, ballY + radius * 0.18f, radius * 0.2f, 40f)
        drawPatch(canvas, ballX + radius * 0.38f, ballY + radius * 0.2f, radius * 0.2f, -20f)
    }

    private fun drawPatch(canvas: Canvas, cx: Float, cy: Float, radius: Float, rotationDegrees: Float) {
        val path = Path()
        val rotation = Math.toRadians(rotationDegrees.toDouble()).toFloat()

        repeat(5) { index ->
            val angle = rotation + Math.toRadians((index * 72 - 90).toDouble()).toFloat()
            val x = cx + cos(angle) * radius
            val y = cy + sin(angle) * radius
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        canvas.drawPath(path, paint)
    }

    data class PhysicsPoint(val x: Float, val y: Float, val speed: Float)

    private data class Shot(val dirX: Float, val dirY: Float, val speed: Float)

    companion object {
        const val FRICTION = 0.975f
        const val WALL_RESTITUTION = 0.72f
        const val MIN_SPEED = 0.35f
        const val DT = 1.0f
        const val MAX_STEPS = 800
    }
}
