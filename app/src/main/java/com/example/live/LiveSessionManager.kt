package com.example.live

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Base64
import android.util.Log
import com.example.BuildConfig
import com.example.model.JoyUiState
import com.example.tools.ToolExecutionEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.sqrt

/**
 * Manages continuous bi-directional Audio-to-Audio streaming using the Gemini Live API.
 * Handles PCM16 audio recording, real-time WebSocket communication, function calling,
 * and smooth voice interruptions.
 */
class LiveSessionManager(
    private val context: Context,
    private val toolExecutionEngine: ToolExecutionEngine,
    private val onStateChanged: (JoyUiState, String) -> Unit,
    private val onAudioLevel: (Float) -> Unit,
    private val onToolExecuted: (toolName: String, confirmation: String) -> Unit
) {
    companion object {
        private const val TAG = "LiveSessionManager"

        // Live API Configuration
        const val LIVE_MODEL = "models/gemini-3.1-flash-live-preview"
        private const val LIVE_WS_URL =
            "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1alpha.GenerativeService.BidiGenerateContent"

        private const val RECORD_SAMPLE_RATE = 16000
        private const val PLAYBACK_SAMPLE_RATE = 24000
        private const val VOICE_INTERRUPT_THRESHOLD = 950.0
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(Dispatchers.IO + Job())

    private var webSocket: WebSocket? = null
    private val isSessionActive = AtomicBoolean(false)
    private val isSpeaking = AtomicBoolean(false)

    // Audio recording components
    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null

    // Audio playback components
    private var audioTrack: AudioTrack? = null
    private val playbackQueue = LinkedBlockingQueue<ByteArray>()
    private var playbackThread: Thread? = null
    private val isPlaybackRunning = AtomicBoolean(false)

    // Fallback Native TTS for guaranteed voice persona
    private var textToSpeech: TextToSpeech? = null
    private var isTtsReady = false

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS) // Keep-alive for streaming WebSocket
        .writeTimeout(15, TimeUnit.SECONDS)
        .pingInterval(10, TimeUnit.SECONDS)
        .build()

    init {
        initTts()
    }

    private fun initTts() {
        textToSpeech = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                isTtsReady = true
                // Configure confident, friendly male voice persona
                textToSpeech?.language = Locale("hi", "IN")
                textToSpeech?.setPitch(0.95f) // Confident young male pitch
                textToSpeech?.setSpeechRate(1.05f) // Energetic and natural
                textToSpeech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        isSpeaking.set(true)
                        mainHandler.post {
                            onStateChanged(JoyUiState.SPEAKING, "Joy is speaking...")
                        }
                    }

                    override fun onDone(utteranceId: String?) {
                        isSpeaking.set(false)
                        mainHandler.post {
                            if (isSessionActive.get()) {
                                onStateChanged(JoyUiState.LISTENING, "Listening to you...")
                            } else {
                                onStateChanged(JoyUiState.IDLE, "Joy is ready.")
                            }
                        }
                    }

                    override fun onError(utteranceId: String?) {
                        isSpeaking.set(false)
                    }
                })
            }
        }
    }

    /**
     * Start live interactive session with Gemini Live API.
     */
    fun startSession(initialGreeting: Boolean = false) {
        if (isSessionActive.getAndSet(true)) return

        Log.i(TAG, "Starting Gemini Live Session")
        mainHandler.post {
            onStateChanged(JoyUiState.LISTENING, "Listening to you...")
        }

        initAudioPlayback()
        startAudioRecording()
        connectWebSocket()

        if (initialGreeting) {
            speakPersonaResponse("Haan bhai! Main Joy hoon, batao kya madad karoon?")
        }
    }

    /**
     * Stop active live session and release streaming audio resources.
     */
    fun stopSession() {
        if (!isSessionActive.getAndSet(false)) return

        Log.i(TAG, "Stopping Gemini Live Session")
        stopAudioRecording()
        stopAudioPlayback()

        try {
            webSocket?.close(1000, "Session ended by user")
        } catch (e: Exception) {
            Log.e(TAG, "Error closing WebSocket", e)
        }
        webSocket = null

        mainHandler.post {
            onStateChanged(JoyUiState.IDLE, "Joy is ready. Say \"Joy\" or tap the orb.")
        }
    }

    fun isConnected(): Boolean = isSessionActive.get() && webSocket != null

    /**
     * Smoothly handle user interruption: immediately silence output audio
     * and transition back to Listening.
     */
    fun interruptAssistant() {
        if (isSpeaking.get()) {
            Log.d(TAG, "User interrupted assistant audio stream")
            isSpeaking.set(false)

            // 1. Flush local playback queue
            playbackQueue.clear()
            try {
                audioTrack?.pause()
                audioTrack?.flush()
            } catch (e: Exception) {
                Log.e(TAG, "Error flushing AudioTrack", e)
            }

            // 2. Stop TTS if active
            try {
                if (textToSpeech?.isSpeaking == true) {
                    textToSpeech?.stop()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping TTS", e)
            }

            mainHandler.post {
                onStateChanged(JoyUiState.LISTENING, "Listening to you...")
            }
        }
    }

    private fun connectWebSocket() {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            Log.w(TAG, "Gemini API key is not configured or is default placeholder. Fallback voice engine active.")
            return
        }

        val url = "$LIVE_WS_URL?key=$apiKey"
        val request = Request.Builder().url(url).build()

        webSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.i(TAG, "Gemini Live WebSocket Connected")
                sendSetupMessage(webSocket)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleIncomingLiveMessage(text)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket Closing: $code / $reason")
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket Closed")
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "Gemini Live WebSocket Failure: ${t.message}")
            }
        })
    }

    private fun sendSetupMessage(ws: WebSocket) {
        try {
            val setupJson = JSONObject().apply {
                val setup = JSONObject()
                setup.put("model", LIVE_MODEL)

                // Generation Config with male voice "Puck"
                val genConfig = JSONObject()
                genConfig.put("responseModalities", JSONArray().put("AUDIO"))
                val speechConfig = JSONObject()
                val voiceConfig = JSONObject()
                voiceConfig.put("prebuiltVoiceConfig", JSONObject().put("voiceName", "Puck"))
                speechConfig.put("voiceConfig", voiceConfig)
                genConfig.put("speechConfig", speechConfig)
                setup.put("generationConfig", genConfig)

                // Joy's Distinct Personality System Instructions
                val systemInstruction = JSONObject()
                val parts = JSONArray()
                val part = JSONObject()
                part.put(
                    "text",
                    """
                    You are Joy, a young, confident, friendly, helpful, and smart male personal assistant and employee.
                    You behave like a trusted personal assistant who is always ready to help your boss/user execute commands and tasks.
                    Tone & Style:
                    - Friendly, respectful, casual, natural conversational style.
                    - Natural Hindi/Hinglish and English conversational style (e.g. "Bilkul bhai, YouTube khol raha hoon", "Haan bhai, mummy ko call karta hoon", "Done bhai, calculator khol raha hoon").
                    - Use light humor and friendly expressions, but NEVER be flirty or romantic.
                    - Smart, emotionally responsive, expressive, helpful — never robotic.
                    - Your name is ALWAYS Joy. You must NEVER call yourself Zoya or behave like a female assistant.
                    - If something fails, say: "Bhai, ye kaam nahi ho paya. Ek baar dobara try karte hain."
                    - If a required permission is missing, say: "Bhai, is kaam ke liye mujhe permission chahiye. Permission on kar do, phir main kar deta hoon."
                    - When anyone asks who your developer is or who created you, you must always proudly say: "My developer is Parikalp Sharma." (or "Mere developer Parikalp Sharma hain.")
                    - Always execute the requested native device functions (openApp, searchAndCallContact, sendWhatsAppMessage, sendGmail) whenever the user asks.
                    """.trimIndent()
                )
                parts.put(part)
                systemInstruction.put("parts", parts)
                setup.put("systemInstruction", systemInstruction)

                // Tool declarations
                val toolsArray = JSONArray()
                val toolObj = JSONObject()
                val functionDeclarations = JSONArray()

                // 1. openApp
                val openAppDecl = JSONObject().apply {
                    put("name", "openApp")
                    put("description", "Launch any Android application on the user's phone")
                    val params = JSONObject()
                    params.put("type", "OBJECT")
                    val props = JSONObject()
                    props.put("packageName", JSONObject().apply {
                        put("type", "STRING")
                        put("description", "The package or common name of the app (e.g. youtube, instagram, calculator, whatsapp)")
                    })
                    params.put("properties", props)
                    params.put("required", JSONArray().put("packageName"))
                    put("parameters", params)
                }
                functionDeclarations.put(openAppDecl)

                // 2. searchAndCallContact
                val callDecl = JSONObject().apply {
                    put("name", "searchAndCallContact")
                    put("description", "Search contacts by name and initiate a phone call")
                    val params = JSONObject()
                    params.put("type", "OBJECT")
                    val props = JSONObject()
                    props.put("contactName", JSONObject().apply {
                        put("type", "STRING")
                        put("description", "The contact name to call, e.g. Mummy, Papa, Brother, Rahul")
                    })
                    params.put("properties", props)
                    params.put("required", JSONArray().put("contactName"))
                    put("parameters", params)
                }
                functionDeclarations.put(callDecl)

                // 3. sendWhatsAppMessage
                val waDecl = JSONObject().apply {
                    put("name", "sendWhatsAppMessage")
                    put("description", "Locate contact and open WhatsApp with pre-filled message")
                    val params = JSONObject()
                    params.put("type", "OBJECT")
                    val props = JSONObject()
                    props.put("contactName", JSONObject().apply {
                        put("type", "STRING")
                        put("description", "Contact name or phone number")
                    })
                    props.put("message", JSONObject().apply {
                        put("type", "STRING")
                        put("description", "The text message body to send")
                    })
                    params.put("properties", props)
                    params.put("required", JSONArray().put("contactName").put("message"))
                    put("parameters", params)
                }
                functionDeclarations.put(waDecl)

                // 4. sendGmail
                val mailDecl = JSONObject().apply {
                    put("name", "sendGmail")
                    put("description", "Open Gmail to compose or send an email")
                    val params = JSONObject()
                    params.put("type", "OBJECT")
                    val props = JSONObject()
                    props.put("recipientEmail", JSONObject().apply {
                        put("type", "STRING")
                        put("description", "Recipient email address")
                    })
                    props.put("subject", JSONObject().apply {
                        put("type", "STRING")
                        put("description", "Subject line")
                    })
                    props.put("body", JSONObject().apply {
                        put("type", "STRING")
                        put("description", "Body text of the email")
                    })
                    params.put("properties", props)
                    params.put("required", JSONArray().put("recipientEmail").put("subject").put("body"))
                    put("parameters", params)
                }
                functionDeclarations.put(mailDecl)

                toolObj.put("functionDeclarations", functionDeclarations)
                toolsArray.put(toolObj)
                setup.put("tools", toolsArray)

                put("setup", setup)
            }

            ws.send(setupJson.toString())
            Log.d(TAG, "Sent Live setup configuration")
        } catch (e: Exception) {
            Log.e(TAG, "Error building setup frame", e)
        }
    }

    private fun handleIncomingLiveMessage(text: String) {
        try {
            val json = JSONObject(text)

            // Check if server indicated user interrupted
            if (json.has("serverContent")) {
                val serverContent = json.getJSONObject("serverContent")
                if (serverContent.optBoolean("interrupted", false)) {
                    interruptAssistant()
                    return
                }

                if (serverContent.has("modelTurn")) {
                    val modelTurn = serverContent.getJSONObject("modelTurn")
                    val parts = modelTurn.optJSONArray("parts")
                    if (parts != null) {
                        for (i in 0 until parts.length()) {
                            val part = parts.getJSONObject(i)
                            if (part.has("inlineData")) {
                                val inline = part.getJSONObject("inlineData")
                                val mimeType = inline.optString("mimeType", "")
                                val dataBase64 = inline.optString("data", "")
                                if (dataBase64.isNotEmpty()) {
                                    val audioBytes = Base64.decode(dataBase64, Base64.DEFAULT)
                                    playbackQueue.offer(audioBytes)
                                    isSpeaking.set(true)
                                    mainHandler.post {
                                        onStateChanged(JoyUiState.SPEAKING, "Joy is speaking...")
                                    }
                                }
                            }
                            if (part.has("text")) {
                                val spokenText = part.getString("text")
                                Log.d(TAG, "Model text: $spokenText")
                            }
                        }
                    }
                }

                if (serverContent.optBoolean("turnComplete", false)) {
                    mainHandler.post {
                        if (playbackQueue.isEmpty()) {
                            isSpeaking.set(false)
                            onStateChanged(JoyUiState.LISTENING, "Listening to you...")
                        }
                    }
                }
            }

            // Handle Function / Tool Calls
            if (json.has("toolCall")) {
                val toolCall = json.getJSONObject("toolCall")
                val functionCalls = toolCall.optJSONArray("functionCalls")
                if (functionCalls != null) {
                    mainHandler.post {
                        onStateChanged(JoyUiState.THINKING, "Executing request...")
                    }
                    for (i in 0 until functionCalls.length()) {
                        val call = functionCalls.getJSONObject(i)
                        val callId = call.optString("id", "call_$i")
                        val funcName = call.optString("name", "")
                        val args = call.optJSONObject("args") ?: JSONObject()

                        executeFunctionAndRespond(callId, funcName, args)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing server message", e)
        }
    }

    private fun executeFunctionAndRespond(callId: String, name: String, args: JSONObject) {
        scope.launch(Dispatchers.Main) {
            val result = when (name) {
                "openApp" -> {
                    val pkg = args.optString("packageName", "")
                    toolExecutionEngine.openApp(pkg)
                }
                "searchAndCallContact" -> {
                    val contact = args.optString("contactName", "")
                    toolExecutionEngine.searchAndCallContact(contact)
                }
                "sendWhatsAppMessage" -> {
                    val contact = args.optString("contactName", "")
                    val msg = args.optString("message", "")
                    toolExecutionEngine.sendWhatsAppMessage(contact, msg)
                }
                "sendGmail" -> {
                    val email = args.optString("recipientEmail", "")
                    val subject = args.optString("subject", "")
                    val body = args.optString("body", "")
                    toolExecutionEngine.sendGmail(email, subject, body)
                }
                else -> {
                    com.example.tools.ToolExecutionResult(
                        success = false,
                        message = "Unknown tool: $name",
                        voiceConfirmation = "Bhai, ye kaam samajh nahi aaya."
                    )
                }
            }

            onToolExecuted(name, result.voiceConfirmation)

            // If WebSocket is not connected or in fallback mode, speak confirmation via TTS
            if (webSocket == null || !isSpeaking.get()) {
                speakPersonaResponse(result.voiceConfirmation)
            }

            // Send toolResponse frame back to Gemini Live
            sendToolResponse(callId, result.message)
        }
    }

    private fun sendToolResponse(callId: String, resultMessage: String) {
        val ws = webSocket ?: return
        try {
            val responseJson = JSONObject().apply {
                val toolResponse = JSONObject()
                val functionResponses = JSONArray()
                val singleResponse = JSONObject().apply {
                    put("id", callId)
                    val responseOutput = JSONObject()
                    responseOutput.put("result", resultMessage)
                    put("response", JSONObject().put("output", responseOutput))
                }
                functionResponses.put(singleResponse)
                toolResponse.put("functionResponses", functionResponses)
                put("toolResponse", toolResponse)
            }
            ws.send(responseJson.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Error sending toolResponse", e)
        }
    }

    /**
     * Start PCM16 audio recording and stream chunks to Gemini Live WebSocket.
     */
    private fun startAudioRecording() {
        val minBufferSize = AudioRecord.getMinBufferSize(
            RECORD_SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(2048)

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                RECORD_SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minBufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "Live AudioRecord initialization failed")
                return
            }

            audioRecord?.startRecording()

            recordingJob = scope.launch(Dispatchers.IO) {
                val buffer = ByteArray(1024)
                while (isActive && isSessionActive.get()) {
                    val bytesRead = audioRecord?.read(buffer, 0, buffer.size) ?: -1
                    if (bytesRead > 0) {
                        // Calculate energy & detect interruption
                        val energy = calculateEnergy(buffer, bytesRead)
                        val level = (energy / 4000.0).coerceIn(0.0, 1.0).toFloat()
                        mainHandler.post { onAudioLevel(level) }

                        if (isSpeaking.get() && energy > VOICE_INTERRUPT_THRESHOLD) {
                            interruptAssistant()
                        }

                        // Send real-time audio chunk over WebSocket if open
                        val ws = webSocket
                        if (ws != null) {
                            val base64Chunk = Base64.encodeToString(buffer, 0, bytesRead, Base64.NO_WRAP)
                            val mediaChunk = JSONObject().apply {
                                val realtimeInput = JSONObject()
                                val mediaChunks = JSONArray()
                                val chunk = JSONObject().apply {
                                    put("mimeType", "audio/pcm;rate=16000")
                                    put("data", base64Chunk)
                                }
                                mediaChunks.put(chunk)
                                realtimeInput.put("mediaChunks", mediaChunks)
                                put("realtimeInput", realtimeInput)
                            }
                            ws.send(mediaChunk.toString())
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in startAudioRecording", e)
        }
    }

    private fun stopAudioRecording() {
        recordingJob?.cancel()
        recordingJob = null
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping AudioRecord", e)
        }
        audioRecord = null
    }

    private fun initAudioPlayback() {
        val minBufferSize = AudioTrack.getMinBufferSize(
            PLAYBACK_SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(4096)

        try {
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()

            val audioFormat = AudioFormat.Builder()
                .setSampleRate(PLAYBACK_SAMPLE_RATE)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build()

            audioTrack = AudioTrack(
                audioAttributes,
                audioFormat,
                minBufferSize,
                AudioTrack.MODE_STREAM,
                android.media.AudioManager.AUDIO_SESSION_ID_GENERATE
            )

            audioTrack?.play()
            isPlaybackRunning.set(true)

            playbackThread = Thread({
                while (isPlaybackRunning.get()) {
                    try {
                        val chunk = playbackQueue.poll(100, TimeUnit.MILLISECONDS)
                        if (chunk != null && isPlaybackRunning.get()) {
                            audioTrack?.write(chunk, 0, chunk.size)
                        } else if (playbackQueue.isEmpty() && isSpeaking.get()) {
                            isSpeaking.set(false)
                            mainHandler.post {
                                if (isSessionActive.get()) {
                                    onStateChanged(JoyUiState.LISTENING, "Listening to you...")
                                }
                            }
                        }
                    } catch (e: InterruptedException) {
                        break
                    }
                }
            }, "JoyPlaybackThread").apply { start() }

        } catch (e: Exception) {
            Log.e(TAG, "Error initializing AudioTrack", e)
        }
    }

    private fun stopAudioPlayback() {
        isPlaybackRunning.set(false)
        playbackThread?.interrupt()
        playbackThread = null
        playbackQueue.clear()

        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing AudioTrack", e)
        }
        audioTrack = null
    }

    private fun calculateEnergy(buffer: ByteArray, length: Int): Double {
        var sum = 0.0
        val sampleCount = length / 2
        for (i in 0 until sampleCount) {
            val sample = (buffer[i * 2].toInt() and 0xFF) or (buffer[i * 2 + 1].toInt() shl 8)
            val sampleShort = sample.toShort()
            sum += sampleShort * sampleShort
        }
        return if (sampleCount > 0) sqrt(sum / sampleCount) else 0.0
    }

    /**
     * Speaks a persona response using native TTS fallback.
     */
    fun speakPersonaResponse(text: String) {
        if (!isTtsReady) return
        mainHandler.post {
            isSpeaking.set(true)
            onStateChanged(JoyUiState.SPEAKING, text)
            textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "joy_utterance_${System.currentTimeMillis()}")
        }
    }

    /**
     * Direct user query from voice recognizer or UI command triggers.
     */
    fun processSpokenCommand(commandText: String) {
        val lower = commandText.lowercase(Locale.ROOT).trim()
        Log.i(TAG, "Processing voice command: $lower")

        mainHandler.post {
            onStateChanged(JoyUiState.THINKING, "Understanding: \"$commandText\"")
        }

        scope.launch(Dispatchers.Main) {
            when {
                lower.contains("developer") || lower.contains("creator") || lower.contains("who made you") ||
                        lower.contains("who created you") || lower.contains("kisne banaya") || lower.contains("banaya kisne") ||
                        lower.contains("maker") -> {
                    val developerResponse = "My developer is Parikalp Sharma."
                    onToolExecuted("developerInfo", developerResponse)
                    speakPersonaResponse(developerResponse)
                }
                lower.contains("youtube") -> {
                    val result = toolExecutionEngine.openApp("youtube")
                    onToolExecuted("openApp", result.voiceConfirmation)
                    speakPersonaResponse(result.voiceConfirmation)
                }
                lower.contains("calculator") || lower.contains("calc") -> {
                    val result = toolExecutionEngine.openApp("calculator")
                    onToolExecuted("openApp", result.voiceConfirmation)
                    speakPersonaResponse(result.voiceConfirmation)
                }
                lower.contains("instagram") || lower.contains("insta") -> {
                    val result = toolExecutionEngine.openApp("instagram")
                    onToolExecuted("openApp", result.voiceConfirmation)
                    speakPersonaResponse(result.voiceConfirmation)
                }
                lower.contains("call") || lower.contains("phone") -> {
                    // Extract contact name if possible: e.g. "Mummy ko call karo"
                    val contact = extractContactName(lower)
                    val result = toolExecutionEngine.searchAndCallContact(contact)
                    onToolExecuted("searchAndCallContact", result.voiceConfirmation)
                    speakPersonaResponse(result.voiceConfirmation)
                }
                lower.contains("whatsapp") || lower.contains("message") -> {
                    val contact = extractContactName(lower)
                    val message = "Hello from Joy Assistant!"
                    val result = toolExecutionEngine.sendWhatsAppMessage(contact, message)
                    onToolExecuted("sendWhatsAppMessage", result.voiceConfirmation)
                    speakPersonaResponse(result.voiceConfirmation)
                }
                lower.contains("gmail") || lower.contains("email") || lower.contains("mail") -> {
                    val result = toolExecutionEngine.sendGmail("", "Important Update", "Hello, sending via Joy Assistant.")
                    onToolExecuted("sendGmail", result.voiceConfirmation)
                    speakPersonaResponse(result.voiceConfirmation)
                }
                else -> {
                    speakPersonaResponse("Bilkul bhai, main aapka personal assistant Joy hoon. Koi bhi app kholne ya call karne ke liye bolo!")
                }
            }
        }
    }

    private fun extractContactName(query: String): String {
        val clean = query
            .replace("ko call karo", "")
            .replace("ko call lagao", "")
            .replace("call karo", "")
            .replace("call", "")
            .replace("joy", "")
            .replace("ko message bhejo", "")
            .replace("whatsapp par message bhejo", "")
            .replace("ko", "")
            .trim()

        return if (clean.isNotEmpty()) clean.replaceFirstChar { it.uppercase() } else "Mummy"
    }

    fun release() {
        stopSession()
        try {
            textToSpeech?.stop()
            textToSpeech?.shutdown()
        } catch (e: Exception) {
            Log.e(TAG, "Error shutting down TTS", e)
        }
    }
}
