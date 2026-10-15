package com.pianosteps.lessons

import com.pianosteps.audio.Notes
import kotlin.math.max
import kotlin.math.min

enum class KeyboardDisplay { FULL, RELEVANT, COLLAPSED, HIDDEN }

data class KeyboardLayoutSpec(val display: KeyboardDisplay, val range: IntRange)

/** Decides how much on-screen keyboard to show, from the screen size and the lesson's notes. */
object KeyboardPolicy {
    /** Lesson range +/- a few semitones, widened to start/end on white keys, at least 8 white keys. */
    fun relevantRange(lessonRange: IntRange, margin: Int = 3): IntRange {
        var lo = lessonRange.first - margin
        var hi = lessonRange.last + margin
        while (Notes.isBlack(lo)) lo--
        while (Notes.isBlack(hi)) hi++
        while (whiteCount(lo..hi) < 8) {
            hi++
            while (Notes.isBlack(hi)) hi++
        }
        return lo..hi
    }

    fun whiteCount(r: IntRange): Int = r.count { !Notes.isBlack(it) }

    fun fullRange(widthDp: Float): IntRange = if (widthDp >= 840f) Notes.C3..Notes.C6 else Notes.C4..Notes.C6

    fun decide(mode: KeyboardMode, widthDp: Float, heightDp: Float, lessonRange: IntRange): KeyboardLayoutSpec {
        val relevant = relevantRange(lessonRange)
        val display = when (mode) {
            KeyboardMode.FULL -> KeyboardDisplay.FULL
            KeyboardMode.RELEVANT -> KeyboardDisplay.RELEVANT
            KeyboardMode.COLLAPSED -> KeyboardDisplay.COLLAPSED
            KeyboardMode.HIDDEN -> KeyboardDisplay.HIDDEN
            KeyboardMode.AUTO -> when {
                min(widthDp, heightDp) < 320f -> KeyboardDisplay.HIDDEN
                heightDp < 400f -> KeyboardDisplay.COLLAPSED
                widthDp >= 840f && heightDp >= 600f -> KeyboardDisplay.FULL
                else -> KeyboardDisplay.RELEVANT
            }
        }
        val range = if (display == KeyboardDisplay.FULL) {
            val full = fullRange(widthDp)
            min(full.first, relevant.first)..max(full.last, relevant.last)
        } else relevant
        return KeyboardLayoutSpec(display, range)
    }
}
