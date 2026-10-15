package com.pianosteps.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Tells the microphone pipeline to ignore input (e.g. while the app itself is playing a tone). */
class MuteGate {
    @Volatile private var mutedUntil = 0L
    fun muteFor(ms: Long) {
        mutedUntil = max(mutedUntil, SystemClock.elapsedRealtime() + ms)
    }
    fun isMuted(nowMs: Long = SystemClock.elapsedRealtime()): Boolean = nowMs < mutedUntil
}

/** Pure PCM generation for a soft, piano-ish tone. Unit-testable (no Android deps). */
object ToneGenerator {
    private val harmonics = doubleArrayOf(1.0, 0.45, 0.22, 0.10, 0.05)

    fun generate(freq: Double, durationMs: Int, sampleRate: Int = 44100, volume: Double = 0.5): ShortArray {
        val n = sampleRate * durationMs / 1000
        val out = ShortArray(n)
        val attack = 0.008 * sampleRate
        val release = 0.06 * sampleRate
        val norm = harmonics.sum()
        for (i in 0 until n) {
            val t = i.toDouble() / sampleRate
            var s = 0.0
            for ((k, a) in harmonics.withIndex()) {
                val f = freq * (k + 1)
                if (f < sampleRate / 2.2) s += a * sin(2 * PI * f * t) * exp(-t * (1.5 + k * 1.2))
            }
            val env = min(1.0, i / attack) * min(1.0, (n - i) / release)
            out[i] = (s / norm * env * volume * Short.MAX_VALUE).toInt().coerceIn(-32767, 32767).toShort()
        }
        return out
    }
}

/** Plays demo notes with [AudioTrack]; mutes the mic pipeline while playing. */
class ToneSynth(private val muteGate: MuteGate) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private val sampleRate = 44100

    /** Plays [midis] one after another; each sounds for [noteMs], a new one starts every [gapMs]. */
    fun play(midis: List<Int>, noteMs: Int = 900, gapMs: Int = noteMs) {
        job?.cancel()
        val gap = max(gapMs, noteMs)
        val total = gap.toLong() * (midis.size - 1) + noteMs
        muteGate.muteFor(total + TAIL_MS)
        job = scope.launch {
            for ((i, m) in midis.withIndex()) {
                playOne(m, noteMs)
                if (i < midis.size - 1 && gap > noteMs) delay((gap - noteMs).toLong())
            }
        }
    }

    fun play(midi: Int, noteMs: Int = 1000) = play(listOf(midi), noteMs)

    private suspend fun playOne(midi: Int, noteMs: Int) {
        val pcm = ToneGenerator.generate(Notes.midiToFreq(midi), noteMs, sampleRate)
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build(),
            )
            .setBufferSizeInBytes(pcm.size * 2)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
        try {
            track.write(pcm, 0, pcm.size)
            track.play()
            // MODE_STATIC plays asynchronously; wait so consecutive tones don't overlap
            delay(noteMs.toLong())
        } finally {
            runCatching { track.stop() }
            track.release()
        }
    }

    fun stop() {
        job?.cancel()
    }

    companion object {
        /** Keep ignoring the mic this long after a tone ends (speaker/room decay). */
        const val TAIL_MS = 350L
    }
}
