package com.pianosteps.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class PitchDetectorTest {
    private val detector = PitchDetector(TestSignals.SR)

    private fun assertDetects(buf: FloatArray, expectedHz: Double, centsTol: Double = 15.0) {
        val r = detector.detect(buf)
        assertNotNull("no pitch found near $expectedHz Hz", r)
        val err = TestSignals.centsError(r!!.frequency, expectedHz)
        assertTrue("expected $expectedHz Hz got ${r.frequency} Hz ($err cents off)", abs(err) <= centsTol)
    }

    @Test fun pureSinesAcrossLessonOctaves() {
        for (midi in listOf(48, 52, 55, 60, 62, 64, 67, 72, 76, 79, 84)) {
            val f = Notes.midiToFreq(midi)
            assertDetects(TestSignals.sine(f), f)
        }
    }

    @Test fun middleCWithinAFewCents() {
        val f = 261.6256
        val r = detector.detect(TestSignals.sine(f))!!
        assertEquals(f, r.frequency, 1.0)
    }

    @Test fun pianoLikeHarmonicsAreStable() {
        val amps = doubleArrayOf(1.0, 0.5, 0.3, 0.2, 0.1, 0.05)
        for (midi in listOf(48, 60, 64, 67, 72, 79)) {
            val f = Notes.midiToFreq(midi)
            assertDetects(TestSignals.harmonic(f, amps), f)
        }
    }

    @Test fun octaveError_strongSecondHarmonicDoesNotReadAsOctaveLow() {
        // Weak fundamental, dominant 2nd harmonic: a naive detector can lock on 2x or 0.5x.
        val amps = doubleArrayOf(0.35, 1.0, 0.5, 0.2)
        for (midi in listOf(48, 55, 60, 67)) {
            val f = Notes.midiToFreq(midi)
            val r = detector.detect(TestSignals.harmonic(f, amps))
            assertNotNull(r)
            val err = abs(TestSignals.centsError(r!!.frequency, f))
            assertTrue("$midi: detected ${r.frequency} for $f (octave error?)", err < 20.0)
        }
    }

    @Test fun octaveError_dominantThirdHarmonic() {
        val amps = doubleArrayOf(0.5, 0.4, 1.0, 0.3)
        val f = Notes.midiToFreq(55)
        val r = detector.detect(TestSignals.harmonic(f, amps))!!
        assertTrue(abs(TestSignals.centsError(r.frequency, f)) < 20.0)
    }

    @Test fun octaveCorrectionStepPicksHigherOctaveWhenHalfLagAlsoPeriodic() {
        // A signal with period T also repeats at T/2 only if its true pitch is 2x.
        // Pure 523.25 Hz must never be reported as 261.6 Hz.
        val f = 523.2511
        val r = detector.detect(TestSignals.sine(f))!!
        assertEquals(f, r.frequency, 4.0)
    }

    @Test fun silenceAndNoiseGiveNoPitch() {
        assertNull(detector.detect(FloatArray(2048)))
        assertNull(detector.detect(TestSignals.noise()))
    }

    @Test fun detectsQuietSignalsToo() {
        val f = Notes.midiToFreq(64)
        assertDetects(TestSignals.sine(f, gain = 0.01), f)
    }

    @Test fun foldIntoRangeFixesOctaveOutliers() {
        assertEquals(60.0, Notes.foldIntoRange(48.0, 53.5, 84.5), 1e-9)   // too low -> up
        assertEquals(84.0, Notes.foldIntoRange(96.0, 47.5, 84.5), 1e-9)   // too high -> down one octave
        assertEquals(72.0, Notes.foldIntoRange(96.0, 47.5, 76.5), 1e-9)   // ...or two, if needed
        assertEquals(64.2, Notes.foldIntoRange(64.2, 47.5, 84.5), 1e-9)   // already inside
    }

    @Test fun synthesizedDemoToneIsDetectedAtItsOwnPitch() {
        // the demo tone the app plays should itself be a clean, correctly pitched note
        for (midi in listOf(60, 62, 64)) {
            val pcm = ToneGenerator.generate(Notes.midiToFreq(midi), 400, TestSignals.SR)
            val buf = FloatArray(2048) { pcm[3000 + it] / 32768f }
            assertDetects(buf, Notes.midiToFreq(midi))
        }
    }

    @Test fun noteMath() {
        assertEquals(60.0, Notes.freqToMidi(261.6256), 1e-3)
        assertEquals(440.0, Notes.midiToFreq(69), 1e-9)
        assertEquals("C4", Notes.name(60))
        assertEquals("A#3", Notes.name(58))
    }
}
