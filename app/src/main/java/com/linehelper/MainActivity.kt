package com.linehelper

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
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.setPadding

class MainActivity : AppCompatActivity() {
    private lateinit var statusText: TextView
    private lateinit var toggleButton: Button
    private var overlayRunning = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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
            text = "⚽ Plato Line Helper"
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
                    if (!Settings.canDrawOverlays(this@MainActivity)) {
                        Toast.makeText(
                            this@MainActivity,
                            "Grant overlay permission first.",
                            Toast.LENGTH_SHORT
                        ).show()
                        return@setOnClickListener
                    }

                    OverlayService.start(this@MainActivity)
                    setOverlayRunning(true)
                }
            }
        }

        val hint = TextView(this).apply {
            text = """
                1. Grant overlay permission.
                2. Tap Start Overlay.
                3. Open Plato Table Soccer.
                4. Drag on the overlay to preview the shot line.
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
        root.addView(hint, matchWidthWrapHeight())

        setContentView(root)
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
