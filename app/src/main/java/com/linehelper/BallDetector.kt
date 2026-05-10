package com.linehelper

import android.graphics.Bitmap
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.Point
import org.opencv.core.Scalar
import org.opencv.imgproc.Imgproc
import kotlin.math.abs
import kotlin.math.max

class BallDetector {
    fun detect(bitmap: Bitmap): Pair<Float, Float>? {
        val rgba = Mat()
        val rgb = Mat()
        val hsv = Mat()
        val mask = Mat()
        val blurred = Mat()
        val circles = Mat()

        return try {
            Utils.bitmapToMat(bitmap, rgba)
            Imgproc.cvtColor(rgba, rgb, Imgproc.COLOR_RGBA2RGB)
            Imgproc.cvtColor(rgb, hsv, Imgproc.COLOR_RGB2HSV)

            Core.inRange(
                hsv,
                Scalar(0.0, 0.0, 168.0),
                Scalar(180.0, 80.0, 255.0),
                mask
            )
            Imgproc.medianBlur(mask, blurred, 5)
            Imgproc.HoughCircles(
                blurred,
                circles,
                Imgproc.HOUGH_GRADIENT,
                1.2,
                60.0,
                120.0,
                18.0,
                15,
                32
            )

            var best: CircleCandidate? = null
            for (i in 0 until circles.cols()) {
                val circle = circles.get(0, i) ?: continue
                if (circle.size < 3) continue

                val cx = circle[0]
                val cy = circle[1]
                val radius = circle[2]
                val confidence = circleMaskConfidence(mask, cx, cy, radius)
                if (confidence <= 0.7) continue

                val darkRatio = darkPatchRatio(hsv, cx, cy, radius)
                val radiusScore = 1.0 - (abs(radius - 18.0) / 18.0).coerceIn(0.0, 1.0)
                val score = confidence * 0.45 + darkRatio * 0.4 + radiusScore * 0.15
                if (score <= 0.58) continue

                val candidate = CircleCandidate(cx.toFloat(), cy.toFloat(), score)
                if (best == null || candidate.score > best.score) {
                    best = candidate
                }
            }

            best?.let { it.x to it.y }
        } finally {
            rgba.release()
            rgb.release()
            hsv.release()
            mask.release()
            blurred.release()
            circles.release()
        }
    }

    private fun darkPatchRatio(hsv: Mat, cx: Double, cy: Double, radius: Double): Double {
        var darkPixels = 0
        var samplePixels = 0
        val left = (cx - radius * 0.72).toInt().coerceAtLeast(0)
        val right = (cx + radius * 0.72).toInt().coerceAtMost(hsv.cols() - 1)
        val top = (cy - radius * 0.72).toInt().coerceAtLeast(0)
        val bottom = (cy + radius * 0.72).toInt().coerceAtMost(hsv.rows() - 1)
        val radiusSquared = radius * radius * 0.52

        for (y in top..bottom step 2) {
            for (x in left..right step 2) {
                val dx = x - cx
                val dy = y - cy
                if (dx * dx + dy * dy > radiusSquared) continue

                val pixel = hsv.get(y, x) ?: continue
                samplePixels++
                val saturation = pixel[1]
                val value = pixel[2]
                if (value < 95.0 && saturation < 110.0) {
                    darkPixels++
                }
            }
        }

        if (samplePixels == 0) return 0.0
        return (darkPixels.toDouble() / samplePixels).coerceIn(0.0, 1.0)
    }

    private fun circleMaskConfidence(mask: Mat, cx: Double, cy: Double, radius: Double): Double {
        val circleMask = Mat.zeros(mask.size(), mask.type())
        val intersection = Mat()

        return try {
            Imgproc.circle(
                circleMask,
                Point(cx, cy),
                max(1.0, radius * 0.82).toInt(),
                Scalar(255.0),
                -1
            )
            Core.bitwise_and(mask, circleMask, intersection)
            val supportedPixels = Core.countNonZero(intersection).toDouble()
            val circlePixels = max(1.0, Core.countNonZero(circleMask).toDouble())
            supportedPixels / circlePixels
        } finally {
            circleMask.release()
            intersection.release()
        }
    }

    private data class CircleCandidate(
        val x: Float,
        val y: Float,
        val score: Double
    )
}
