package com.pianosteps.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class AnalyzerCalibrationTest {
    private fun frame(t: Long, rms: Double, midi: Double?, cmndf: Double = 0.05) =
        AnalysisFrame(t, rms, midi, midi, cmndf, midi != null)

    @Test fun analyzerGatesQuietAudio() {
        val a = FrameAnalyzer(TestSignals.SR, { Calibration() })
        val loud = a.analyze(TestSignals.sine(261.63, gain = 0.2), 0)
        assertTrue(loud.voiced)
        assertEquals(60.0, loud.midi!!, 0.2)
        val quiet = a.analyze(TestSignals.sine(261.63, gain = 0.001), 0)
        assertFalse(quiet.voiced)
        assertNull(quiet.midi)
    }

    @Test fun analyzerAppliesTuningOffset() {
        // instrument is 30 cents sharp; calibration says so -> reported pitch is corrected to C4
        val sharp = Notes.midiToFreq(60.30)
        val a = FrameAnalyzer(TestSignals.SR, { Calibration(centsOffset = 30.0, calibrated = true) })
        val f = a.analyze(TestSignals.sine(sharp, gain = 0.2), 0)
        assertEquals(60.0, f.midi!!, 0.08)
        assertEquals(60.30, f.rawMidi!!, 0.08)
    }

    @Test fun analyzerFoldsOutOfRangePitchesIntoLessonOctaves() {
        val a = FrameAnalyzer(TestSignals.SR, { Calibration() }, preferredLo = 59.5, preferredHi = 71.5)
        val f = a.analyze(TestSignals.sine(Notes.midiToFreq(72), gain = 0.2), 0)
        assertEquals(60.0, f.midi!!, 0.2) // C5 folded down into C4..B4 window
    }

    @Test fun calibrationComputesNoiseTuningAndThresholds() {
        val silence = List(80) { 0.003 + (it % 4) * 0.0005 }
        fun capture(target: Int, centsOff: Double, rms: Double): NoteCapture {
            val frames = List(40) { i ->
                frame(i * 23L, rms * (if (i < 3) 0.5 else 1.0), target + centsOff / 100.0 + (i % 3 - 1) * 0.01)
            }
            return NoteCapture(target, frames)
        }
        val cal = CalibrationMath.compute(
            silence, listOf(capture(60, 20.0, 0.08), capture(67, 22.0, 0.05), capture(72, 18.0, 0.03)),
        )
        assertNotNull(cal)
        cal!!
        assertTrue(cal.calibrated)
        assertEquals(20.0, cal.centsOffset, 3.0)
        assertTrue(cal.noiseFloorRms in 0.003..0.006)
        assertEquals(3, cal.noteThresholds.size)
        // louder note -> higher threshold; every threshold is above the general gate
        assertTrue(cal.noteThresholds.getValue(60) > cal.noteThresholds.getValue(72))
        assertTrue(cal.noteThresholds.values.all { it >= cal.gateRms })
        // threshold lookup uses the nearest calibrated note
        assertEquals(cal.noteThresholds.getValue(60), cal.thresholdFor(61.0), 1e-12)
        assertEquals(cal.noteThresholds.getValue(72), cal.thresholdFor(71.0), 1e-12)
    }

    @Test fun calibrationIgnoresWrongNotesAndFailsWhenNothingHeard() {
        val wrong = NoteCapture(60, List(40) { frame(it * 23L, 0.05, 67.0) })
        assertNull(CalibrationMath.compute(listOf(0.003), listOf(wrong)))
        val tooShort = NoteCapture(60, List(4) { frame(it * 23L, 0.05, 60.0) })
        assertNull(CalibrationMath.compute(listOf(0.003), listOf(tooShort)))
    }

    @Test fun calibrationOffsetIsClamped() {
        val frames = List(40) { frame(it * 23L, 0.05, 60.0) }
        val cal = CalibrationMath.compute(listOf(0.003), listOf(NoteCapture(60, frames)))!!
        assertTrue(abs(cal.centsOffset) <= 50.0)
    }

    @Test fun calibrationRoundTripsThroughStorageString() {
        val c = Calibration(0.0123, -17.5, mapOf(60 to 0.02, 72 to 0.015), 0.27, true)
        assertEquals(c, Calibration.decode(c.encode()))
        assertEquals(Calibration(), Calibration.decode(null))
        assertEquals(Calibration(), Calibration.decode("garbage"))
    }
}
