package com.linehelper

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class OverlayService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var windowManager: WindowManager? = null
    private var trajectoryView: TrajectoryView? = null
    private var overlayParams: WindowManager.LayoutParams? = null
    private var controlBubbleView: ControlBubbleView? = null
    private var controlBubbleParams: WindowManager.LayoutParams? = null
    private var aimPadView: AimPadView? = null
    private var aimPadParams: WindowManager.LayoutParams? = null
    private var captureManager: ScreenCaptureManager? = null
    private var guideEnabled = true

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        addOverlay()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_ENABLE_GUIDE -> {
                setGuideEnabled(true)
                return START_STICKY
            }

            ACTION_DISABLE_GUIDE -> {
                setGuideEnabled(false)
                return START_STICKY
            }

            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
        }

        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, 0) ?: 0
        val data = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent?.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent?.getParcelableExtra(EXTRA_RESULT_DATA)
        }

        if (resultCode != 0 && data != null && captureManager == null) {
            startScreenCapture(resultCode, data)
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        captureManager?.stop()
        captureManager = null
        trajectoryView?.let { view ->
            runCatching { windowManager?.removeView(view) }
        }
        controlBubbleView?.let { view ->
            runCatching { windowManager?.removeView(view) }
        }
        aimPadView?.let { view ->
            runCatching { windowManager?.removeView(view) }
        }
        trajectoryView = null
        controlBubbleView = null
        aimPadView = null
        overlayParams = null
        controlBubbleParams = null
        aimPadParams = null
        windowManager = null
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun addOverlay() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        trajectoryView = TrajectoryView(this).apply {
            setGuideEnabled(guideEnabled)
        }

        overlayParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            overlayType(),
            drawOnlyOverlayFlags(),
            PixelFormat.TRANSLUCENT
        )

        windowManager?.addView(trajectoryView, overlayParams)
        addControlBubble()
        addAimPad()
    }

    private fun setGuideEnabled(enabled: Boolean) {
        guideEnabled = enabled
        trajectoryView?.setGuideEnabled(enabled)
        controlBubbleView?.setGuideEnabled(enabled)
        updateNotification()
    }

    private fun addAimPad() {
        aimPadView = AimPadView(this) { dirX, dirY, power, active ->
            trajectoryView?.updateManualAim(dirX, dirY, power, active)
        }

        aimPadParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 12
            y = (resources.displayMetrics.heightPixels * 0.68f).toInt()
        }

        windowManager?.addView(aimPadView, aimPadParams)
    }

    private fun addControlBubble() {
        controlBubbleView = ControlBubbleView(
            context = this,
            onGuideToggle = { setGuideEnabled(!guideEnabled) },
            onDrag = { dx, dy -> moveControlBubble(dx, dy) }
        ).apply {
            setGuideEnabled(guideEnabled)
        }

        controlBubbleParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = resources.displayMetrics.widthPixels - 56
            y = (resources.displayMetrics.heightPixels * 0.45f).toInt()
        }

        windowManager?.addView(controlBubbleView, controlBubbleParams)
    }

    private fun moveControlBubble(dx: Int, dy: Int) {
        val params = controlBubbleParams ?: return
        params.x = (params.x + dx).coerceIn(0, resources.displayMetrics.widthPixels - 48)
        params.y = (params.y + dy).coerceIn(70, resources.displayMetrics.heightPixels - 120)
        controlBubbleView?.let { view ->
            runCatching { windowManager?.updateViewLayout(view, params) }
        }
    }

    private fun overlayType(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
    }

    private fun drawOnlyOverlayFlags(): Int {
        return WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
    }

    private fun startScreenCapture(resultCode: Int, data: Intent) {
        val manager = ScreenCaptureManager(this, resultCode, data, serviceScope)
        captureManager = manager
        manager.start()

        serviceScope.launch {
            manager.ballPosition.collectLatest { position ->
                trajectoryView?.updateDetectedBall(position)
            }
        }
        serviceScope.launch {
            manager.fieldBounds.collectLatest { bounds ->
                bounds?.let {
                    trajectoryView?.updateDetectedField(it)
                }
            }
        }
    }

    private fun buildNotification(): Notification {
        val guideAction = if (guideEnabled) {
            NotificationCompat.Action(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Hide Guide",
                servicePendingIntent(ACTION_DISABLE_GUIDE, 1)
            )
        } else {
            NotificationCompat.Action(
                android.R.drawable.ic_menu_compass,
                "Show Guide",
                servicePendingIntent(ACTION_ENABLE_GUIDE, 2)
            )
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentTitle("Line Helper Active")
            .setContentText(
                if (guideEnabled) {
                    "Pass-through guide ON: predicts after the ball moves"
                } else {
                    "Guide hidden: Plato taps work normally"
                }
            )
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .addAction(guideAction)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Stop",
                servicePendingIntent(ACTION_STOP, 3)
            )
            .build()
    }

    private fun updateNotification() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification())
    }

    private fun servicePendingIntent(action: String, requestCode: Int): PendingIntent {
        val intent = Intent(this, OverlayService::class.java).apply {
            this.action = action
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getService(this, requestCode, intent, flags)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Line Helper",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        private const val CHANNEL_ID = "LineHelperChannel"
        private const val NOTIFICATION_ID = 1001
        private const val EXTRA_RESULT_CODE = "extra_result_code"
        private const val EXTRA_RESULT_DATA = "extra_result_data"
        private const val ACTION_ENABLE_GUIDE = "com.linehelper.action.ENABLE_GUIDE"
        private const val ACTION_DISABLE_GUIDE = "com.linehelper.action.DISABLE_GUIDE"
        private const val ACTION_STOP = "com.linehelper.action.STOP"

        fun start(context: Context, resultCode: Int, data: Intent) {
            val intent = Intent(context, OverlayService::class.java).apply {
                putExtra(EXTRA_RESULT_CODE, resultCode)
                putExtra(EXTRA_RESULT_DATA, data)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, OverlayService::class.java))
        }
    }
}
