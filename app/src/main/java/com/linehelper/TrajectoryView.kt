package com.linehelper

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.os.SystemClock
import android.view.View
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin

class TrajectoryView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
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
    private var lastDetectedX = 0f
    private var lastDetectedY = 0f
    private var lastDetectedAt = 0L
    private var velocityX = 0f
    private var velocityY = 0f
    private var predictionVisibleUntil = 0L
    private var guideEnabled = true
    private var manualAimActive = false
    private var manualDirX = 0f
    private var manualDirY = -1f
    private var manualPower = 0f

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
        val now = SystemClock.uptimeMillis()
        if (position == null) {
            detectionMisses++
            hasLiveBallDetection = false
            if (detectionMisses > 10) {
                ballX = fallbackBallX
                ballY = fallbackBallY
            }
        } else {
            if (lastDetectedAt > 0L) {
                val frameScale = ((now - lastDetectedAt).coerceAtLeast(1L) / 67f).coerceAtLeast(0.5f)
                val measuredVx = (position.first - lastDetectedX) / frameScale
                val measuredVy = (position.second - lastDetectedY) / frameScale
                velocityX = velocityX * 0.45f + measuredVx * 0.55f
                velocityY = velocityY * 0.45f + measuredVy * 0.55f

                if (hypot(velocityX, velocityY) > MOVING_BALL_SPEED) {
                    predictionVisibleUntil = now + PREDICTION_HOLD_MS
                }
            }

            detectionMisses = 0
            hasLiveBallDetection = true
            ballX = position.first
            ballY = position.second
            fallbackBallX = ballX
            fallbackBallY = ballY
            lastDetectedX = ballX
            lastDetectedY = ballY
            lastDetectedAt = now
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

    fun setGuideEnabled(enabled: Boolean) {
        guideEnabled = enabled
        invalidate()
    }

    fun updateManualAim(dirX: Float, dirY: Float, power: Float, active: Boolean) {
        manualDirX = dirX
        manualDirY = dirY
        manualPower = power
        manualAimActive = active
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!guideEnabled || !hasLiveBallDetection) return

        drawDetectedBallIndicator(canvas)
        if (manualAimActive) {
            val result = simulatePhysics(
                ballX,
                ballY,
                manualDirX,
                manualDirY,
                (6f + manualPower * 22f).coerceIn(6f, 28f)
            )
            drawTrajectory(canvas, result.points, result.outcome)
            drawAimVector(canvas)
        } else if (SystemClock.uptimeMillis() <= predictionVisibleUntil) {
            val result = simulateFromVelocity(ballX, ballY, velocityX, velocityY)
            drawTrajectory(canvas, result.points, result.outcome)
        }
    }

    private fun simulateFromVelocity(startX: Float, startY: Float, vx: Float, vy: Float): SimulationResult {
        val speed = hypot(vx, vy)
        if (speed <= 0.01f) return SimulationResult(emptyList(), TrajectoryOutcome.STOPS_SHORT)
        val initSpeed = (speed * 0.9f).coerceIn(5f, 30f)
        return simulatePhysics(startX, startY, vx / speed, vy / speed, initSpeed)
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

    private fun drawDetectedBallIndicator(canvas: Canvas) {
        val radius = 16f
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2.5f
        paint.color = Color.argb(190, 90, 255, 130)
        canvas.drawCircle(ballX, ballY, radius + 5f, paint)
    }

    private fun drawAimVector(canvas: Canvas) {
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3f
        paint.color = Color.argb(150, 255, 255, 255)
        canvas.drawLine(
            ballX,
            ballY,
            ballX + manualDirX * (45f + manualPower * 60f),
            ballY + manualDirY * (45f + manualPower * 60f),
            paint
        )
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

    companion object {
        const val FRICTION = 0.973f
        const val WALL_RESTITUTION = 0.68f
        const val FLOOR_FRICTION = 0.991f
        const val SPIN_DECAY = 0.95f
        const val MIN_SPEED = 0.3f
        const val DT = 1.0f
        const val MAX_STEPS = 1000
        private const val MOVING_BALL_SPEED = 2.0f
        private const val PREDICTION_HOLD_MS = 1300L
    }
}
