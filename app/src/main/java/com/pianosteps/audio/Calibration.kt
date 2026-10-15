package com.pianosteps.audio

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** What the app learned about this keyboard + room during the parent's calibration step. */
data class Calibration(
    /** Typical RMS level of the room with nobody playing (0..1 full scale). */
    val noiseFloorRms: Double = DEFAULT_NOISE,
    /** How many cents sharp (+) or flat (-) this instrument sounds versus A440. */
    val centsOffset: Double = 0.0,
    /** MIDI note -> minimum RMS to count as "played" for notes near it. */
    val noteThresholds: Map<Int, Double> = emptyMap(),
    /** Per-instrument limit on how noisy/inharmonic a frame can be (YIN cmndf). */
    val maxCmndf: Double = 0.3,
    val calibrated: Boolean = false,
) {
    /** General "something is sounding" level, used for onset detection. */
    val gateRms: Double get() = max(noiseFloorRms * 2.5, MIN_GATE)

    fun thresholdFor(midi: Double): Double {
        if (noteThresholds.isEmpty()) return gateRms
        val nearest = noteThresholds.keys.minByOrNull { abs(it - midi) } ?: return gateRms
        return max(noteThresholds.getValue(nearest), gateRms)
    }

    fun encode(): String = listOf(
        noiseFloorRms, centsOffset, maxCmndf, if (calibrated) 1 else 0,
        noteThresholds.entries.joinToString(",") { "${it.key}:${it.value}" },
    ).joinToString(";")

    companion object {
        const val DEFAULT_NOISE = 0.004
        const val MIN_GATE = 0.006

        fun decode(s: String?): Calibration {
            if (s.isNullOrBlank()) return Calibration()
            return try {
                val p = s.split(";")
                Calibration(
                    noiseFloorRms = p[0].toDouble(),
                    centsOffset = p[1].toDouble(),
                    maxCmndf = p[2].toDouble(),
                    calibrated = p[3] == "1",
                    noteThresholds = p.getOrElse(4) { "" }.split(",").filter { it.isNotBlank() }
                        .associate { e -> e.substringBefore(":").toInt() to e.substringAfter(":").toDouble() },
                )
            } catch (e: Exception) {
                Calibration()
            }
        }
    }
}

/** One frame of analysed audio. */
data class AnalysisFrame(
    val timeMs: Long,
    val rms: Double,
    /** MIDI pitch with the instrument tuning offset applied; null when no clear pitch. */
    val midi: Double?,
    /** MIDI pitch as measured (no tuning offset); used by calibration itself. */
    val rawMidi: Double?,
    val cmndf: Double,
    /** True if loud enough (vs. noise floor / per-note threshold) and pitched. */
    val voiced: Boolean,
) {
    companion object {
        fun silent(timeMs: Long) = AnalysisFrame(timeMs, 0.0, null, null, 1.0, false)
    }
}

/** Frames captured while the parent played one prompted reference note. */
class NoteCapture(val targetMidi: Int, val frames: List<AnalysisFrame>)

object CalibrationMath {
    const val MIN_GOOD_FRAMES = 8
    /** Accept a frame as "the prompted note" if within this many semitones of target. */
    const val GOOD_FRAME_SEMITONES = 0.7

    fun isGoodFrame(f: AnalysisFrame, target: Int): Boolean {
        val m = f.rawMidi ?: return false
        return abs(m - target) <= GOOD_FRAME_SEMITONES && f.rms > Calibration.MIN_GATE
    }

    fun percentile(values: List<Double>, p: Double): Double {
        if (values.isEmpty()) return 0.0
        val s = values.sorted()
        val idx = ((s.size - 1) * p).toInt().coerceIn(0, s.size - 1)
        return s[idx]
    }

    /**
     * Combine the silence measurement and the prompted notes into a [Calibration].
     * Returns null if no prompted note could be heard at all.
     */
    fun compute(silenceRms: List<Double>, captures: List<NoteCapture>): Calibration? {
        val noise = if (silenceRms.isEmpty()) Calibration.DEFAULT_NOISE else max(percentile(silenceRms, 0.9), 0.0005)
        val thresholds = HashMap<Int, Double>()
        val offsets = ArrayList<Double>()
        val clarities = ArrayList<Double>()
        for (c in captures) {
            val good = c.frames.filter { isGoodFrame(it, c.targetMidi) }
            if (good.size < MIN_GOOD_FRAMES) continue
            // skip the attack transient, use the steady part
            val steady = good.drop(min(2, good.size / 4))
            val amp = percentile(steady.map { it.rms }, 0.25)
            thresholds[c.targetMidi] = max(amp * 0.35, noise * 2.5)
            offsets += percentile(steady.map { Notes.centsFrom(it.rawMidi!!, c.targetMidi) }, 0.5)
            clarities += steady.map { it.cmndf }
        }
        if (thresholds.isEmpty()) return null
        val offset = percentile(offsets, 0.5).coerceIn(-50.0, 50.0)
        val maxC = (percentile(clarities, 0.9) + 0.1).coerceIn(0.2, 0.4)
        return Calibration(noise, offset, thresholds, maxC, calibrated = true)
    }
}
