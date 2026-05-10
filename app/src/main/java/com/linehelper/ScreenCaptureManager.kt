package com.linehelper

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.HandlerThread
import android.view.WindowManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.opencv.android.OpenCVLoader
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean

class ScreenCaptureManager(
    private val context: Context,
    private val resultCode: Int,
    private val data: Intent,
    private val scope: CoroutineScope
) {
    private val ballDetector = BallDetector()
    private val fieldDetector = FieldDetector()
    private val processing = AtomicBoolean(false)
    private val stopped = AtomicBoolean(false)

    private var projection: MediaProjection? = null
    private var projectionCallback: MediaProjection.Callback? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var handlerThread: HandlerThread? = null
    private var lastFrameAt = 0L
    private var densityDpi = 1
    private var captureWidth = 1
    private var captureHeight = 1

    private val _ballPosition = MutableStateFlow<Pair<Float, Float>?>(null)
    val ballPosition: StateFlow<Pair<Float, Float>?> = _ballPosition

    private val _fieldBounds = MutableStateFlow<FieldBounds?>(null)
    val fieldBounds: StateFlow<FieldBounds?> = _fieldBounds

    fun start() {
        stopped.set(false)
        if (!OpenCVLoader.initDebug()) return

        val windowManager = context.getSystemService(WindowManager::class.java)
        val metrics = windowManager.currentWindowMetrics
        val bounds = metrics.bounds
        captureWidth = bounds.width().coerceAtLeast(1)
        captureHeight = bounds.height().coerceAtLeast(1)
        densityDpi = context.resources.displayMetrics.densityDpi

        val projectionManager =
            context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projection = projectionManager.getMediaProjection(resultCode, data)

        handlerThread = HandlerThread("line-helper-capture").also { it.start() }
        val handler = Handler(handlerThread!!.looper)
        projectionCallback = object : MediaProjection.Callback() {
            override fun onStop() {
                stop()
            }
        }
        projection?.registerCallback(projectionCallback!!, handler)

        imageReader = ImageReader.newInstance(captureWidth, captureHeight, PixelFormat.RGBA_8888, 2)
        imageReader?.setOnImageAvailableListener({ reader ->
            val now = System.currentTimeMillis()
            if (now - lastFrameAt < FRAME_INTERVAL_MS || processing.get()) {
                reader.acquireLatestImage()?.close()
                return@setOnImageAvailableListener
            }

            val image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener
            lastFrameAt = now
            val bitmap = image.toBitmap()
            image.close()

            processing.set(true)
            scope.launch(Dispatchers.Default) {
                try {
                    _ballPosition.value = ballDetector.detect(bitmap)
                    _fieldBounds.value = fieldDetector.detect(bitmap)
                } finally {
                    bitmap.recycle()
                    processing.set(false)
                }
            }
        }, handler)

        virtualDisplay = projection?.createVirtualDisplay(
            "plato-line-helper-capture",
            captureWidth,
            captureHeight,
            densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface,
            null,
            handler
        )
    }

    fun stop() {
        if (!stopped.compareAndSet(false, true)) return

        virtualDisplay?.release()
        imageReader?.close()
        projectionCallback?.let { callback ->
            runCatching { projection?.unregisterCallback(callback) }
        }
        runCatching { projection?.stop() }
        handlerThread?.quitSafely()
        virtualDisplay = null
        imageReader = null
        projection = null
        projectionCallback = null
        handlerThread = null
    }

    private fun Image.toBitmap(): Bitmap {
        val plane = planes[0]
        val buffer: ByteBuffer = plane.buffer
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val rowPadding = rowStride - pixelStride * width
        val paddedWidth = width + rowPadding / pixelStride
        val paddedBitmap = Bitmap.createBitmap(paddedWidth, height, Bitmap.Config.ARGB_8888)
        paddedBitmap.copyPixelsFromBuffer(buffer)

        val cropped = Bitmap.createBitmap(paddedBitmap, 0, 0, width, height)
        if (cropped !== paddedBitmap) paddedBitmap.recycle()
        return cropped
    }

    companion object {
        private const val FRAME_INTERVAL_MS = 67L
    }
}
