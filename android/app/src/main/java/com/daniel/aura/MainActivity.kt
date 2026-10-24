package com.daniel.aura

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast

class MainActivity : Activity() {
    private lateinit var webView: WebView
    private val website = "https://danielservice225-dot.github.io/Daniel/"
    private val mediaPermissionRequest = 420
    private var pendingWebPermission: PermissionRequest? = null

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

            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    val uri = request?.url ?: return false
                    if (uri.scheme == "https" && uri.host == "danielservice225-dot.github.io") return false
                    if (uri.scheme == "https" && uri.host == "daniel-aura-api.onrender.com") return false
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

        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 21)
        }
        webView.loadUrl(website)
    }

    @Deprecated("Deprecated in Android API")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
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
        if (::webView.isInitialized) {
            webView.stopLoading()
            webView.destroy()
        }
        super.onDestroy()
    }
}
