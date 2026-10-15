# Piano Steps - design notes

## Layers

```
MicEngine (AudioRecord, 44.1 kHz mono, 1024-sample hop, 2048 window)
   -> FrameAnalyzer (RMS gate + YIN pitch + octave folding + tuning offset)   -> AnalysisFrame
        -> OnsetDetector (RMS envelope)           -> "a sound started"   (rhythm lessons, re-arming)
        -> NoteStabilizer (3 consistent frames)   -> HeardNote           (note lessons)
             -> NoteMatcher / NoteRound           -> hit / near-hit / near-miss / miss
             -> RhythmRound                       -> beat claimed or not
             -> StreakTracker                     -> balloons / fireworks
```

Everything left of the Compose UI except `MicEngine` and `ToneSynth` is pure Kotlin and unit-tested
without a device (`app/src/test`). `LessonSession` is the thin glue that exposes Compose state.

## Pitch detection

`audio/PitchDetector.kt` is a pure-Kotlin **YIN** implementation:

1. Difference function over a 1024-sample window for lags up to `fs / 120 Hz` (~367 samples).
2. Cumulative-mean-normalised difference (CMNDF).
3. First dip below an absolute threshold (0.15), walked down to its local minimum; otherwise the global
   minimum if it is below the instrument's `maxCmndf` (default 0.3), otherwise "no pitch" (noise).
4. **Octave-error correction**: if half the lag is *also* a clear period (CMNDF < 0.22 and no worse than
   the chosen lag + 0.07) the pitch is the octave above (classic YIN sub-harmonic error on piano partials).
   This is applied up to twice.
5. Parabolic interpolation around the lag for sub-sample precision (tests: within ~15 cents for sines from
   C3 to C6).

Candidate range is biased towards the lesson octaves: the search is limited to 120-1250 Hz (about B2-D#6) and
`FrameAnalyzer` folds any result outside B2-C6 by whole octaves (`Notes.foldIntoRange`).

A frame is **voiced** only when its RMS is above the per-note threshold from calibration (or the general
gate `max(2.5 x noise floor, 0.006)` when uncalibrated).

`NoteStabilizer` emits a note once its pitch has been steady for 3 frames (~70 ms). It will not emit again
until the sound stops, the pitch moves by more than 0.8 semitone, or `OnsetDetector` sees a fresh attack.
That prevents one held key being counted as several attempts, and lets the same key be played twice.

### Not hearing itself

`ToneSynth` calls `MuteGate.muteFor(duration + 350 ms)` before playing. While muted, `MicEngine` emits silent
frames instead of analysing audio, so the demo tone is never treated as an attempt.

## Calibration

`CalibrationController` (UI state) + `CalibrationMath` (pure) in `audio/Calibration.kt`, `ui/CalibrationScreen.kt`:

1. **Silence** for 2 s -> `noiseFloorRms` = 90th percentile of the frame RMS values.
2. **Prompted notes** C4, G4, C5 (the lesson octaves). A frame counts as "the note" if its raw pitch is within
   0.7 semitone of the target; 30 such frames (~0.7 s) finish a note (20 s timeout, skippable).
3. For each heard note (skipping the attack): threshold = max(0.35 x 25th-percentile RMS, 2.5 x noise floor);
   tuning error in cents = median(raw pitch - target).
4. `centsOffset` = median across notes (clamped to +-50). It is subtracted from every later pitch, so a piano
   that sits 25 cents sharp still maps to the right key.
5. `maxCmndf` = 90th percentile of the heard frames' CMNDF + 0.1 (clamped 0.2-0.4): how "clean" this
   instrument's tone is.

Stored as one string in SharedPreferences (`Calibration.encode/decode`). "Learning the timbre" here means
the loudness, tuning and periodicity statistics above - it does not build a spectral model of the instrument.

## Near-miss tolerance

`NoteMatcher.judge(target, heardMidi, toleranceSemitones, anyOctave)`:

| distance to target | result |
| --- | --- |
| 0 | `EXACT` - counts |
| 1..tolerance | `NEAR_HIT` - counts **and** the right key glows ("So close!") |
| <= 2 but not accepted | `NEAR_MISS` - doesn't count, right key glows, friendly message |
| otherwise | `MISS` - friendly "higher/lower" hint; right key glows after two tries |

`anyOctave` folds the distance modulo 12 (a 5-year-old rarely finds the right octave). Nothing ever fails a
lesson - the child just keeps trying.

## Rhythm

`RhythmRound` (pure): `countInBeats` pulse-only beats, then `beats` beats. Beat *k* is at
`start + (countIn + k) x 60000/bpm`. An onset within +-`windowFraction` x beat (default 0.40) of an unclaimed beat claims
the nearest one. `requiredHits` of `beats` completes the round. The UI draws a ring shrinking onto the target
circle (`phaseToNextBeat`) - there is deliberately no click track (it would be heard by the microphone).

## Streaks and rewards

`StreakTracker` counts consecutive "clean" rounds (<= 3 misses/near-misses). Every round shows balloons
(pop animation, themed); three clean rounds in a row trigger fireworks (Canvas particles) and the counter
restarts. A round that needed lots of help restarts the count silently - no negative feedback is shown.
Themes: balloons, stars, animals, space (`RewardTheme`).

## Keyboard

`KeyboardPolicy.decide(mode, widthDp, heightDp, lessonRange)` (pure, tested) returns FULL (C4-C6, or C3-C6 on
wide screens), RELEVANT (lesson range +- 3 semitones, at least 8 white keys), COLLAPSED (relevant keys in a
slim strip) or HIDDEN. `AUTO` uses `BoxWithConstraints` size; parents can force a mode; the child has a
show/hide chip.

## Lesson data format

Lessons are plain data in `lessons/Lesson.kt` (`Lessons.all`):

```kotlin
Lesson(
    id = "three-steps-up",             // unique, stored in settings
    title = "Three Steps Up",
    teaches = "For the parent: what this lesson teaches",
    intro = "Climb the stairs: 1, 2, 3!",
    kind = LessonKind.NOTES,            // or LessonKind.RHYTHM
    steps = listOf(                     // NOTES: one entry per note, in order
        LessonStep(midi = 60, finger = 1, prompt = "Finger 1 on the glowing key."),
        LessonStep(midi = 62, finger = 2, prompt = "Finger 2 - up one!"),
        LessonStep(midi = 64, finger = 3, prompt = "Finger 3 - up again!"),
    ),
    celebration = "Hooray! You did it!",
)

Lesson(                                 // RHYTHM
    id = "gentle-beat", title = "Gentle Beat", teaches = "...", intro = "...",
    kind = LessonKind.RHYTHM,
    rhythm = RhythmSpec(bpm = 60, beats = 4, countInBeats = 2, requiredHits = 3),
)
```

Add an entry to `Lessons.all` and it appears in the parent's lesson list. The up/down line, arrows, finger
labels and keyboard highlighting are all derived from `steps` (direction = sign of the pitch change).
`LessonDataTest` checks that every lesson is well formed.

## Parent gate

A lock icon in the corner of the child screen: press and hold for 1.5 s, then answer a multiplication question
(6-9 x 4-9). It is a speed bump against a curious 5-year-old, not security.
