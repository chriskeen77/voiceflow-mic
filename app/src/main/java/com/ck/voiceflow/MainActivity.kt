package com.ck.voiceflow

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {

    private lateinit var micStatus: TextView
    private lateinit var serviceStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val pad = (16 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad * 2, pad, pad)
            gravity = Gravity.CENTER_HORIZONTAL
        }

        root.addView(TextView(this).apply {
            text = "VoiceFlow Mic"
            textSize = 26f
            setTextColor(Color.parseColor("#0AA2C2"))
            gravity = Gravity.CENTER
        })
        root.addView(TextView(this).apply {
            text = "Floating dictation bubble for any text field.\nDo both steps below, then tap into any text box."
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(0, pad / 2, 0, pad)
        })

        micStatus = TextView(this).apply { textSize = 15f; setPadding(0, pad, 0, 4) }
        root.addView(micStatus)
        root.addView(Button(this).apply {
            text = "1. Grant microphone"
            setOnClickListener {
                requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1)
            }
        })

        serviceStatus = TextView(this).apply { textSize = 15f; setPadding(0, pad, 0, 4) }
        root.addView(serviceStatus)
        root.addView(Button(this).apply {
            text = "2. Enable floating mic"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        })

        root.addView(TextView(this).apply {
            text = "\nHow to use:\n• Tap into a text field → teal bubble appears\n• Tap bubble to dictate (turns red)\n• Tap again to stop — cleaned text is typed in\n• Say \"scratch that\" to drop the last sentence\n• Drag the bubble if it's in the way"
            textSize = 14f
            setPadding(0, pad, 0, 0)
        })

        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        val micOk = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        micStatus.text = if (micOk) "✅ Microphone granted" else "❌ Microphone not granted"
        val svcOk = isServiceEnabled(this)
        serviceStatus.text = if (svcOk) "✅ Floating mic enabled" else "❌ Floating mic OFF — enable in Accessibility"
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(code, perms, results)
        onResume()
    }

    companion object {
        fun isServiceEnabled(ctx: Context): Boolean {
            val enabled = Settings.Secure.getString(
                ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            return enabled.contains(ctx.packageName + "/")
        }
    }
}
