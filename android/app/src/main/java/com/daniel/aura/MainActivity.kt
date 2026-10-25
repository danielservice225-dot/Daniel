package com.daniel.aura

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.graphics.drawable.GradientDrawable
import android.provider.Settings
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import android.os.Build
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognizerIntent
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import org.json.JSONObject
import java.util.Locale

class MainActivity : Activity() {
    private lateinit var webView: WebView
    private val website = "https://daniel-aura-api.onrender.com/"
    private val mediaPermissionRequest = 420
    private val nativeSpeechPermissionRequest = 421
    private var pendingWebPermission: PermissionRequest? = null
    private var pendingNativeLanguage = "fr-FR"
    private var nativeSpeechPending = false
    private val nativeSpeechResultRequest = 422
    private var shortcutButton: TextView? = null
    private val shortcutPrefs by lazy { getSharedPreferences("daniel_aura_app", MODE_PRIVATE) }
    private var waitingForOverlayPermission = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(7, 8, 13)
        window.navigationBarColor = android.graphics.Color.rgb(7, 8, 13)
        window.decorView.systemUiVisibility = 0

        webView = WebView(this).apply {
            setBackgroundColor(android.graphics.Color.rgb(7, 8, 13))
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.databaseEnabled = true
            settings.loadsImagesAutomatically = true
            settings.javaScriptCanOpenWindowsAutomatically = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.setSupportMultipleWindows(false)
            settings.allowFileAccess = false
            settings.allowContentAccess = true
            addJavascriptInterface(NativeSpeechBridge(), "DanielNativeSpeech")
            addJavascriptInterface(AndroidAssistantBridge(this@MainActivity), "DanielAndroid")

            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    if (url?.startsWith(website) == true) {
                        view?.evaluateJavascript("document.documentElement.style.setProperty('--bg','#080d19');document.documentElement.style.setProperty('--bg-deep','#050914');document.documentElement.style.setProperty('--violet','#7c6cf0');document.documentElement.style.setProperty('--cyan','#38bdf8');document.documentElement.style.setProperty('--mint','#22e58b');document.body.style.background='radial-gradient(circle at 50% -10%,rgba(37,99,235,.18),transparent 42%),#080d19';document.documentElement.classList.add('daniel-android-app');", null)
                        view?.evaluateJavascript("""(function() {
                          if (window.__danielNativeOpenPatched) return;
                          window.__danielNativeOpenPatched = true;
                          var nativeOpen = window.open.bind(window);
                          var apps = [
                            [/youtube\.com/i, "com.google.android.youtube"],
                            [/open\.spotify\.com|spotify\.com/i, "com.spotify.music"],
                            [/google\.com\/maps/i, "com.google.android.apps.maps"],
                            [/mail\.google\.com/i, "com.google.android.gm"],
                            [/web\.whatsapp\.com|whatsapp\.com/i, "com.whatsapp"],
                            [/drive\.google\.com/i, "com.google.android.apps.docs"],
                            [/calendar\.google\.com/i, "com.google.android.calendar"],
                            [/netflix\.com/i, "com.netflix.mediaclient"],
                            [/github\.com/i, "com.github.android"],
                            [/notion\.so/i, "notion.id"],
                            [/deezer\.com/i, "deezer.android.app"]
                          ];
                          window.open = function(url, target, features) {
                            var value = String(url || "");
                            var match = apps.find(function(item) { return item[0].test(value); });
                            try {
                              var userMessages = document.querySelectorAll("#messages .message.user");
                              var latestCommand = userMessages.length ? userMessages[userMessages.length - 1].innerText : "";
                              if (/google\.com/i.test(value) && /assistant google|google assistant|assistant de google|parle a google/i.test(latestCommand) && window.DanielAndroid) {
                                window.DanielAndroid.openGoogleAssistant();
                                return null;
                              }
                              if (match && window.DanielAndroid && window.DanielAndroid.isAppInstalled(match[1])) {
                                window.DanielAndroid.openApp(match[1]);
                                return null;
                              }
                            } catch (e) {}
                            return nativeOpen(url, target, features);
                          };
                        })();""", null)
                    }
                }
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    val uri = request?.url ?: return false
                    if (uri.scheme == "https" && (uri.host == "danielservice225-dot.github.io" || uri.host == "daniel-aura-api.onrender.com")) return false
                    if (uri.scheme == "http" || uri.scheme == "https" || uri.scheme == "mailto" || uri.scheme == "tel") {
                        try { startActivity(Intent(Intent.ACTION_VIEW, uri)) } catch (_: Exception) {
                            Toast.makeText(this@MainActivity, "Impossible d’ouvrir ce lien sur cet appareil.", Toast.LENGTH_SHORT).show()
                        }
                        return true
                    }
                    return true
                }
            }

            webChromeClient = object : WebChromeClient() {
                override fun onPermissionRequest(request: PermissionRequest) {
                    runOnUiThread {
                        // Never grant media access to an unexpected website origin.
                        val host = request.origin.host
                        if (request.origin.scheme != "https" || host != "daniel-aura-api.onrender.com") {
                            request.deny()
                            return@runOnUiThread
                        }
                        val allowed = request.resources.filter {
                            it == PermissionRequest.RESOURCE_AUDIO_CAPTURE ||
                            it == PermissionRequest.RESOURCE_VIDEO_CAPTURE
                        }.toTypedArray()
                        if (allowed.isEmpty()) {
                            request.deny()
                            return@runOnUiThread
                        }
                        val missing = mutableListOf<String>()
                        if (allowed.contains(PermissionRequest.RESOURCE_AUDIO_CAPTURE) &&
                            checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                            missing.add(Manifest.permission.RECORD_AUDIO)
                        }
                        if (allowed.contains(PermissionRequest.RESOURCE_VIDEO_CAPTURE) &&
                            checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                            missing.add(Manifest.permission.CAMERA)
                        }
                        if (missing.isEmpty()) {
                            request.grant(allowed)
                        } else {
                            pendingWebPermission = request
                            requestPermissions(missing.toTypedArray(), mediaPermissionRequest)
                        }
                    }
                }

                override fun onPermissionRequestCanceled(request: PermissionRequest) {
                    if (pendingWebPermission == request) pendingWebPermission = null
                }
            }
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(android.graphics.Color.rgb(8, 13, 25))
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(18), dp(10), dp(14), dp(10))
            background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(android.graphics.Color.rgb(13, 23, 43), android.graphics.Color.rgb(20, 23, 48))
            ).apply { cornerRadius = dp(18).toFloat() }
            elevation = dp(5).toFloat()
        }
        val brand = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val title = TextView(this).apply {
            text = "Daniel Aura"
            textSize = 18f
            setTextColor(android.graphics.Color.WHITE)
            setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD))
        }
        val subtitle = TextView(this).apply {
            text = "ASSISTANT INTELLIGENT  ·  2.0"
            textSize = 9f
            letterSpacing = .12f
            setTextColor(android.graphics.Color.rgb(148, 163, 184))
        }
        brand.addView(title)
        brand.addView(subtitle)
        header.addView(brand, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        header.addView(createHeaderButton("Google") {
            AndroidAssistantBridge(this).openGoogleAssistant()
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            marginEnd = dp(6)
        })
        header.addView(createHeaderButton("Accès") {
            AndroidAssistantBridge(this).openAccessibilitySettings()
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            marginEnd = dp(6)
        })
        shortcutButton = TextView(this).apply {
            text = if (shortcutPrefs.getBoolean("shortcut_enabled", false)) "Raccourci actif" else "＋ Raccourci"
            textSize = 12f
            gravity = Gravity.CENTER
            setPadding(dp(14), dp(11), dp(14), dp(11))
            setTextColor(android.graphics.Color.WHITE)
            background = GradientDrawable().apply {
                cornerRadius = dp(16).toFloat()
                setColor(android.graphics.Color.rgb(37, 99, 235))
            }
            contentDescription = "Activer ou désactiver le raccourci flottant"
            setOnClickListener { toggleShortcut() }
        }
        header.addView(shortcutButton, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        val headerParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        headerParams.setMargins(dp(10), dp(8), dp(10), dp(6))
        root.addView(header, headerParams)
        root.addView(webView, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
        webView.loadUrl(website)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun createHeaderButton(label: String, click: () -> Unit): TextView {
        return TextView(this).apply {
            text = label
            textSize = 12f
            gravity = Gravity.CENTER
            setPadding(dp(10), dp(11), dp(10), dp(11))
            setTextColor(android.graphics.Color.WHITE)
            background = GradientDrawable().apply {
                cornerRadius = dp(14).toFloat()
                setColor(android.graphics.Color.rgb(45, 55, 75))
            }
            setOnClickListener { click() }
        }
    }

    private fun toggleShortcut() {
        val enabled = shortcutPrefs.getBoolean("shortcut_enabled", false)
        if (enabled) {
            stopService(Intent(this, OverlayService::class.java))
            shortcutPrefs.edit().putBoolean("shortcut_enabled", false).apply()
            shortcutButton?.text = "＋ Raccourci"
            Toast.makeText(this, "Raccourci flottant désactivé", Toast.LENGTH_SHORT).show()
            return
        }
        if (!Settings.canDrawOverlays(this)) {
            waitingForOverlayPermission = true
            try {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            } catch (_: Exception) {
                Toast.makeText(this, "Autorisez l’affichage par-dessus les autres applications dans les réglages.", Toast.LENGTH_LONG).show()
            }
            return
        }
        enableShortcut()
    }

    private fun enableShortcut() {
        if (!Settings.canDrawOverlays(this)) return
        shortcutPrefs.edit().putBoolean("shortcut_enabled", true).apply()
        val intent = Intent(this, OverlayService::class.java)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
            shortcutButton?.text = "Raccourci actif"
            Toast.makeText(this, "Raccourci flottant activé", Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {
            shortcutPrefs.edit().putBoolean("shortcut_enabled", false).apply()
            shortcutButton?.text = "＋ Raccourci"
            Toast.makeText(this, "Impossible d’activer le raccourci. Vérifiez les autorisations.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onResume() {
        super.onResume()
        if (waitingForOverlayPermission) {
            waitingForOverlayPermission = false
            if (Settings.canDrawOverlays(this)) enableShortcut()
            else Toast.makeText(this, "L’autorisation du raccourci n’a pas été accordée.", Toast.LENGTH_SHORT).show()
        } else if (shortcutPrefs.getBoolean("shortcut_enabled", false) && Settings.canDrawOverlays(this)) {
            val intent = Intent(this, OverlayService::class.java)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
                shortcutButton?.text = "Raccourci actif"
            } catch (_: Exception) {}
        }
    }

    private inner class NativeSpeechBridge {
        @JavascriptInterface
        fun startListening(language: String?) {
            runOnUiThread {
                pendingNativeLanguage = if (language?.startsWith("fr-") == true) language else "fr-FR"
                startNativeRecognition()
            }
        }

        @JavascriptInterface
        fun stopListening() {
            runOnUiThread { stopNativeRecognition() }
        }
    }

    // Uses Android's system speech-input screen and the speech service configured on the phone.
    // This avoids relying on WebView's unsupported Web Speech API or a custom in-app voice engine.
    private fun startNativeRecognition() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), nativeSpeechPermissionRequest)
            return
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, pendingNativeLanguage)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, pendingNativeLanguage)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Parlez à Daniel")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        }
        try {
            nativeSpeechPending = true
            webView.evaluateJavascript("window.onDanielNativeSpeechReady && window.onDanielNativeSpeechReady();", null)
            startActivityForResult(intent, nativeSpeechResultRequest)
        } catch (_: Exception) {
            nativeSpeechPending = false
            notifyWebSpeechError("unavailable")
        }
    }

    private fun notifyWebSpeechError(code: String) {
        runOnUiThread {
            webView.evaluateJavascript("window.onDanielNativeSpeechError && window.onDanielNativeSpeechError(${JSONObject.quote(code)});", null)
        }
    }

    private fun stopNativeRecognition() {
        // The system owns the speech-input screen; ignore its result if the user cancels Daniel.
        nativeSpeechPending = false
    }

    @Deprecated("Deprecated in Android API")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != nativeSpeechResultRequest) return
        val wasPending = nativeSpeechPending
        nativeSpeechPending = false
        if (!wasPending) return

        if (resultCode == RESULT_OK) {
            val phrase = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!phrase.isNullOrBlank()) {
                webView.evaluateJavascript(
                    "window.onDanielNativeSpeechResult && window.onDanielNativeSpeechResult(${JSONObject.quote(phrase)}, true);",
                    null
                )
            } else {
                notifyWebSpeechError("no-speech")
            }
        } else {
            notifyWebSpeechError("no-speech")
        }
    }

    @Deprecated("Deprecated in Android API")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == nativeSpeechPermissionRequest) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startNativeRecognition()
            } else {
                notifyWebSpeechError("not-allowed")
            }
        }
        if (requestCode == mediaPermissionRequest) {
            val request = pendingWebPermission
            pendingWebPermission = null
            if (request != null) {
                val resources = request.resources.filter { resource ->
                    (resource == PermissionRequest.RESOURCE_AUDIO_CAPTURE &&
                        checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) ||
                    (resource == PermissionRequest.RESOURCE_VIDEO_CAPTURE &&
                        checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
                }.toTypedArray()
                if (resources.isNotEmpty()) request.grant(resources) else request.deny()
            }
        }
    }

    override fun onBackPressed() {
        if (::webView.isInitialized && webView.canGoBack()) webView.goBack()
        else super.onBackPressed()
    }

    override fun onDestroy() {
        pendingWebPermission?.let { try { it.deny() } catch (_: Exception) {} }
        pendingWebPermission = null
        stopNativeRecognition()
        if (::webView.isInitialized) {
            webView.stopLoading()
            webView.destroy()
        }
        super.onDestroy()
    }
}
