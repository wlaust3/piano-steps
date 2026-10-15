package com.pianosteps.audio

import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

object TestSignals {
    const val SR = 44100

    /** Sum of harmonics: amps[0] is the fundamental. */
    fun harmonic(freq: Double, amps: DoubleArray, n: Int = 2048, gain: Double = 0.3, phase: Double = 0.0): FloatArray =
        FloatArray(n) { i ->
            var s = 0.0
            for ((k, a) in amps.withIndex()) s += a * sin(2 * PI * freq * (k + 1) * i / SR + phase * (k + 1))
            (s * gain / amps.sum()).toFloat()
        }

    fun sine(freq: Double, n: Int = 2048, gain: Double = 0.3) = harmonic(freq, doubleArrayOf(1.0), n, gain)

    fun noise(n: Int = 2048, gain: Double = 0.2, seed: Int = 1): FloatArray {
        val r = Random(seed)
        return FloatArray(n) { ((r.nextDouble() * 2 - 1) * gain).toFloat() }
    }

    fun centsError(measuredHz: Double, expectedHz: Double): Double =
        1200.0 * Math.log(measuredHz / expectedHz) / Math.log(2.0)
}
