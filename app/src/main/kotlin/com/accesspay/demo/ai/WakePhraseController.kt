package com.accesspay.demo.ai

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.accessai.android.AndroidSpeechInput
import java.util.Locale

/**
 * Passively listens in the foreground for wake phrases ("Hello Access AI", "Start Access AI")
 * using the SDK's [AndroidSpeechInput] and launches AccessAI mode once detected.
 */
class WakePhraseController(
    context: Context,
    private val onWakeDetected: () -> Unit,
) {
    private val speech = AndroidSpeechInput(context.applicationContext, Locale.getDefault())
    private val handler = Handler(Looper.getMainLooper())
    private var active = false
    private var triggered = false

    private val restartRunnable = Runnable {
        if (active && !triggered) beginListening()
    }

    fun start() {
        if (active) return
        if (!speech.isAvailable()) return
        active = true
        triggered = false
        beginListening()
    }

    fun stop() {
        active = false
        handler.removeCallbacks(restartRunnable)
        speech.cancel()
    }

    private fun beginListening() {
        if (!active || triggered) return
        speech.start(object : AndroidSpeechInput.Listener {
            override fun onPartial(text: String) {
                handleTranscript(text)
            }

            override fun onResult(text: String) {
                handleTranscript(text)
            }

            override fun onError(message: String) {
                scheduleRestart(delayMs = 1200L)
            }

            override fun onEnd() {
                scheduleRestart(delayMs = 700L)
            }
        })
    }

    private fun handleTranscript(text: String) {
        if (!active || triggered) return
        if (WakePhraseMatcher.matches(text)) {
            triggered = true
            stop()
            handler.post { onWakeDetected() }
        }
    }

    private fun scheduleRestart(delayMs: Long) {
        if (!active || triggered) return
        handler.removeCallbacks(restartRunnable)
        handler.postDelayed(restartRunnable, delayMs)
    }
}
