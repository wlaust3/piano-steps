package com.pianosteps.audio

import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt

/** Equal-temperament helpers (A4 = 440 Hz, MIDI numbers: C4 = 60). */
object Notes {
    const val A4_HZ = 440.0
    const val C3 = 48
    const val C4 = 60
    const val D4 = 62
    const val E4 = 64
    const val G4 = 67
    const val C5 = 72
    const val C6 = 84

    private val NAMES = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
    private val BLACK = setOf(1, 3, 6, 8, 10)

    fun midiToFreq(midi: Double): Double = A4_HZ * 2.0.pow((midi - 69.0) / 12.0)
    fun midiToFreq(midi: Int): Double = midiToFreq(midi.toDouble())
    fun freqToMidi(freq: Double): Double = 69.0 + 12.0 * ln(freq / A4_HZ) / ln(2.0)

    fun pitchClass(midi: Int): Int = ((midi % 12) + 12) % 12
    fun isBlack(midi: Int): Boolean = pitchClass(midi) in BLACK
    fun letter(midi: Int): String = NAMES[pitchClass(midi)]
    fun name(midi: Int): String = letter(midi) + (Math.floorDiv(midi, 12) - 1)

    /** Signed distance in cents from [target] to a fractional MIDI value. */
    fun centsFrom(midi: Double, target: Int): Double = (midi - target) * 100.0

    /**
     * Octave-error correction: if a detected pitch lies outside the preferred range, shift it by
     * whole octaves until it fits (or as close as it can get).
     */
    fun foldIntoRange(midi: Double, lo: Double, hi: Double): Double {
        var m = midi
        var guard = 0
        while (m < lo && guard++ < 6) m += 12.0
        while (m > hi && guard++ < 12) m -= 12.0
        return m
    }

    fun nearestMidi(midi: Double): Int = midi.roundToInt()
}
