package com.example.ui

import android.media.AudioManager
import android.media.ToneGenerator

/**
 * Short correct/wrong tones for the "Sound Effects" setting. Uses the system tone generator, so no
 * audio assets are needed. Every call is guarded: some devices refuse to create a ToneGenerator.
 */
object SoundEffects {
    private var generator: ToneGenerator? = null

    private fun tones(): ToneGenerator? = generator ?: try {
        ToneGenerator(AudioManager.STREAM_MUSIC, VOLUME).also { generator = it }
    } catch (e: RuntimeException) {
        null
    }

    fun correct() = play(ToneGenerator.TONE_PROP_ACK, 160)

    fun wrong() = play(ToneGenerator.TONE_SUP_ERROR, 280)

    private fun play(tone: Int, durationMs: Int) {
        try {
            tones()?.startTone(tone, durationMs)
        } catch (e: RuntimeException) {
            // Audio unavailable; silently skip.
        }
    }

    private const val VOLUME = 70
}
