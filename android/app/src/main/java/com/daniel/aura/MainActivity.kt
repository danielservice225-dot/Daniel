package com.daniel.aura

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
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
    private var nativeSpeechRecognizer: SpeechRecognizer? = null

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

            webViewClient = object : WebViewClient() {
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
        setContentView(webView)

        webView.loadUrl(website)
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

    private fun startNativeRecognition() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), nativeSpeechPermissionRequest)
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            notifyWebSpeechError("unavailable")
            return
        }

        stopNativeRecognition()
        try {
            val recognizer = SpeechRecognizer.createSpeechRecognizer(this)
            nativeSpeechRecognizer = recognizer
            recognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    webView.evaluateJavascript("window.onDanielNativeSpeechReady && window.onDanielNativeSpeechReady();", null)
                }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
                override fun onPartialResults(partialResults: Bundle?) {
                    val text = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                    if (!text.isNullOrBlank()) {
                        webView.evaluateJavascript("window.onDanielNativeSpeechResult && window.onDanielNativeSpeechResult(${JSONObject.quote(text)}, false);", null)
                    }
                }
                override fun onResults(results: Bundle?) {
                    val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                    if (!text.isNullOrBlank()) {
                        webView.evaluateJavascript("window.onDanielNativeSpeechResult && window.onDanielNativeSpeechResult(${JSONObject.quote(text)}, true);", null)
                    } else {
                        notifyWebSpeechError("no-speech")
                    }
                    stopNativeRecognition()
                }
                override fun onError(error: Int) {
                    val code = when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "no-speech"
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "not-allowed"
                        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "network"
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "busy"
                        else -> "recognition"
                    }
                    notifyWebSpeechError(code)
                    stopNativeRecognition()
                }
            })
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, pendingNativeLanguage)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, pendingNativeLanguage)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Parlez à Daniel")
            }
            recognizer.startListening(intent)
        } catch (_: Exception) {
            stopNativeRecognition()
            notifyWebSpeechError("unavailable")
        }
    }

    private fun notifyWebSpeechError(code: String) {
        runOnUiThread {
            webView.evaluateJavascript("window.onDanielNativeSpeechError && window.onDanielNativeSpeechError(${JSONObject.quote(code)});", null)
        }
    }

    private fun stopNativeRecognition() {
        val recognizer = nativeSpeechRecognizer
        nativeSpeechRecognizer = null
        if (recognizer != null) {
            try { recognizer.cancel() } catch (_: Exception) {}
            try { recognizer.destroy() } catch (_: Exception) {}
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
