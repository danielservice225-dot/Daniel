package com.daniel.aura

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.concurrent.Executors

class MainActivity : Activity(), TextToSpeech.OnInitListener {
    private val apiBase = "https://daniel-aura-api.onrender.com"
    private val executor = Executors.newSingleThreadExecutor()
    private lateinit var transcript: TextView
    private lateinit var prompt: EditText
    private lateinit var status: TextView
    private var tts: TextToSpeech? = null
    private val history = JSONArray()
    private val speechRequest = 812

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TextToSpeech(this, this)
        buildUi()
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 20)
        }
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 28, 20, 16)
            setBackgroundColor(0xFFF8FAFC.toInt())
        }
        val title = TextView(this).apply {
            text = "Daniel — Aura IA"
            textSize = 25f
            setTextColor(0xFF1D4ED8.toInt())
        }
        status = TextView(this).apply { text = "Connecté au service Daniel"; textSize = 13f }
        transcript = TextView(this).apply {
            text = "Bonjour, je suis Daniel. Pose ta question par écrit ou par la voix."
            textSize = 16f
            setTextColor(0xFF0F172A.toInt())
            setPadding(4, 16, 4, 16)
        }
        val scroll = ScrollView(this).apply { addView(transcript) }
        root.addView(title)
        root.addView(status)
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        prompt = EditText(this).apply { hint = "Pose ta question à Daniel…"; minLines = 1; maxLines = 4 }
        root.addView(prompt)
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        row.addView(makeButton("Envoyer") { sendMessage() }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(makeButton("Parler") { startVoiceInput() }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(row)
        root.addView(makeButton("Activer le bouton flottant") { enableOverlay() })
        root.addView(makeButton("Autoriser les actions système (facultatif)") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        })
        root.addView(makeButton("Ouvrir le site Daniel") {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://danielservice225-dot.github.io/Daniel/")))
        })
        setContentView(root)
    }

    private fun makeButton(label: String, action: () -> Unit) = Button(this).apply {
        text = label
        setOnClickListener { action() }
    }

    private fun enableOverlay() {
        if (!Settings.canDrawOverlays(this)) {
            startActivityForResult(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")), 901)
        } else {
            startService(Intent(this, OverlayService::class.java))
            status.text = "Bouton flottant activé. Tu peux quitter Daniel."
        }
    }

    @Deprecated("Legacy Android callback retained for broad device support")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 901 && Settings.canDrawOverlays(this)) {
            startService(Intent(this, OverlayService::class.java))
            status.text = "Bouton flottant activé. Tu peux quitter Daniel."
        }
        if (requestCode == speechRequest && resultCode == RESULT_OK) {
            val spoken = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spoken.isNullOrBlank()) {
                prompt.setText(spoken)
                sendMessage()
            }
        }
    }

    private fun startVoiceInput() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "fr-FR")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Parle à Daniel")
        }
        try { startActivityForResult(intent, speechRequest) }
        catch (_: Exception) { status.text = "La reconnaissance vocale Android n'est pas disponible sur cet appareil." }
    }

    private fun sendMessage() {
        val message = prompt.text.toString().trim()
        if (message.isEmpty()) return
        transcript.append("\n\nVous : $message\n\nDaniel : …")
        prompt.setText("")
        status.text = "Daniel réfléchit…"
        executor.execute {
            try {
                val conn = (URL("$apiBase/api/chat").openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 15000
                    readTimeout = 45000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                }
                val payload = JSONObject().put("message", message).put("history", history).toString()
                conn.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val body = stream.bufferedReader().use { it.readText() }
                if (code !in 200..299) throw IllegalStateException("Serveur indisponible ($code)")
                val reply = JSONObject(body).optString("reply", "Je n'ai pas reçu de réponse.")
                history.put(JSONObject().put("role", "user").put("content", message))
                history.put(JSONObject().put("role", "assistant").put("content", reply))
                runOnUiThread {
                    transcript.append(reply)
                    status.text = "Prêt"
                    tts?.speak(reply, TextToSpeech.QUEUE_FLUSH, null, "daniel_reply")
                }
            } catch (e: Exception) {
                runOnUiThread {
                    transcript.append("Impossible de joindre Daniel. Vérifie la connexion et le service Render.")
                    status.text = "Connexion indisponible"
                }
            }
        }
    }

    override fun onInit(statusCode: Int) {
        if (statusCode == TextToSpeech.SUCCESS) tts?.language = Locale.FRENCH
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        executor.shutdownNow()
        super.onDestroy()
    }
}
