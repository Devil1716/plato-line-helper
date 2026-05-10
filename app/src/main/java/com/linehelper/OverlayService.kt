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
    private var captureManager: ScreenCaptureManager? = null
    private var aimModeEnabled = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        addOverlay()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_ENABLE_AIM -> {
                setAimModeEnabled(true)
                return START_STICKY
            }

            ACTION_DISABLE_AIM -> {
                setAimModeEnabled(false)
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
        trajectoryView = null
        controlBubbleView = null
        overlayParams = null
        controlBubbleParams = null
        windowManager = null
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun addOverlay() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        trajectoryView = TrajectoryView(this) {
            setAimModeEnabled(false)
        }.apply {
            setAimModeEnabled(aimModeEnabled)
        }

        overlayParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            overlayType(),
            overlayFlags(),
            PixelFormat.TRANSLUCENT
        )

        windowManager?.addView(trajectoryView, overlayParams)
        addControlBubble()
    }

    private fun setAimModeEnabled(enabled: Boolean) {
        aimModeEnabled = enabled
        trajectoryView?.setAimModeEnabled(enabled)
        controlBubbleView?.setAimModeEnabled(enabled)

        val params = overlayParams ?: return
        params.flags = overlayFlags()
        trajectoryView?.let { view ->
            runCatching { windowManager?.updateViewLayout(view, params) }
        }
        updateNotification()
    }

    private fun addControlBubble() {
        controlBubbleView = ControlBubbleView(
            context = this,
            onAimToggle = { setAimModeEnabled(!aimModeEnabled) },
            onStop = { stopSelf() },
            onDrag = { dx, dy -> moveControlBubble(dx, dy) }
        ).apply {
            setAimModeEnabled(aimModeEnabled)
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
            x = 12
            y = 720
        }

        windowManager?.addView(controlBubbleView, controlBubbleParams)
    }

    private fun moveControlBubble(dx: Int, dy: Int) {
        val params = controlBubbleParams ?: return
        params.x = (params.x + dx).coerceIn(0, resources.displayMetrics.widthPixels - 70)
        params.y = (params.y + dy).coerceIn(60, resources.displayMetrics.heightPixels - 180)
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

    private fun overlayFlags(): Int {
        var flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN

        if (!aimModeEnabled) {
            flags = flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        }

        return flags
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
        val aimAction = if (aimModeEnabled) {
            NotificationCompat.Action(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Disable Aim Mode",
                servicePendingIntent(ACTION_DISABLE_AIM, 1)
            )
        } else {
            NotificationCompat.Action(
                android.R.drawable.ic_menu_compass,
                "Enable Aim Mode",
                servicePendingIntent(ACTION_ENABLE_AIM, 2)
            )
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentTitle("Line Helper Active")
            .setContentText(
                if (aimModeEnabled) {
                    "Aim Mode ON: overlay captures drag gestures"
                } else {
                    "Pass-through ON: Plato taps work normally"
                }
            )
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .addAction(aimAction)
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
        private const val ACTION_ENABLE_AIM = "com.linehelper.action.ENABLE_AIM"
        private const val ACTION_DISABLE_AIM = "com.linehelper.action.DISABLE_AIM"
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
