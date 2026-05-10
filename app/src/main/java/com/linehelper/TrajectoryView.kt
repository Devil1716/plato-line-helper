package com.linehelper

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin

class TrajectoryView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(190, 255, 255, 255)
        textAlign = Paint.Align.CENTER
        textSize = 34f
    }
    private val arrowPath = Path()
    private val defaultBounds = FieldBounds(0f, 0f, 0f, 0f, 0f, 0f)

    private var fieldBounds = defaultBounds
    private var lockedFieldBounds: FieldBounds? = null
    private var previousDetectedBounds: FieldBounds? = null
    private var stableBoundsCount = 0

    private var ballX = 0f
    private var ballY = 0f
    private var fallbackBallX = 0f
    private var fallbackBallY = 0f
    private var detectionMisses = 0
    private var hasLiveBallDetection = false

    private var touchStartX = 0f
    private var touchStartY = 0f
    private var aimX = 0f
    private var aimY = 0f
    private var previousAimX = 0f
    private var previousAimY = 0f
    private var swipeStartAngle = 0f
    private var swipeEndAngle = 0f
    private var isAiming = false
    private var aimModeEnabled = false

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val goalWidth = w * 0.32f
        fieldBounds = FieldBounds(
            left = w * 0.06f + 14f,
            right = w * 0.94f - 14f,
            top = h * 0.06f,
            bottom = h * 0.92f,
            goalLeft = w / 2f - goalWidth / 2f,
            goalRight = w / 2f + goalWidth / 2f
        )
        ballX = w / 2f
        ballY = h * 0.55f
        fallbackBallX = ballX
        fallbackBallY = ballY
    }

    fun updateDetectedBall(position: Pair<Float, Float>?) {
        if (position == null) {
            detectionMisses++
            hasLiveBallDetection = false
            if (detectionMisses > 10) {
                ballX = fallbackBallX
                ballY = fallbackBallY
            }
        } else {
            detectionMisses = 0
            hasLiveBallDetection = true
            ballX = position.first
            ballY = position.second
            fallbackBallX = ballX
            fallbackBallY = ballY
        }
        invalidate()
    }

    fun updateDetectedField(bounds: FieldBounds) {
        if (lockedFieldBounds != null) return

        val previous = previousDetectedBounds
        stableBoundsCount = if (previous != null && bounds.isCloseTo(previous)) {
            stableBoundsCount + 1
        } else {
            1
        }
        previousDetectedBounds = bounds

        if (stableBoundsCount >= 10) {
            lockedFieldBounds = bounds
            fieldBounds = bounds
        }
        invalidate()
    }

    fun setAimModeEnabled(enabled: Boolean) {
        aimModeEnabled = enabled
        if (!enabled) {
            isAiming = false
        }
        invalidate()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!aimModeEnabled) return false

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                touchStartX = event.x
                touchStartY = event.y
                aimX = event.x
                aimY = event.y
                previousAimX = event.x
                previousAimY = event.y
                swipeStartAngle = 0f
                swipeEndAngle = 0f
                isAiming = true
                invalidate()
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                previousAimX = aimX
                previousAimY = aimY
                aimX = event.x
                aimY = event.y
                val fromStartX = aimX - touchStartX
                val fromStartY = aimY - touchStartY
                val segmentX = aimX - previousAimX
                val segmentY = aimY - previousAimY
                if (hypot(fromStartX, fromStartY) > 6f && swipeStartAngle == 0f) {
                    swipeStartAngle = atan2(fromStartY, fromStartX)
                }
                if (hypot(segmentX, segmentY) > 2f) {
                    swipeEndAngle = atan2(segmentY, segmentX)
                }
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

        if (!aimModeEnabled) {
            if (lockedFieldBounds != null && hasLiveBallDetection) {
                drawGoalZone(canvas)
                drawBall(canvas)
            }
            return
        }

        drawGoalZone(canvas)

        if (lockedFieldBounds == null) {
            canvas.drawText("calibrating...", width / 2f, height * 0.18f, textPaint)
        }

        if (isAiming) {
            val shot = currentShot()
            if (shot != null) {
                val result = simulatePhysics(ballX, ballY, shot.dirX, shot.dirY, shot.speed, shot.sidespin)
                drawTrajectory(canvas, result.points, result.outcome)
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
        return Shot(dirX, dirY, initSpeed, deriveSidespin())
    }

    private fun deriveSidespin(): Float {
        if (swipeStartAngle == 0f || swipeEndAngle == 0f) return 0f
        val delta = normalizeAngle(swipeEndAngle - swipeStartAngle)
        return (delta / 1.2f).coerceIn(-1f, 1f)
    }

    fun simulatePhysics(
        startX: Float,
        startY: Float,
        dirX: Float,
        dirY: Float,
        initSpeed: Float,
        sidespin: Float = 0f
    ): SimulationResult {
        val points = mutableListOf<PhysicsPoint>()
        var x = startX
        var y = startY
        var vx = dirX * initSpeed
        var vy = dirY * initSpeed
        var spin = sidespin
        var wallHit = false

        repeat(MAX_STEPS) {
            val speed = hypot(vx, vy)
            if (speed < MIN_SPEED) return SimulationResult(points, TrajectoryOutcome.STOPS_SHORT)

            points.add(PhysicsPoint(x, y, speed))
            vx += spin * 0.012f * speed

            var nx = x + vx * DT
            var ny = y + vy * DT

            if (nx < fieldBounds.left) {
                nx = fieldBounds.left
                vx = -vx * WALL_RESTITUTION
                vy *= 0.96f
                wallHit = true
            } else if (nx > fieldBounds.right) {
                nx = fieldBounds.right
                vx = -vx * WALL_RESTITUTION
                vy *= 0.96f
                wallHit = true
            }

            if (ny < fieldBounds.top) {
                points.add(PhysicsPoint(nx, fieldBounds.top, hypot(vx, vy)))
                val scored = nx in fieldBounds.goalLeft..fieldBounds.goalRight
                return SimulationResult(
                    points,
                    if (scored) TrajectoryOutcome.GOAL else TrajectoryOutcome.WALL_HIT
                )
            } else if (ny > fieldBounds.bottom) {
                ny = fieldBounds.bottom
                vy = -vy * WALL_RESTITUTION
                vx *= 0.96f
                wallHit = true
            }

            x = nx
            y = ny
            vx *= FRICTION
            vy *= FRICTION * FLOOR_FRICTION
            spin *= SPIN_DECAY
        }

        return SimulationResult(
            points,
            if (wallHit) TrajectoryOutcome.WALL_HIT else TrajectoryOutcome.STOPS_SHORT
        )
    }

    private fun drawTrajectory(canvas: Canvas, points: List<PhysicsPoint>, outcome: TrajectoryOutcome) {
        if (points.size < 2) return

        val (red, green, blue) = when (outcome) {
            TrajectoryOutcome.GOAL -> Triple(80, 255, 120)
            TrajectoryOutcome.WALL_HIT -> Triple(255, 200, 50)
            TrajectoryOutcome.STOPS_SHORT -> Triple(255, 80, 80)
        }
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
                paint.color = Color.argb(alpha, red, green, blue)
                paint.style = Paint.Style.FILL
                canvas.drawCircle(point.x, point.y, dotRadius, paint)
                nextDotAt = distance + dotSpacing
            }

            lastX = point.x
            lastY = point.y
        }

        drawArrowhead(canvas, points, red, green, blue)
    }

    private fun drawArrowhead(canvas: Canvas, points: List<PhysicsPoint>, red: Int, green: Int, blue: Int) {
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

        paint.color = Color.argb(230, red, green, blue)
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
        canvas.drawRect(
            fieldBounds.goalLeft,
            fieldBounds.top - 12f,
            fieldBounds.goalRight,
            fieldBounds.top + 22f,
            paint
        )
    }

    private fun drawBall(canvas: Canvas) {
        val radius = width * 0.043f
        if (hasLiveBallDetection) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2f
            paint.color = Color.argb((0.6f * 255).toInt(), 80, 255, 120)
            canvas.drawCircle(ballX, ballY, radius + 8f, paint)
        }

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

    private fun normalizeAngle(angle: Float): Float {
        var normalized = angle
        while (normalized > Math.PI) normalized -= (Math.PI * 2).toFloat()
        while (normalized < -Math.PI) normalized += (Math.PI * 2).toFloat()
        return normalized
    }

    private fun FieldBounds.isCloseTo(other: FieldBounds): Boolean {
        val tolerance = max(8f, width * 0.015f)
        return abs(left - other.left) <= tolerance &&
            abs(right - other.right) <= tolerance &&
            abs(top - other.top) <= tolerance &&
            abs(bottom - other.bottom) <= tolerance &&
            abs(goalLeft - other.goalLeft) <= tolerance &&
            abs(goalRight - other.goalRight) <= tolerance
    }

    private val FieldBounds.width: Float
        get() = right - left

    data class PhysicsPoint(val x: Float, val y: Float, val speed: Float)

    data class SimulationResult(
        val points: List<PhysicsPoint>,
        val outcome: TrajectoryOutcome
    )

    enum class TrajectoryOutcome {
        GOAL,
        WALL_HIT,
        STOPS_SHORT
    }

    private data class Shot(
        val dirX: Float,
        val dirY: Float,
        val speed: Float,
        val sidespin: Float
    )

    companion object {
        const val FRICTION = 0.973f
        const val WALL_RESTITUTION = 0.68f
        const val FLOOR_FRICTION = 0.991f
        const val SPIN_DECAY = 0.95f
        const val MIN_SPEED = 0.3f
        const val DT = 1.0f
        const val MAX_STEPS = 1000
    }
}
