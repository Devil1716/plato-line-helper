package com.linehelper

import android.graphics.Bitmap
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.Point
import org.opencv.core.Scalar
import org.opencv.imgproc.Imgproc
import kotlin.math.PI
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
                45
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

                val area = PI * radius * radius
                val candidate = CircleCandidate(cx.toFloat(), cy.toFloat(), confidence, area)
                if (best == null || candidate.area > best.area) {
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
        val confidence: Double,
        val area: Double
    )
}
