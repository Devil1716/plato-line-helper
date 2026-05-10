package com.linehelper

import android.app.Activity
import android.media.projection.MediaProjectionManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.setPadding

class MainActivity : AppCompatActivity() {
    private companion object {
        private const val LATEST_RELEASE_URL =
            "https://github.com/Devil1716/plato-line-helper/releases/latest"
    }

    private lateinit var statusText: TextView
    private lateinit var toggleButton: Button
    private lateinit var mediaProjectionLauncher: ActivityResultLauncher<Intent>
    private var overlayRunning = false
    private var shouldPromptForCaptureOnResume = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        mediaProjectionLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                OverlayService.start(this, result.resultCode, result.data!!)
                setOverlayRunning(true)
            } else {
                Toast.makeText(this, "Screen capture permission is required.", Toast.LENGTH_SHORT).show()
                setOverlayRunning(false)
            }
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(36)
            setBackgroundColor(Color.rgb(18, 24, 31))
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val title = TextView(this).apply {
            text = "Plato Line Helper"
            textSize = 28f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }

        statusText = TextView(this).apply {
            text = "Overlay: OFF"
            textSize = 18f
            setTextColor(Color.rgb(190, 204, 214))
            gravity = Gravity.CENTER
            setPadding(0, 36, 0, 24)
        }

        val permissionButton = Button(this).apply {
            text = "Grant Overlay Permission"
            setOnClickListener {
                shouldPromptForCaptureOnResume = true
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivity(intent)
            }
        }

        toggleButton = Button(this).apply {
            text = "Start Overlay"
            setOnClickListener {
                if (overlayRunning) {
                    OverlayService.stop(this@MainActivity)
                    setOverlayRunning(false)
                } else {
                    startOverlayWithPermissions()
                }
            }
        }

        val updateButton = Button(this).apply {
            text = "Check for Updates"
            setOnClickListener {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(LATEST_RELEASE_URL)))
            }
        }

        val hint = TextView(this).apply {
            text = """
                1. Grant overlay permission.
                2. Approve screen capture for this session.
                3. Open Plato Table Soccer.
                4. Drag to preview a shot using live ball and field detection.
                5. Use Check for Updates to download the newest APK.
            """.trimIndent()
            textSize = 16f
            setTextColor(Color.rgb(214, 224, 232))
            setLineSpacing(4f, 1f)
            setPadding(0, 42, 0, 0)
        }

        root.addView(title, matchWidthWrapHeight())
        root.addView(statusText, matchWidthWrapHeight())
        root.addView(permissionButton, matchWidthWrapHeight())
        root.addView(toggleButton, matchWidthWrapHeight())
        root.addView(updateButton, matchWidthWrapHeight())
        root.addView(hint, matchWidthWrapHeight())

        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        if (shouldPromptForCaptureOnResume && Settings.canDrawOverlays(this)) {
            shouldPromptForCaptureOnResume = false
            requestMediaProjection()
        }
    }

    private fun startOverlayWithPermissions() {
        if (!Settings.canDrawOverlays(this)) {
            shouldPromptForCaptureOnResume = true
            Toast.makeText(this, "Grant overlay permission first.", Toast.LENGTH_SHORT).show()
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
            return
        }

        requestMediaProjection()
    }

    private fun requestMediaProjection() {
        val projectionManager =
            getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjectionLauncher.launch(projectionManager.createScreenCaptureIntent())
    }

    private fun setOverlayRunning(running: Boolean) {
        overlayRunning = running
        statusText.text = if (running) "Overlay: ON" else "Overlay: OFF"
        toggleButton.text = if (running) "Stop Overlay" else "Start Overlay"
    }

    private fun matchWidthWrapHeight(): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = 12
        }
    }
}
