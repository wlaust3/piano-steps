package com.pianosteps.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OnsetNoteTest {
    private val gate = 0.01

    @Test fun onsetFiresOnAttackNotOnSustainOrDecay() {
        val d = OnsetDetector()
        val envelope = listOf(0.0, 0.0, 0.2, 0.19, 0.18, 0.17, 0.15, 0.12, 0.1)
        val fired = envelope.mapIndexed { i, r -> d.feed(i * 23L, r, gate) }
        assertEquals(listOf(2), fired.withIndex().filter { it.value }.map { it.index })
    }

    @Test fun onsetRespectsGateAndRefractory() {
        val d = OnsetDetector(refractoryMs = 180)
        assertFalse(d.feed(0, 0.005, gate))            // below the gate
        assertTrue(d.feed(23, 0.2, gate))
        assertFalse(d.feed(46, 0.0, gate))
        assertFalse(d.feed(69, 0.3, gate))             // within refractory period
        assertFalse(d.feed(92, 0.0, gate))
        for (i in 4..10) d.feed(i * 23L, 0.0, gate)
        assertTrue(d.feed(400, 0.3, gate))             // a second press later is a new onset
    }

    private fun voiced(t: Long, midi: Double) = AnalysisFrame(t, 0.1, midi, midi, 0.05, true)
    private val silent = { t: Long -> AnalysisFrame.silent(t) }

    @Test fun stabilizerEmitsOncePerHeldNote() {
        val s = NoteStabilizer(framesNeeded = 3)
        val out = (0 until 20).mapNotNull { s.feed(voiced(it * 23L, 60.05), false) }
        assertEquals(1, out.size)
        assertEquals(60.05, out[0].midi, 1e-9)
    }

    @Test fun stabilizerNeedsSeveralConsistentFrames() {
        val s = NoteStabilizer(framesNeeded = 3)
        assertNull(s.feed(voiced(0, 60.0), false))
        assertNull(s.feed(voiced(23, 65.0), false))   // jumped: restart
        assertNull(s.feed(voiced(46, 60.0), false))
        assertNull(s.feed(voiced(69, 60.0), false))
        assertNotNull(s.feed(voiced(92, 60.0), false))
    }

    @Test fun stabilizerRearmsAfterSilenceAndPitchChange() {
        val s = NoteStabilizer(framesNeeded = 2, releaseFrames = 2)
        var t = 0L
        fun run(midi: Double, n: Int) = (0 until n).mapNotNull { s.feed(voiced(t++ * 23, midi), false) }
        assertEquals(1, run(60.0, 6).size)
        assertEquals(1, run(62.0, 6).size)              // legato move to D: new note
        repeat(3) { s.feed(silent(t++ * 23), false) }
        assertEquals(1, run(62.0, 6).size)              // same key again after a gap
    }

    @Test fun stabilizerRearmsOnFreshOnsetOfSameNote() {
        val s = NoteStabilizer(framesNeeded = 2)
        assertNull(s.feed(voiced(0, 60.0), false))
        assertNotNull(s.feed(voiced(23, 60.0), false))
        assertNull(s.feed(voiced(46, 60.0), false))
        assertNull(s.feed(voiced(69, 60.0), true))      // re-strike without releasing
        assertNotNull(s.feed(voiced(92, 60.0), false))
    }

    @Test fun mutedFramesNeverProduceNotes() {
        val s = NoteStabilizer()
        assertNull((0 until 20).firstNotNullOfOrNull { s.feed(AnalysisFrame.silent(it * 23L), false) })
    }
}
