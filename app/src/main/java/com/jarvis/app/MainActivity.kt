package com.jarvis.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {

    private lateinit var tvChat: TextView
    private lateinit var etInput: EditText
    private lateinit var tts: TextToSpeech
    private var chatHistory = "You are JARVIS, a highly professional AI assistant. Address the user as Sir. Be concise.\n"

    // 🚨 YAHAN APNI OPENROUTER API KEY DAALEIN (sk-or-v1-...)
    private val API_KEY = 
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
    private val speechLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val spokenText = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.get(0)
            if (spokenText != null) {
                etInput.setText(spokenText)
                sendMessageToAI(spokenText)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvChat = findViewById(R.id.tvChat)
        etInput = findViewById(R.id.etInput)

        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts.language = Locale.US
            }
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 101)
        }

        findViewById<Button>(R.id.btnSend).setOnClickListener {
            val text = etInput.text.toString().trim()
            if (text.isNotEmpty()) {
                sendMessageToAI(text)
                etInput.setText("")
            }
        }

        findViewById<Button>(R.id.btnMic).setOnClickListener {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak to JARVIS...")
            speechLauncher.launch(intent)
        }
    }

    private fun sendMessageToAI(userMessage: String) {
        tvChat.append("\nYou: $userMessage\n")
        chatHistory += "User: $userMessage\n"

        thread {
            try {
                val url = URL("https://openrouter.ai/api/v1/chat/completions")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.setRequestProperty("Authorization", "Bearer $API_KEY")

                val jsonBody = JSONObject().apply {
                    put("model", "openrouter/free")
                    put("messages", org.json.JSONArray().put(JSONObject().apply {
                        put("role", "user")
                        put("content", chatHistory)
                    }))
                }

                val writer = OutputStreamWriter(conn.outputStream, "UTF-8")
                writer.write(jsonBody.toString())
                writer.flush()
                writer.close()

                if (conn.responseCode == 200) {
                    val response = conn.inputStream.bufferedReader().readText()
                    val jsonResponse = JSONObject(response)
                    val aiReply = jsonResponse.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
                    
                    chatHistory += "JARVIS: $aiReply\n"

                    runOnUiThread {
                        tvChat.append("\nJARVIS: $aiReply\n")
                        tts.speak(aiReply, TextToSpeech.QUEUE_FLUSH, null, "")
                    }
                }
            } catch (e: Exception) {
                runOnUiThread { tvChat.append("\nError: ${e.message}\n") }
            }
        }
    }
}
