package com.daniel.aura

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.view.accessibility.AccessibilityEvent

class DanielAccessibilityService : AccessibilityService() {
    private val commandReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.getStringExtra("command")) {
                "back" -> performGlobalAction(GLOBAL_ACTION_BACK)
                "home" -> performGlobalAction(GLOBAL_ACTION_HOME)
                "notifications" -> performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
            }
        }
    }
    private var receiverRegistered = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        val filter = IntentFilter("com.daniel.aura.SYSTEM_COMMAND")
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(commandReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        else @Suppress("DEPRECATION") registerReceiver(commandReceiver, filter)
        receiverRegistered = true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) { /* No screen content is collected. */ }
    override fun onInterrupt() {}

    override fun onDestroy() {
        if (receiverRegistered) {
            try { unregisterReceiver(commandReceiver) } catch (_: Exception) {}
        }
        super.onDestroy()
    }
}