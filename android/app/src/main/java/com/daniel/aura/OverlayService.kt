package com.daniel.aura

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import kotlin.math.abs

class OverlayService : Service() {
    private var windowManager: WindowManager? = null
    private var bubble: TextView? = null
    private var params: WindowManager.LayoutParams? = null
    private val channelId = "daniel_floating_assistant"

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        val notification = Notification.Builder(this, channelId)
            .setContentTitle("Daniel — Aura IA est actif")
            .setContentText("Touchez le raccourci flottant pour ouvrir Daniel.")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .build()
        startForeground(41, notification)
        if (Settings.canDrawOverlays(this)) showBubble()
    }

    private fun showBubble() {
        if (bubble != null) return
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val density = resources.displayMetrics.density
        val view = TextView(this).apply {
            text = "D"
            textSize = 21f
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.rgb(37, 99, 235), Color.rgb(124, 108, 240), Color.rgb(34, 229, 139))
            ).apply {
                shape = GradientDrawable.OVAL
                setStroke((1.5f * density).toInt(), Color.argb(190, 255, 255, 255))
            }
            contentDescription = "Ouvrir Daniel Aura IA"
            elevation = 18f * density
        }
        val layout = WindowManager.LayoutParams(
            (58 * density).toInt(),
            (58 * density).toInt(),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = (12 * density).toInt()
            y = (resources.displayMetrics.heightPixels * 0.38f).toInt()
        }
        view.setOnTouchListener(object : View.OnTouchListener {
            private var startX = 0
            private var startY = 0
            private var touchX = 0f
            private var touchY = 0f
            private var moved = false
            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        startX = layout.x
                        startY = layout.y
                        touchX = event.rawX
                        touchY = event.rawY
                        moved = false
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (touchX - event.rawX).toInt()
                        val dy = (touchY - event.rawY).toInt()
                        if (abs(dx) > 5 || abs(dy) > 5) moved = true
                        layout.x = (startX + dx).coerceAtLeast(0)
                        layout.y = (startY + dy).coerceAtLeast(0)
                        try { windowManager?.updateViewLayout(v, layout) } catch (_: Exception) {}
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (!moved) {
                            val launch = packageManager.getLaunchIntentForPackage(packageName)
                            launch?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                            if (launch != null) startActivity(launch)
                        }
                        return true
                    }
                }
                return false
            }
        })
        bubble = view
        params = layout
        try { windowManager?.addView(view, layout) } catch (_: Exception) { bubble = null; params = null }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Raccourci flottant Daniel", NotificationManager.IMPORTANCE_LOW)
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        try { if (bubble != null) windowManager?.removeView(bubble) } catch (_: Exception) {}
        bubble = null
        params = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}