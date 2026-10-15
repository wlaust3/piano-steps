package com.pianosteps.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.pianosteps.audio.AnalysisFrame
import com.pianosteps.audio.HeardNote
import com.pianosteps.audio.NoteStabilizer
import com.pianosteps.audio.OnsetDetector
import com.pianosteps.lessons.Lesson
import com.pianosteps.lessons.LessonKind
import com.pianosteps.lessons.MatchKind
import com.pianosteps.lessons.NoteRound
import com.pianosteps.lessons.RhythmRound
import com.pianosteps.lessons.Settings
import com.pianosteps.lessons.StepOutcome
import com.pianosteps.lessons.StreakTracker

data class UiMessage(val id: Long, val text: String)

/**
 * Compose-facing state for the lesson the child is doing. The decision logic lives in the pure,
 * unit-tested classes (NoteRound, RhythmRound, StreakTracker, NoteMatcher); this class only wires
 * microphone frames into them and exposes observable state for the UI.
 */
class LessonSession(val lesson: Lesson, private val settings: Settings) {
    private val stabilizer = NoteStabilizer()
    private val onsets = OnsetDetector()
    private val streakTracker = StreakTracker(3)
    private var noteRound = newNoteRound()
    private var counter = 0L

    var stepIndex by mutableIntStateOf(0); private set
    var celebrating by mutableStateOf(false); private set
    var message by mutableStateOf<UiMessage?>(null); private set
    var hintMidi by mutableStateOf<Int?>(null); private set
    var balloonTrigger by mutableIntStateOf(0); private set
    var fireworksTrigger by mutableIntStateOf(0); private set
    var streakShown by mutableIntStateOf(0); private set
    var level by mutableFloatStateOf(0f); private set

    // rhythm
    var rhythmRound by mutableStateOf<RhythmRound?>(null); private set
    val now = mutableLongStateOf(0L)
    val lastHit = mutableLongStateOf(-10_000L)
    val rhythmRunning: Boolean get() = rhythmRound != null && !celebrating && !rhythmFinished
    var rhythmFinished by mutableStateOf(false); private set

    val currentTarget: Int? get() = if (lesson.kind == LessonKind.NOTES) lesson.steps[stepIndex].midi else null

    private fun newNoteRound() =
        if (lesson.kind == LessonKind.NOTES) NoteRound(lesson, settings.toleranceSemitones, settings.anyOctave) else null

    private fun say(text: String) {
        message = UiMessage(++counter, text)
    }

    fun clearMessage() {
        message = null
        hintMidi = null
    }

    fun onFrame(frame: AnalysisFrame, gateRms: Double) {
        level = (frame.rms / (gateRms * 8)).toFloat().coerceIn(0f, 1f)
        val onset = onsets.feed(frame.timeMs, frame.rms, gateRms)
        if (celebrating) return
        when (lesson.kind) {
            LessonKind.NOTES -> stabilizer.feed(frame, onset)?.let { handleNote(it) }
            LessonKind.RHYTHM -> {
                val r = rhythmRound ?: return
                if (onset && !rhythmFinished && r.onOnset(frame.timeMs) != null) lastHit.longValue = frame.timeMs
            }
        }
    }

    private fun handleNote(heard: HeardNote) {
        val round = noteRound ?: return
        val justTarget = round.target.midi
        when (val o = round.onNote(heard.midi)) {
            StepOutcome.None -> {}
            is StepOutcome.Advanced -> {
                stepIndex = round.stepIndex
                if (o.result.kind == MatchKind.NEAR_HIT) {
                    hintMidi = justTarget
                    say("So close! This is the key that was glowing 💛")
                } else say("Yes! 🎉")
            }
            is StepOutcome.Hint -> {
                hintMidi = if (o.showGlow) justTarget else null
                say(
                    when {
                        o.result.kind == MatchKind.NEAR_MISS -> "So close! Try the glowing key 💛"
                        o.result.direction > 0 -> "Let's try a higher key ⬆️"
                        o.result.direction < 0 -> "Let's try a lower key ⬇️"
                        else -> "Let's look for the glowing key 💛"
                    },
                )
            }
            is StepOutcome.Completed -> {
                stepIndex = round.stepIndex
                if (o.result.kind == MatchKind.NEAR_HIT) hintMidi = justTarget
                celebrate(o.clean)
            }
        }
    }

    fun startRhythm(nowMs: Long) {
        val spec = lesson.rhythm ?: return
        now.longValue = nowMs
        rhythmFinished = false
        onsets.reset()
        rhythmRound = RhythmRound(spec, nowMs + 400, settings.rhythmWindowPercent / 100.0)
        say("Get ready…")
    }

    /** Called every frame by the UI clock while a rhythm round is running. */
    fun tick(nowMs: Long) {
        now.longValue = nowMs
        val r = rhythmRound ?: return
        if (!rhythmFinished && r.isFinished(nowMs)) {
            rhythmFinished = true
            if (r.success()) celebrate(true)
            else {
                say("Good try! Let's do it again 🎵")
                streakTracker.onRoundCompleted(false)
                streakShown = 0
            }
        }
    }

    private fun celebrate(clean: Boolean) {
        val r = streakTracker.onRoundCompleted(clean)
        balloonTrigger++
        if (r.celebrate) fireworksTrigger++
        streakShown = r.count
        celebrating = true
        say(if (r.celebrate) "Three in a row! Amazing! 🎆" else lesson.celebration)
    }

    /** Ends the celebration and gets ready for another go. */
    fun nextRound() {
        noteRound = newNoteRound()
        stepIndex = 0
        celebrating = false
        rhythmRound = null
        rhythmFinished = false
        stabilizer.reset()
        onsets.reset()
        hintMidi = null
        message = null
        if (streakShown >= 3) streakShown = 0
    }
}
