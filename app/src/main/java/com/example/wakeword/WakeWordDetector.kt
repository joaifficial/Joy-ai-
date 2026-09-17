package com.example.wakeword

import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.content.ContextCompat
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.sqrt

/**
 * Lightweight, local wake-word detection engine for the name "Joy".
 * Uses AudioRecord for real-time energy & audio monitoring, and on-device
 * acoustic matching to trigger when "Joy" is spoken (strictly rejecting "Zoya").
 */
class WakeWordDetector(
    private val context: Context,
    private val onWakeWordDetected: (wakeWord: String) -> Unit,
    private val onAudioLevelChanged: (level: Float) -> Unit = {}
) {
    companion object {
        private const val TAG = "WakeWordDetector"
        private const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val ENERGY_THRESHOLD = 850.0 // Voice activity energy threshold
    }

    private val isRunning = AtomicBoolean(false)
    private var audioRecord: AudioRecord? = null
    private var recordThread: Thread? = null

    private val mainHandler = Handler(Looper.getMainLooper())
    private var speechRecognizer: SpeechRecognizer? = null
    private var isSpeechRecognizerActive = false
    private var lastTriggerTime = 0L

    fun startListening() {
        if (isRunning.getAndSet(true)) return

        Log.d(TAG, "Starting wake-word detector for 'Joy'")
        setupAudioRecord()
        mainHandler.post {
            setupSpeechRecognizer()
        }
    }

    fun stopListening() {
        if (!isRunning.getAndSet(false)) return

        Log.d(TAG, "Stopping wake-word detector")
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping AudioRecord", e)
        }
        audioRecord = null
        recordThread?.interrupt()
        recordThread = null

        mainHandler.post {
            try {
                speechRecognizer?.destroy()
            } catch (e: Exception) {
                Log.e(TAG, "Error destroying speech recognizer", e)
            }
            speechRecognizer = null
            isSpeechRecognizerActive = false
        }
    }

    private fun setupAudioRecord() {
        val minBufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT
        ).coerceAtLeast(2048)

        try {
            if (ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.RECORD_AUDIO
                ) != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                Log.w(TAG, "RECORD_AUDIO permission missing for wake-word detector")
                return
            }

            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                minBufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord not initialized")
                return
            }

            audioRecord?.startRecording()

            recordThread = Thread({
                val buffer = ShortArray(1024)
                while (isRunning.get() && !Thread.currentThread().isInterrupted) {
                    val read = audioRecord?.read(buffer, 0, buffer.size) ?: -1
                    if (read > 0) {
                        // Calculate RMS Energy
                        var sum = 0.0
                        for (i in 0 until read) {
                            sum += buffer[i] * buffer[i]
                        }
                        val rms = sqrt(sum / read)
                        val normalizedLevel = (rms / 4000.0).coerceIn(0.0, 1.0).toFloat()
                        onAudioLevelChanged(normalizedLevel)

                        // Voice activity detected: trigger acoustic verification
                        if (rms > ENERGY_THRESHOLD) {
                            val now = System.currentTimeMillis()
                            if (now - lastTriggerTime > 1500) {
                                mainHandler.post {
                                    startVoiceVerification()
                                }
                            }
                        }
                    }
                }
            }, "JoyWakeWordThread").apply { start() }

        } catch (e: Exception) {
            Log.e(TAG, "Exception initializing AudioRecord", e)
        }
    }

    private fun setupSpeechRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            Log.w(TAG, "Speech recognition not available on device")
            return
        }

        try {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
            speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {
                    isSpeechRecognizerActive = false
                }

                override fun onError(error: Int) {
                    isSpeechRecognizerActive = false
                }

                override fun onResults(results: Bundle?) {
                    isSpeechRecognizerActive = false
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    evaluateMatches(matches)
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    evaluateMatches(matches)
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up speech recognizer", e)
        }
    }

    private fun startVoiceVerification() {
        if (!isRunning.get() || isSpeechRecognizerActive) return
        if (speechRecognizer == null) {
            setupSpeechRecognizer()
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra("android.speech.extra.DICTATION_MODE", true)
        }

        try {
            isSpeechRecognizerActive = true
            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            isSpeechRecognizerActive = false
            Log.e(TAG, "Failed to start speech recognizer listening", e)
        }
    }

    private fun evaluateMatches(matches: List<String>?) {
        if (matches.isNullOrEmpty()) return

        for (phrase in matches) {
            val lower = phrase.lowercase(Locale.ROOT).trim()
            Log.d(TAG, "Recognized audio candidate: '$lower'")

            // Strictly reject "Zoya" or variants
            if (lower.contains("zoya") || lower.contains("zoya ji") || lower.contains("zoyaa")) {
                Log.w(TAG, "Rejected candidate 'zoya'")
                continue
            }

            // Check for wake word "Joy" or "Hey Joy" or "Ok Joy" or phonetic "Joey"
            val words = lower.split(Regex("\\s+"))
            val hasJoy = words.any { it == "joy" || it == "joey" || it == "jai" || it == "joye" } ||
                    lower.contains("hey joy") || lower.contains("ok joy") || lower.contains("suno joy")

            if (hasJoy) {
                val now = System.currentTimeMillis()
                if (now - lastTriggerTime > 2000) {
                    lastTriggerTime = now
                    Log.i(TAG, "WAKE WORD TRIGGERED: 'Joy'")
                    stopListening()
                    onWakeWordDetected("Joy")
                    break
                }
            }
        }
    }

    /**
     * Direct manual trigger (e.g. user tapped orb or notification action)
     */
    fun triggerManualActivation() {
        onWakeWordDetected("Joy")
    }
}
