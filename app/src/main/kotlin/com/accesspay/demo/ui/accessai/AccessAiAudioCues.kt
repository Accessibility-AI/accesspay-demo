package com.accesspay.demo.ui.accessai

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin

/**
 * Distinct non-overlapping audio cues per AccessAI TalkBack spec (1a / 1h):
 * "Each status pairs an icon shape, a word and a distinct sound. Failure never resembles success."
 */
object AccessAiAudioCues {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private const val SAMPLE_RATE = 22050

    /** Soft rising tone when the mic opens. */
    fun micOpen() = playNotes(listOf(440.0 to 70, 587.33 to 90), volume = 0.18)

    /** Falling tone when the mic closes / AccessAI stops. */
    fun micClose() = playNotes(listOf(523.25 to 70, 349.23 to 110), volume = 0.18)

    /** One quiet tick on entering Thinking. */
    fun thinkingTick() = playNotes(listOf(660.0 to 28), volume = 0.10)

    /** Short neutral chime for Answer. */
    fun answerChime() = playNotes(listOf(523.25 to 90), volume = 0.16)

    /** Two-note question tone for Clarification. */
    fun clarifyQuestion() = playNotes(listOf(440.0 to 80, 554.37 to 120), volume = 0.18)

    /** Distinct "check" tone for Confirmation. */
    fun confirmationPrompt() = playNotes(listOf(493.88 to 65, 659.25 to 110), volume = 0.18)

    /** Two rising notes for Success. */
    fun success() = playNotes(listOf(523.25 to 85, 783.99 to 140), volume = 0.22)

    /** One low falling tone for Failure (never shares a pattern with success). */
    fun failure() = playNotes(listOf(311.13 to 110, 220.0 to 200), volume = 0.24)

    /** One low tone for Offline. */
    fun offline() = playNotes(listOf(246.94 to 160), volume = 0.18)

    /** Soft neutral tone for Speech not recognised. */
    fun notUnderstood() = playNotes(listOf(392.0 to 85), volume = 0.14)

    private fun playNotes(notes: List<Pair<Double, Int>>, volume: Double) {
        scope.launch {
            runCatching {
                val gapSamples = (SAMPLE_RATE * 0.025).toInt()
                val totalSamples = notes.sumOf { (SAMPLE_RATE * (it.second / 1000.0)).toInt() + gapSamples }
                val pcm = ShortArray(totalSamples)
                var offset = 0
                for ((freq, durationMs) in notes) {
                    val count = (SAMPLE_RATE * (durationMs / 1000.0)).toInt()
                    val fade = (count * 0.18).toInt().coerceAtLeast(1)
                    for (i in 0 until count) {
                        val env = when {
                            i < fade -> i.toDouble() / fade
                            i > count - fade -> (count - i).toDouble() / fade
                            else -> 1.0
                        }
                        val sample = sin(2.0 * PI * freq * i / SAMPLE_RATE) * env * volume * Short.MAX_VALUE
                        pcm[offset + i] = sample.toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                    }
                    offset += count + gapSamples
                }
                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build(),
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setSampleRate(SAMPLE_RATE)
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build(),
                    )
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .setBufferSizeInBytes(pcm.size * 2)
                    .build()
                track.write(pcm, 0, pcm.size)
                track.play()
                val totalMs = notes.sumOf { it.second + 25 } + 80L
                Thread.sleep(totalMs)
                track.stop()
                track.release()
            }
        }
    }
}
