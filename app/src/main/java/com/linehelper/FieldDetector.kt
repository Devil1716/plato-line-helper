package com.linehelper

import android.graphics.Bitmap
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.Rect
import org.opencv.core.Scalar
import org.opencv.imgproc.Imgproc
import kotlin.math.max

class FieldDetector {
    private var frameIndex = 0
    private var cachedBounds: FieldBounds? = null

    fun detect(bitmap: Bitmap): FieldBounds? {
        frameIndex++
        cachedBounds?.let { cached ->
            if (frameIndex % REDETECT_INTERVAL != 0) return cached
        }

        val rgba = Mat()
        val rgb = Mat()
        val hsv = Mat()
        val greenMask = Mat()
        val whiteMask = Mat()
        val contours = mutableListOf<MatOfPoint>()
        val hierarchy = Mat()

        return try {
            Utils.bitmapToMat(bitmap, rgba)
            Imgproc.cvtColor(rgba, rgb, Imgproc.COLOR_RGBA2RGB)
            Imgproc.cvtColor(rgb, hsv, Imgproc.COLOR_RGB2HSV)

            Core.inRange(
                hsv,
                Scalar(35.0, 40.0, 40.0),
                Scalar(85.0, 255.0, 200.0),
                greenMask
            )
            Imgproc.morphologyEx(
                greenMask,
                greenMask,
                Imgproc.MORPH_CLOSE,
                Imgproc.getStructuringElement(Imgproc.MORPH_RECT, org.opencv.core.Size(11.0, 11.0))
            )
            Imgproc.findContours(
                greenMask,
                contours,
                hierarchy,
                Imgproc.RETR_EXTERNAL,
                Imgproc.CHAIN_APPROX_SIMPLE
            )

            val fieldRect = contours
                .map { Imgproc.boundingRect(it) to Imgproc.contourArea(it) }
                .maxByOrNull { it.second }
                ?.first
                ?: return cachedBounds

            Core.inRange(
                hsv,
                Scalar(0.0, 0.0, 175.0),
                Scalar(180.0, 70.0, 255.0),
                whiteMask
            )

            val goal = detectGoalMouth(whiteMask, fieldRect)
            val bounds = FieldBounds(
                left = fieldRect.x.toFloat(),
                right = (fieldRect.x + fieldRect.width).toFloat(),
                top = fieldRect.y.toFloat(),
                bottom = (fieldRect.y + fieldRect.height).toFloat(),
                goalLeft = goal.first,
                goalRight = goal.second
            )
            cachedBounds = bounds
            bounds
        } finally {
            contours.forEach { it.release() }
            rgba.release()
            rgb.release()
            hsv.release()
            greenMask.release()
            whiteMask.release()
            hierarchy.release()
        }
    }

    private fun detectGoalMouth(whiteMask: Mat, fieldRect: Rect): Pair<Float, Float> {
        val yStart = fieldRect.y.coerceAtLeast(0)
        val yEnd = (fieldRect.y + max(8, fieldRect.height / 18)).coerceAtMost(whiteMask.rows())
        var bestStart = fieldRect.x
        var bestEnd = fieldRect.x + fieldRect.width
        var bestLength = 0

        for (y in yStart until yEnd) {
            var runStart = -1
            for (x in fieldRect.x until fieldRect.x + fieldRect.width) {
                val isWhite = (whiteMask.get(y, x)?.firstOrNull() ?: 0.0) > 0.0
                if (isWhite && runStart < 0) {
                    runStart = x
                } else if (!isWhite && runStart >= 0) {
                    val length = x - runStart
                    if (length > bestLength) {
                        bestLength = length
                        bestStart = runStart
                        bestEnd = x
                    }
                    runStart = -1
                }
            }

            if (runStart >= 0) {
                val end = fieldRect.x + fieldRect.width
                val length = end - runStart
                if (length > bestLength) {
                    bestLength = length
                    bestStart = runStart
                    bestEnd = end
                }
            }
        }

        if (bestLength < fieldRect.width * 0.12f) {
            val goalWidth = fieldRect.width * 0.32f
            val center = fieldRect.x + fieldRect.width / 2f
            return center - goalWidth / 2f to center + goalWidth / 2f
        }

        return bestStart.toFloat() to bestEnd.toFloat()
    }

    companion object {
        private const val REDETECT_INTERVAL = 90
    }
}

data class FieldBounds(
    val left: Float,
    val right: Float,
    val top: Float,
    val bottom: Float,
    val goalLeft: Float,
    val goalRight: Float
)
