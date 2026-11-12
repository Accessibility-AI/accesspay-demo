package com.accesspay.demo.ai

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/**
 * Passively listens in the foreground for wake phrases ("Hello Access AI", "Start Access AI")
 * via Android's [SpeechRecognizer] and triggers AccessAI mode when matched.
 */
class WakePhraseController(
    context: Context,
    private val onWakeDetected: () -> Unit,
) {
    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var active = false
    private var triggered = false

    private val restartTask = Runnable {
        if (active && !triggered) startCycle()
    }

    fun start() {
        if (active) return
        if (!SpeechRecognizer.isRecognitionAvailable(appContext)) return
        active = true
        triggered = false
        mainHandler.post { startCycle() }
    }

    fun stop() {
        active = false
        mainHandler.removeCallbacks(restartTask)
        mainHandler.post {
            runCatching {
                recognizer?.cancel()
                recognizer?.destroy()
            }
            recognizer = null
        }
    }

    private fun startCycle() {
        if (!active || triggered) return
        runCatching {
            recognizer?.destroy()
            val sr = SpeechRecognizer.createSpeechRecognizer(appContext)
            recognizer = sr
            sr.setRecognitionListener(WakeListener())
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, appContext.packageName)
            }
            sr.startListening(intent)
        }.onFailure {
            scheduleRestart(1500L)
        }
    }

    private fun checkBundle(bundle: Bundle?) {
        if (!active || triggered) return
        val lines = bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
        if (lines.any { WakePhraseMatcher.matches(it) }) {
            triggered = true
            stop()
            mainHandler.post { onWakeDetected() }
        }
    }

    private fun scheduleRestart(delayMs: Long) {
        if (!active || triggered) return
        mainHandler.removeCallbacks(restartTask)
        mainHandler.postDelayed(restartTask, delayMs)
    }

    private inner class WakeListener : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) = Unit
        override fun onBeginningOfSpeech() = Unit
        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() = Unit
        override fun onEvent(eventType: Int, params: Bundle?) = Unit

        override fun onPartialResults(partialResults: Bundle?) = checkBundle(partialResults)

        override fun onResults(results: Bundle?) {
            checkBundle(results)
            if (active && !triggered) scheduleRestart(700L)
        }

        override fun onError(error: Int) {
            if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                stop()
                return
            }
            val delay = if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) 1500L else 900L
            scheduleRestart(delay)
        }
    }
}
