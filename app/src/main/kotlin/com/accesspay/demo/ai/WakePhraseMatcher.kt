package com.accesspay.demo.ai

import java.util.Locale

/**
 * Detects spoken activation phrases such as "Hello Access AI" or "Start Access AI"
 * to open AccessAI mode hands-free when AccessPay is running in the foreground.
 */
object WakePhraseMatcher {
    private val activationWords = listOf(
        "hello",
        "hey",
        "hi",
        "start",
        "open",
        "launch",
        "turn on",
    )

    private val assistantNames = listOf(
        "accessai",
        "access ai",
        "access a i",
        "access eye",
    )

    fun matches(transcript: String): Boolean {
        val cleaned = transcript
            .lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        if (cleaned.isEmpty()) return false
        for (verb in activationWords) {
            for (name in assistantNames) {
                if (cleaned.contains("$verb $name")) return true
            }
        }
        return false
    }
}
