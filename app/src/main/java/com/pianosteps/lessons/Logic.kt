package com.pianosteps.lessons

import kotlin.math.abs
import kotlin.math.roundToInt

enum class MatchKind { EXACT, NEAR_HIT, NEAR_MISS, MISS }

data class MatchResult(
    val kind: MatchKind,
    /** Whole semitones between the (rounded) heard note and the target, after optional octave folding. */
    val semitones: Int,
    /** +1 if the target is higher than what was heard, -1 lower, 0 same. */
    val direction: Int,
) {
    val counts: Boolean get() = kind == MatchKind.EXACT || kind == MatchKind.NEAR_HIT
    /** The target key should softly glow (near-miss or near-hit: "this one!"). */
    val glow: Boolean get() = kind != MatchKind.EXACT
}

object NoteMatcher {
    /**
     * @param toleranceSemitones notes within this many semitones count as a hit (0 = exact only).
     * @param anyOctave if true, the right note in any octave counts (a 5-year-old rarely finds the
     *   right octave by ear).
     * Notes up to 2 semitones away that don't count are reported as NEAR_MISS so the UI can gently
     * highlight the right key instead of simply failing.
     */
    fun judge(target: Int, heardMidi: Double, toleranceSemitones: Int, anyOctave: Boolean): MatchResult {
        var diff = target - heardMidi.roundToInt()
        if (anyOctave) diff -= 12 * Math.floorDiv(diff + 6, 12)   // fold into -6..5
        val d = abs(diff)
        val dir = Integer.signum(diff)
        val kind = when {
            d == 0 -> MatchKind.EXACT
            d <= toleranceSemitones -> MatchKind.NEAR_HIT
            d <= 2 -> MatchKind.NEAR_MISS
            else -> MatchKind.MISS
        }
        return MatchResult(kind, d, dir)
    }
}

/**
 * Rhythm round: [countIn] pulse-only beats, then [spec.beats] beats to land on. An onset within
 * +-[windowFraction] of a beat claims that beat. No metronome precision needed.
 */
class RhythmRound(
    val spec: RhythmSpec,
    val startMs: Long,
    val windowFraction: Double = 0.4,
) {
    val beatMs: Double = 60000.0 / spec.bpm
    val hits = BooleanArray(spec.beats)

    fun beatTime(k: Int): Double = startMs + (spec.countInBeats + k) * beatMs

    /** Time at which the round is over (last beat + window). */
    val endMs: Long get() = (beatTime(spec.beats - 1) + windowFraction * beatMs).toLong()

    /** @return index of the beat claimed by this onset, or null if outside every window / already claimed. */
    fun onOnset(timeMs: Long): Int? {
        var best = -1
        var bestDist = Double.MAX_VALUE
        for (k in 0 until spec.beats) {
            val d = abs(timeMs - beatTime(k))
            if (d <= windowFraction * beatMs && !hits[k] && d < bestDist) {
                best = k
                bestDist = d
            }
        }
        if (best < 0) return null
        hits[best] = true
        return best
    }

    fun isFinished(nowMs: Long): Boolean = nowMs >= endMs
    val hitCount: Int get() = hits.count { it }
    fun success(): Boolean = hitCount >= spec.requiredHits

    /** 1.0 right after a beat, falling to 0.0 as the next beat arrives (drives the approaching ring). */
    fun phaseToNextBeat(nowMs: Long): Double {
        val t = (nowMs - startMs) / beatMs
        if (t < 0) return 1.0
        return 1.0 - (t - Math.floor(t))
    }
}

data class StreakResult(val count: Int, val celebrate: Boolean)

/**
 * Counts consecutive "clean" completed rounds. Three in a row => fireworks and the counter restarts.
 * A round that needed lots of help just restarts the count quietly - nothing negative is shown.
 */
class StreakTracker(private val target: Int = 3) {
    var count = 0
        private set

    fun onRoundCompleted(clean: Boolean): StreakResult {
        if (!clean) {
            count = 0
            return StreakResult(0, false)
        }
        count++
        if (count >= target) {
            count = 0
            return StreakResult(target, true)
        }
        return StreakResult(count, false)
    }

    fun reset() {
        count = 0
    }
}

sealed interface StepOutcome {
    data object None : StepOutcome
    data class Advanced(val result: MatchResult) : StepOutcome
    data class Hint(val result: MatchResult, val showGlow: Boolean) : StepOutcome
    data class Completed(val result: MatchResult, val clean: Boolean) : StepOutcome
}

/** State machine for one pass through a notes lesson. */
class NoteRound(
    private val lesson: Lesson,
    private val toleranceSemitones: Int,
    private val anyOctave: Boolean,
    private val allowedMisses: Int = 3,
) {
    var stepIndex = 0
        private set
    var misses = 0
        private set
    private var stepMisses = 0

    val target: LessonStep get() = lesson.steps[stepIndex]

    fun onNote(heardMidi: Double): StepOutcome {
        val r = NoteMatcher.judge(target.midi, heardMidi, toleranceSemitones, anyOctave)
        if (r.counts) {
            stepIndex++
            stepMisses = 0
            return if (stepIndex >= lesson.steps.size) {
                stepIndex = lesson.steps.size - 1
                StepOutcome.Completed(r, clean = misses <= allowedMisses)
            } else StepOutcome.Advanced(r)
        }
        misses++
        stepMisses++
        // glow the right key straight away for a near miss, and after two misses otherwise
        return StepOutcome.Hint(r, showGlow = r.kind == MatchKind.NEAR_MISS || stepMisses >= 2)
    }
}
