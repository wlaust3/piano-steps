package com.pianosteps.lessons

import com.pianosteps.audio.Notes

enum class LessonKind { NOTES, RHYTHM }

/** One note the child should play. [finger] is the suggested right-hand finger (1 = thumb ... 5 = pinky). */
data class LessonStep(
    val midi: Int,
    val finger: Int? = null,
    /** Child-friendly prompt shown while this step is the target. */
    val prompt: String,
)

/** Rhythm lessons: play anything (any key, or even a clap) on the pulses. */
data class RhythmSpec(
    val bpm: Int,
    val beats: Int,
    val countInBeats: Int = 2,
    /** How many of [beats] must be landed to complete the round. */
    val requiredHits: Int,
)

/**
 * A lesson is plain data - add a new one by adding an entry to [Lessons.all].
 */
data class Lesson(
    val id: String,
    val title: String,
    /** One line for the grown-up: what this lesson teaches. */
    val teaches: String,
    /** Child-friendly intro read at the start. */
    val intro: String,
    val kind: LessonKind = LessonKind.NOTES,
    val steps: List<LessonStep> = emptyList(),
    val rhythm: RhythmSpec? = null,
    val celebration: String = "Hooray! You did it!",
) {
    init {
        require((kind == LessonKind.NOTES && steps.isNotEmpty()) || (kind == LessonKind.RHYTHM && rhythm != null)) {
            "lesson $id is not well formed"
        }
    }

    /** Range of notes the lesson uses (rhythm lessons: middle C octave). */
    val noteRange: IntRange
        get() = if (steps.isEmpty()) Notes.C4..Notes.C5 else steps.minOf { it.midi }..steps.maxOf { it.midi }

    /** +1 up, -1 down, 0 same/first for step [i], relative to the previous step. */
    fun directionAt(i: Int): Int =
        if (i <= 0 || i >= steps.size) 0 else Integer.signum(steps[i].midi - steps[i - 1].midi)
}

object Lessons {
    val all: List<Lesson> = listOf(
        Lesson(
            id = "find-the-key",
            title = "Find the Key",
            teaches = "Finding and playing one key (middle C) with the right thumb; the first note-to-sound connection.",
            intro = "Let's find the glowing key and play it!",
            steps = listOf(
                LessonStep(Notes.C4, 1, "Find the glowing key. Use finger 1, your thumb!"),
            ),
        ),
        Lesson(
            id = "up-and-down",
            title = "Up and Down",
            teaches = "The idea of up (higher) and down (lower) on the keyboard: two notes, C then D, one step to the right.",
            intro = "Up we go! Play the first key, then the next key to the right.",
            steps = listOf(
                LessonStep(Notes.C4, 1, "Start on the glowing key with finger 1."),
                LessonStep(Notes.D4, 2, "Now go UP one key with finger 2!"),
            ),
        ),
        Lesson(
            id = "three-steps-up",
            title = "Three Steps Up",
            teaches = "A three-note pattern C-D-E going up, using fingers 1-2-3 in order; keys to the right sound higher.",
            intro = "Climb the stairs: 1, 2, 3!",
            steps = listOf(
                LessonStep(Notes.C4, 1, "Finger 1 on the glowing key."),
                LessonStep(Notes.D4, 2, "Finger 2 - up one!"),
                LessonStep(Notes.E4, 3, "Finger 3 - up again!"),
            ),
        ),
        Lesson(
            id = "down-we-go",
            title = "Down We Go",
            teaches = "Playing downward: E-D-C with fingers 3-2-1; keys to the left sound lower.",
            intro = "Slide down the slide: 3, 2, 1!",
            steps = listOf(
                LessonStep(Notes.E4, 3, "Start with finger 3 on the glowing key."),
                LessonStep(Notes.D4, 2, "Finger 2 - down one!"),
                LessonStep(Notes.C4, 1, "Finger 1 - down again!"),
            ),
        ),
        Lesson(
            id = "gentle-beat",
            title = "Gentle Beat",
            teaches = "Feeling a slow steady beat: play any key (or clap) roughly when the circle pulses; being near the beat counts.",
            intro = "Watch the circle. Play any key when the ring reaches it!",
            kind = LessonKind.RHYTHM,
            rhythm = RhythmSpec(bpm = 60, beats = 4, countInBeats = 2, requiredHits = 3),
            celebration = "You kept the beat!",
        ),
    )

    fun byId(id: String?): Lesson = all.firstOrNull { it.id == id } ?: all.first()
}
