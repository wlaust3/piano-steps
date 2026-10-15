package com.pianosteps.audio

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

data class PitchResult(
    val frequency: Double,
    /** Cumulative-mean-normalised difference at the chosen lag. 0 = perfectly periodic, ~1 = noise. */
    val cmndf: Double,
)

/**
 * Pure-Kotlin YIN pitch detector (de Cheveigné & Kawahara, 2002) with a candidate range biased
 * towards the lesson octaves and an extra octave-error correction pass.
 *
 * Needs [requiredSamples] samples of audio per call. Not thread safe (re-uses a scratch buffer).
 */
class PitchDetector(
    val sampleRate: Int = 44100,
    val minFreq: Double = 120.0,
    val maxFreq: Double = 1250.0,
    private val threshold: Double = 0.15,
    val windowSize: Int = 1024,
) {
    private val tauMin = max(2, floor(sampleRate / maxFreq).toInt())
    private val tauMax = ceil(sampleRate / minFreq).toInt()
    val requiredSamples: Int = windowSize + tauMax + 2

    private val cmndf = DoubleArray(tauMax + 2)

    /**
     * @param maxCmndf reject frames whose best candidate is less periodic than this (noise).
     * @return null if no clear pitch is present.
     */
    fun detect(buf: FloatArray, offset: Int = 0, maxCmndf: Double = 0.3): PitchResult? {
        require(buf.size - offset >= requiredSamples) { "need $requiredSamples samples" }
        computeCmndf(buf, offset)

        // 1) absolute threshold: first dip below threshold, then walk down to its local minimum
        var tau = -1
        var t = tauMin
        while (t < tauMax) {
            if (cmndf[t] < threshold) {
                while (t + 1 < tauMax && cmndf[t + 1] < cmndf[t]) t++
                tau = t
                break
            }
            t++
        }
        // 2) otherwise take the global minimum if it is periodic enough
        if (tau < 0) {
            var best = tauMin
            for (i in tauMin until tauMax) if (cmndf[i] < cmndf[best]) best = i
            if (cmndf[best] > maxCmndf) return null
            tau = best
        }
        if (cmndf[tau] > maxCmndf) return null

        // 3) octave-error correction: if half the lag is *also* a clear period, the true pitch is
        //    the octave above (YIN's classic "picked a sub-harmonic" failure on piano partials).
        var guard = 0
        while (guard++ < 2) {
            val half = tau / 2
            if (half < tauMin) break
            var bestHalf = -1
            for (i in max(tauMin, half - 2)..min(tauMax - 1, half + 2)) {
                if (bestHalf < 0 || cmndf[i] < cmndf[bestHalf]) bestHalf = i
            }
            if (bestHalf > 0 && cmndf[bestHalf] < threshold + 0.07 && cmndf[bestHalf] <= cmndf[tau] + 0.07) {
                tau = bestHalf
            } else break
        }

        val refined = parabolic(tau)
        return PitchResult(sampleRate / refined, cmndf[tau])
    }

    private fun computeCmndf(x: FloatArray, off: Int) {
        cmndf[0] = 1.0
        var running = 0.0
        for (tau in 1..tauMax) {
            var sum = 0.0
            for (j in 0 until windowSize) {
                val diff = (x[off + j] - x[off + j + tau]).toDouble()
                sum += diff * diff
            }
            running += sum
            cmndf[tau] = if (running > 1e-12) sum * tau / running else 1.0
        }
        cmndf[tauMax + 1] = 1.0
    }

    private fun parabolic(tau: Int): Double {
        if (tau <= 1 || tau >= tauMax) return tau.toDouble()
        val a = cmndf[tau - 1]
        val b = cmndf[tau]
        val c = cmndf[tau + 1]
        val denom = a - 2 * b + c
        if (abs(denom) < 1e-12) return tau.toDouble()
        return tau + 0.5 * (a - c) / denom
    }

    companion object {
        fun rms(buf: FloatArray, from: Int, to: Int): Double {
            if (to <= from) return 0.0
            var s = 0.0
            for (i in from until to) s += buf[i] * buf[i]
            return sqrt(s / (to - from))
        }
    }
}
