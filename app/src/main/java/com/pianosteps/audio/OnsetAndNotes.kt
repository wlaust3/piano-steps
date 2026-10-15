package com.pianosteps.audio

import kotlin.math.abs

/** Detects the start of a sound (key press / clap) from the RMS envelope. */
class OnsetDetector(
    private val refractoryMs: Long = 180,
    private val riseFactor: Double = 1.8,
    private val historyFrames: Int = 4,
) {
    private val history = ArrayDeque<Double>()
    private var lastOnsetMs = Long.MIN_VALUE / 2

    fun feed(timeMs: Long, rms: Double, gateRms: Double): Boolean {
        val prev = if (history.isEmpty()) 0.0 else history.average()
        history.addLast(rms)
        while (history.size > historyFrames) history.removeFirst()
        val onset = rms >= gateRms && rms > prev * riseFactor && timeMs - lastOnsetMs >= refractoryMs
        if (onset) lastOnsetMs = timeMs
        return onset
    }

    fun reset() {
        history.clear()
        lastOnsetMs = Long.MIN_VALUE / 2
    }
}

data class HeardNote(val timeMs: Long, val midi: Double)

/**
 * Turns a stream of per-frame pitches into discrete "the child played a note" events.
 * A note is emitted once its pitch has been stable for [framesNeeded] frames, then not again until
 * the sound stops, the pitch moves, or a fresh onset is seen. This stops one long-held key being
 * counted as several attempts.
 */
class NoteStabilizer(
    private val framesNeeded: Int = 3,
    private val releaseFrames: Int = 3,
    private val maxDriftSemitones: Double = 0.8,
) {
    private val candidates = ArrayList<AnalysisFrame>()
    private var emitted = false
    private var emittedMidi = 0.0
    private var silentFrames = 0

    fun feed(frame: AnalysisFrame, onset: Boolean): HeardNote? {
        val midi = frame.midi
        if (!frame.voiced || midi == null) {
            silentFrames++
            candidates.clear()
            if (silentFrames >= releaseFrames) emitted = false
            return null
        }
        silentFrames = 0
        if (emitted) {
            if (onset || abs(midi - emittedMidi) > maxDriftSemitones) {
                emitted = false
                candidates.clear()
            } else return null
        }
        if (candidates.isNotEmpty() && candidates.any { abs(it.midi!! - midi) > maxDriftSemitones }) candidates.clear()
        candidates += frame
        if (candidates.size >= framesNeeded) {
            val sorted = candidates.map { it.midi!! }.sorted()
            val med = sorted[sorted.size / 2]
            val first = candidates.first().timeMs
            candidates.clear()
            emitted = true
            emittedMidi = med
            return HeardNote(first, med)
        }
        return null
    }

    fun reset() {
        candidates.clear()
        emitted = false
        silentFrames = 0
    }
}
