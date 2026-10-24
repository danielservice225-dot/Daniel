package com.daniel.aura

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import android.provider.Settings
import android.webkit.JavascriptInterface
import android.widget.Toast

/**
 * Small, explicit Android action bridge for the trusted Daniel Aura WebView.
 * Actions that affect other apps are opened through Android intents; Android or
 * the target app remains responsible for confirmations and permissions.
 */
class AndroidAssistantBridge(private val activity: Activity) {
    @JavascriptInterface
    fun openGoogleAssistant(): Boolean = onUi {
        val assist = Intent(Intent.ACTION_ASSIST).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            activity.startActivity(assist)
        } catch (_: Exception) {
            val google = activity.packageManager.getLaunchIntentForPackage("com.google.android.googlequicksearchbox")
            if (google != null) activity.startActivity(google)
            else Toast.makeText(activity, "Assistant Google n’est pas disponible sur ce téléphone.", Toast.LENGTH_LONG).show()
        }
    }

    @JavascriptInterface
    fun openAccessibilitySettings() = onUi {
        activity.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    @JavascriptInterface
    fun openAppSettings() = onUi {
        activity.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${activity.packageName}")))
    }

    @JavascriptInterface
    fun openApp(packageName: String?): Boolean = onUi {
        val safePackage = packageName?.trim().orEmpty()
        if (safePackage.isBlank() || safePackage.length > 180 || safePackage.any { !(it.isLetterOrDigit() || it == '.' || it == '_') }) {
            Toast.makeText(activity, "Nom d’application invalide.", Toast.LENGTH_SHORT).show()
        } else {
            val launch = activity.packageManager.getLaunchIntentForPackage(safePackage)
            if (launch != null) activity.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            else Toast.makeText(activity, "Cette application n’est pas installée ou ne peut pas être ouverte.", Toast.LENGTH_LONG).show()
        }
    }

    @JavascriptInterface
    fun dialNumber(number: String?) = onUi {
        val value = number?.trim().orEmpty()
        if (value.isNotBlank() && value.length <= 40) {
            activity.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(value))))
        }
    }

    @JavascriptInterface
    fun searchMaps(query: String?) = onUi {
        val value = query?.trim().orEmpty()
        if (value.isNotBlank() && value.length <= 300) {
            activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/search/?api=1&query=" + Uri.encode(value))))
        }
    }

    @JavascriptInterface
    fun setAlarm(hour: Int, minute: Int, label: String?) = onUi {
        if (hour in 0..23 && minute in 0..59) {
            val intent = Intent(AlarmClock.ACTION_SET_ALARM)
                .putExtra(AlarmClock.EXTRA_HOUR, hour)
                .putExtra(AlarmClock.EXTRA_MINUTES, minute)
                .putExtra(AlarmClock.EXTRA_MESSAGE, label?.take(100) ?: "Daniel Aura")
                .putExtra(AlarmClock.EXTRA_SKIP_UI, false)
            activity.startActivity(intent)
        }
    }

    @JavascriptInterface
    fun systemCommand(command: String?) = onUi {
        val allowed = setOf("back", "home", "notifications")
        val value = command?.lowercase()?.trim()
        if (value in allowed) {
            val intent = Intent("com.daniel.aura.SYSTEM_COMMAND")
                .setPackage(activity.packageName)
                .putExtra("command", value)
            activity.sendBroadcast(intent)
        }
    }

    private fun onUi(action: () -> Unit) {
        activity.runOnUiThread {
            try { action() } catch (_: Exception) {
                Toast.makeText(activity, "Cette action n’est pas disponible sur cet appareil.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun onUi(action: () -> Unit): Boolean {
        activity.runOnUiThread {
            try { action() } catch (_: Exception) {
                Toast.makeText(activity, "Cette action n’est pas disponible sur cet appareil.", Toast.LENGTH_SHORT).show()
            }
        }
        return true
    }
}