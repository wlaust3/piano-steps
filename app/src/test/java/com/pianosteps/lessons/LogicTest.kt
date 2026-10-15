package com.pianosteps.lessons

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteMatcherTest {
    @Test fun exactHit() {
        val r = NoteMatcher.judge(60, 60.2, 1, false)
        assertEquals(MatchKind.EXACT, r.kind)
        assertTrue(r.counts)
        assertFalse(r.glow)
    }

    @Test fun halfStepOffCountsAsHitAndGlowsWhenToleranceIsOne() {
        for (heard in listOf(59.0, 61.0)) {
            val r = NoteMatcher.judge(60, heard, 1, false)
            assertEquals(MatchKind.NEAR_HIT, r.kind)
            assertTrue(r.counts)
            assertTrue("correct key should glow", r.glow)
        }
    }

    @Test fun nearMissIsNotCountedButStillGentle() {
        val strict = NoteMatcher.judge(60, 61.0, 0, false)
        assertEquals(MatchKind.NEAR_MISS, strict.kind)
        assertFalse(strict.counts)
        assertTrue(strict.glow)
        val twoOff = NoteMatcher.judge(60, 62.0, 1, false)
        assertEquals(MatchKind.NEAR_MISS, twoOff.kind)
    }

    @Test fun farNotesAreMissesWithDirection() {
        val low = NoteMatcher.judge(64, 60.0, 1, false)
        assertEquals(MatchKind.MISS, low.kind)
        assertEquals(1, low.direction)       // target is higher
        val high = NoteMatcher.judge(60, 67.0, 1, false)
        assertEquals(-1, high.direction)
    }

    @Test fun toleranceTwoAcceptsWholeStep() {
        assertTrue(NoteMatcher.judge(60, 62.0, 2, false).counts)
        assertFalse(NoteMatcher.judge(60, 63.0, 2, false).counts)
    }

    @Test fun anyOctaveFoldsDistance() {
        assertEquals(MatchKind.EXACT, NoteMatcher.judge(60, 72.0, 1, true).kind)
        assertEquals(MatchKind.EXACT, NoteMatcher.judge(60, 48.1, 1, true).kind)
        assertEquals(MatchKind.NEAR_HIT, NoteMatcher.judge(60, 73.0, 1, true).kind)  // C#5 vs C4
        assertEquals(MatchKind.MISS, NoteMatcher.judge(60, 72.0, 1, false).kind)
    }
}

class RhythmRoundTest {
    private val spec = RhythmSpec(bpm = 60, beats = 4, countInBeats = 2, requiredHits = 3)

    @Test fun beatTimesAreSpacedByTheTempo() {
        val r = RhythmRound(spec, startMs = 1000)
        assertEquals(1000.0 + 2000, r.beatTime(0), 1e-9)
        assertEquals(1000.0 + 5000, r.beatTime(3), 1e-9)
        assertEquals(1000.0, 60000.0 / spec.bpm * 0 + r.startMs.toDouble(), 1e-9)
    }

    @Test fun hitsWithinTheGenerousWindowCount() {
        val r = RhythmRound(spec, 0, windowFraction = 0.4)
        assertEquals(0, r.onOnset(2000 + 399))       // 0.4 of 1000 ms
        assertEquals(1, r.onOnset(3000 - 350))       // early
        assertNull(r.onOnset(4000 + 450))            // outside window
        assertEquals(2, r.onOnset(4000 + 100))
    }

    @Test fun countInTapsAndDoubleTapsDoNotCount() {
        val r = RhythmRound(spec, 0)
        assertNull(r.onOnset(0))                      // count-in beat
        assertNull(r.onOnset(1000))
        assertEquals(0, r.onOnset(2050))
        assertNull(r.onOnset(2100))                   // same beat can't be claimed twice
    }

    @Test fun successNeedsRequiredHits() {
        val r = RhythmRound(spec, 0)
        r.onOnset(2000); r.onOnset(3100)
        assertFalse(r.success())
        r.onOnset(3900)
        assertTrue(r.success())
        assertEquals(3, r.hitCount)
    }

    @Test fun roundFinishesAfterLastWindow() {
        val r = RhythmRound(spec, 0, 0.4)
        assertFalse(r.isFinished(5300))
        assertTrue(r.isFinished(5400))
    }

    @Test fun windowIsConfigurable() {
        val tight = RhythmRound(spec, 0, 0.1)
        assertNull(tight.onOnset(2300))
        assertEquals(0, tight.onOnset(2090))
    }

    @Test fun ringPhaseFallsTowardsEachBeat() {
        val r = RhythmRound(spec, 0)
        assertEquals(1.0, r.phaseToNextBeat(-500), 1e-9)
        assertEquals(1.0, r.phaseToNextBeat(2000), 1e-9)      // just landed
        assertEquals(0.5, r.phaseToNextBeat(2500), 1e-9)
        assertTrue(r.phaseToNextBeat(2990) < 0.02)
    }
}

class StreakTrackerTest {
    @Test fun threeCleanRoundsInARowCelebrateThenRestart() {
        val s = StreakTracker(3)
        assertEquals(StreakResult(1, false), s.onRoundCompleted(true))
        assertEquals(StreakResult(2, false), s.onRoundCompleted(true))
        assertEquals(StreakResult(3, true), s.onRoundCompleted(true))
        assertEquals(0, s.count)
        assertEquals(StreakResult(1, false), s.onRoundCompleted(true))
    }

    @Test fun aRoundThatNeededHelpQuietlyRestartsTheCount() {
        val s = StreakTracker(3)
        s.onRoundCompleted(true); s.onRoundCompleted(true)
        assertEquals(StreakResult(0, false), s.onRoundCompleted(false))
        assertFalse(s.onRoundCompleted(true).celebrate)
        assertFalse(s.onRoundCompleted(true).celebrate)
        assertTrue(s.onRoundCompleted(true).celebrate)
    }

    @Test fun sixInARowGivesTwoCelebrations() {
        val s = StreakTracker(3)
        assertEquals(2, (1..6).count { s.onRoundCompleted(true).celebrate })
    }
}

class NoteRoundTest {
    private val lesson = Lessons.byId("three-steps-up")

    @Test fun playingTheNotesInOrderCompletesTheRound() {
        val r = NoteRound(lesson, 1, false)
        assertTrue(r.onNote(60.1) is StepOutcome.Advanced)
        assertTrue(r.onNote(62.0) is StepOutcome.Advanced)
        val done = r.onNote(64.0)
        assertTrue(done is StepOutcome.Completed)
        assertTrue((done as StepOutcome.Completed).clean)
    }

    @Test fun nearMissOnStepGlowsAndNeverFails() {
        val r = NoteRound(lesson, 0, false)           // strict: half step off is not a hit
        val o = r.onNote(61.0) as StepOutcome.Hint
        assertTrue(o.showGlow)
        assertEquals(0, r.stepIndex)                  // still on the first step, no penalty
        assertTrue(r.onNote(60.0) is StepOutcome.Advanced)
    }

    @Test fun halfStepOffAdvancesWhenTolerant() {
        val r = NoteRound(lesson, 1, false)
        val o = r.onNote(61.0) as StepOutcome.Advanced
        assertEquals(MatchKind.NEAR_HIT, o.result.kind)
        assertEquals(1, r.stepIndex)
    }

    @Test fun farMissesGlowAfterTwoTriesAndManyMissesMeanNotClean() {
        val r = NoteRound(Lessons.byId("find-the-key"), 1, false)
        assertFalse((r.onNote(70.0) as StepOutcome.Hint).showGlow)
        assertTrue((r.onNote(70.0) as StepOutcome.Hint).showGlow)
        r.onNote(70.0); r.onNote(70.0)
        val done = r.onNote(60.0) as StepOutcome.Completed
        assertFalse(done.clean)
    }
}

class LessonDataTest {
    @Test fun starterLessonsAreWellFormed() {
        assertEquals(5, Lessons.all.size)
        assertEquals(Lessons.all.size, Lessons.all.map { it.id }.toSet().size)
        for (l in Lessons.all) {
            assertTrue(l.title.isNotBlank() && l.teaches.isNotBlank() && l.intro.isNotBlank())
            l.steps.forEach { assertTrue(it.prompt.isNotBlank()); assertTrue(it.midi in 48..84) }
        }
    }

    @Test fun directionsAreDerivedFromNotes() {
        val up = Lessons.byId("three-steps-up")
        assertEquals(listOf(0, 1, 1), (0..2).map { up.directionAt(it) })
        val down = Lessons.byId("down-we-go")
        assertEquals(listOf(0, -1, -1), (0..2).map { down.directionAt(it) })
    }

    @Test fun unknownLessonIdFallsBackToFirst() {
        assertEquals(Lessons.all.first(), Lessons.byId("nope"))
    }
}

class KeyboardPolicyTest {
    private val range = 60..64

    @Test fun relevantRangeStartsAndEndsOnWhiteKeysAndHasAtLeastEightWhites() {
        val r = KeyboardPolicy.relevantRange(range)
        assertTrue(r.first <= 60 - 3 && r.last >= 64 + 3)
        assertFalse(com.pianosteps.audio.Notes.isBlack(r.first))
        assertFalse(com.pianosteps.audio.Notes.isBlack(r.last))
        assertTrue(KeyboardPolicy.whiteCount(r) >= 8)
    }

    @Test fun autoModeAdaptsToScreenSize() {
        assertEquals(KeyboardDisplay.RELEVANT, KeyboardPolicy.decide(KeyboardMode.AUTO, 411f, 800f, range).display)
        assertEquals(KeyboardDisplay.COLLAPSED, KeyboardPolicy.decide(KeyboardMode.AUTO, 800f, 360f, range).display)
        assertEquals(KeyboardDisplay.HIDDEN, KeyboardPolicy.decide(KeyboardMode.AUTO, 300f, 300f, range).display)
        assertEquals(KeyboardDisplay.FULL, KeyboardPolicy.decide(KeyboardMode.AUTO, 1280f, 800f, range).display)
    }

    @Test fun manualModesOverrideAuto() {
        assertEquals(KeyboardDisplay.HIDDEN, KeyboardPolicy.decide(KeyboardMode.HIDDEN, 1280f, 800f, range).display)
        assertEquals(KeyboardDisplay.FULL, KeyboardPolicy.decide(KeyboardMode.FULL, 411f, 800f, range).display)
        assertEquals(KeyboardDisplay.COLLAPSED, KeyboardPolicy.decide(KeyboardMode.COLLAPSED, 1280f, 800f, range).display)
    }

    @Test fun fullKeyboardWidensWithScreenWidthAndStillContainsLessonNotes() {
        val narrow = KeyboardPolicy.decide(KeyboardMode.FULL, 411f, 800f, range).range
        val wide = KeyboardPolicy.decide(KeyboardMode.FULL, 1280f, 800f, range).range
        assertTrue(wide.count() > narrow.count())
        assertTrue(range.all { it in narrow } && range.all { it in wide })
    }
}

class ParentGateTest {
    @Test fun answersAreChecked() {
        val q = GateQuestion(7, 8)
        assertTrue(q.check("56"))
        assertTrue(q.check(" 56 "))
        assertFalse(q.check("55"))
        assertFalse(q.check(""))
        assertFalse(q.check("abc"))
    }

    @Test fun generatedQuestionsAreBeyondAFiveYearOld() {
        repeat(50) {
            val q = ParentGate.newQuestion()
            assertTrue(q.answer >= 24)
        }
    }
}
