package com.pianosteps.ui

import android.os.SystemClock
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.pianosteps.audio.MicUnavailableException
import com.pianosteps.lessons.KeyboardDisplay
import com.pianosteps.lessons.KeyboardPolicy
import com.pianosteps.lessons.Lesson
import com.pianosteps.lessons.LessonKind
import com.pianosteps.lessons.Lessons
import kotlinx.coroutines.delay

/** The child-facing screen. No settings here: only the lesson, a keyboard show/hide chip and a hidden parent lock. */
@Composable
fun LessonScreen(vm: AppViewModel, onParent: () -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val calibration by vm.calibration.collectAsStateWithLifecycle()
    val lesson = Lessons.byId(settings.lessonId)
    val session = remember(lesson.id, settings.toleranceSemitones, settings.anyOctave, settings.rhythmWindowPercent) {
        LessonSession(lesson, settings)
    }
    var micError by remember { mutableStateOf<String?>(null) }
    var micRetry by remember { mutableStateOf(0) }
    var keysOverride by rememberSaveable { mutableStateOf<Boolean?>(null) } // child's own show/hide chip
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    // Listen to the mic only while the screen is resumed.
    LaunchedEffect(session, micRetry) {
        micError = null
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            try {
                vm.mic.frames { vm.calibration.value }.collect { session.onFrame(it, vm.calibration.value.gateRms) }
            } catch (e: MicUnavailableException) {
                micError = e.message
            } catch (e: SecurityException) {
                micError = "Microphone permission is off."
            }
        }
    }
    // Message fades after a moment.
    LaunchedEffect(session.message?.id) {
        if (session.message != null && !session.celebrating && !(session.rhythmRunning)) {
            delay(2600); session.clearMessage()
        }
    }
    LaunchedEffect(session.celebrating) {
        if (session.celebrating) { delay(3000); session.nextRound() }
    }
    // Rhythm clock
    LaunchedEffect(session.rhythmRunning) {
        while (session.rhythmRunning) {
            androidx.compose.runtime.withFrameNanos { }
            session.tick(SystemClock.elapsedRealtime())
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(PsColors.Background).systemBarsPadding()) {
        val wDp = maxWidth.value
        val hDp = maxHeight.value
        val wide = maxWidth > maxHeight
        val autoSpec = KeyboardPolicy.decide(settings.keyboardMode, wDp, hDp, lesson.noteRange)
        val display = when (keysOverride) {
            false -> KeyboardDisplay.HIDDEN
            true -> if (autoSpec.display == KeyboardDisplay.HIDDEN) KeyboardDisplay.COLLAPSED else autoSpec.display
            null -> autoSpec.display
        }
        val kbHeight: Dp = when (display) {
            KeyboardDisplay.HIDDEN -> 0.dp
            KeyboardDisplay.COLLAPSED -> 64.dp
            else -> (maxHeight * 0.28f).coerceIn(110.dp, 230.dp)
        }

        Column(Modifier.fillMaxSize()) {
            // top bar
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(lesson.title, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
                Text(if (session.level > 0.15f) "👂" else "😴", fontSize = 24.sp, modifier = Modifier.padding(end = 8.dp))
                Text(
                    if (display == KeyboardDisplay.HIDDEN) "🎹 Show keys" else "🎹 Hide keys",
                    modifier = Modifier.clip(RoundedCornerShape(50)).clickable {
                        keysOverride = display == KeyboardDisplay.HIDDEN
                    }.background(Color(0x14000000)).padding(horizontal = 14.dp, vertical = 10.dp),
                    fontSize = 16.sp,
                )
                ParentLockButton(onUnlocked = onParent)
            }

            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (micError != null) MicErrorCard(micError!!, onRetry = { micRetry++ })
                else if (lesson.kind == LessonKind.NOTES) NotesBody(lesson, session, vm, wide, hDp)
                else RhythmBody(lesson, session, vm, wide, hDp)
            }

            if (display != KeyboardDisplay.HIDDEN) {
                PianoKeyboard(
                    range = autoSpec.range,
                    targetMidi = session.currentTarget,
                    hintMidi = session.hintMidi,
                    onKey = { vm.synth.play(it, 700) },
                    modifier = Modifier.fillMaxWidth().height(kbHeight).padding(horizontal = 8.dp, vertical = 4.dp),
                    compact = display == KeyboardDisplay.COLLAPSED,
                )
            }
        }
        EffectsOverlay(session.balloonTrigger, session.fireworksTrigger, settings.theme)
    }
}

@Composable
private fun MicErrorCard(msg: String, onRetry: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("🎤 I can't hear yet", style = MaterialTheme.typography.headlineMedium)
        Text(msg, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge)
        Text("Ask a grown-up to help!", style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(12.dp))
        BigButton("Try again", onRetry)
    }
}

@Composable
fun BigButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, color: Color = PsColors.Coral) {
    Button(
        onClick = onClick, modifier = modifier.height(64.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color),
        shape = RoundedCornerShape(32.dp),
    ) { Text(text, fontSize = 22.sp, fontWeight = FontWeight.Bold) }
}

@Composable
private fun StreakStars(count: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(3) { Text(if (it < count) "⭐" else "☆", fontSize = 28.sp) }
    }
}

@Composable
private fun FeedbackText(session: LessonSession) {
    val m = session.message
    Box(Modifier.fillMaxWidth().height(44.dp), contentAlignment = Alignment.Center) {
        AnimatedVisibility(visible = m != null) {
            Text(m?.text ?: "", fontSize = 22.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, color = PsColors.Ink)
        }
    }
}

@Composable
private fun NotesBody(lesson: Lesson, session: LessonSession, vm: AppViewModel, wide: Boolean, hDp: Float) {
    val step = lesson.steps[session.stepIndex]
    val prompt = @Composable {
        Text(
            if (session.celebrating) lesson.celebration else step.prompt,
            style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(8.dp),
        )
    }
    val hearIt = @Composable {
        BigButton("🔊 Hear it", { vm.synth.play(step.midi, 1000) }, color = PsColors.Sky)
    }
    val line = @Composable {
        DirectionLine(
            lesson, session.stepIndex,
            Modifier.fillMaxWidth().height((hDp * 0.28f).coerceIn(90f, 180f).dp).padding(horizontal = 8.dp),
        )
    }
    if (wide) {
        Row(Modifier.fillMaxSize().padding(8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
                prompt()
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (step.finger != null) FingerHand(step.finger, Modifier.size(width = 110.dp, height = 100.dp))
                    hearIt()
                }
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
                line()
                FeedbackText(session)
                StreakStars(session.streakShown)
            }
        }
    } else {
        Column(Modifier.fillMaxSize().padding(8.dp).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
            prompt()
            line()
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                if (step.finger != null) FingerHand(step.finger, Modifier.size(width = 130.dp, height = 120.dp))
                hearIt()
            }
            FeedbackText(session)
            StreakStars(session.streakShown)
        }
    }
}

@Composable
private fun RhythmBody(lesson: Lesson, session: LessonSession, vm: AppViewModel, wide: Boolean, hDp: Float) {
    val spec = lesson.rhythm!!
    val running = session.rhythmRunning
    val circle = @Composable {
        PulseCircle(
            session.rhythmRound, session.now, session.lastHit,
            Modifier.fillMaxWidth().height((hDp * 0.42f).coerceIn(120f, 320f).dp),
        )
    }
    val controls = @Composable {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                if (session.celebrating) lesson.celebration else lesson.intro,
                style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center, modifier = Modifier.padding(8.dp),
            )
            BeatDots(session.rhythmRound, spec.beats, spec.countInBeats, session.now, Modifier.fillMaxWidth().height(40.dp).padding(horizontal = 24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 8.dp)) {
                if (!running && !session.celebrating) {
                    BigButton("▶ Start", { session.startRhythm(SystemClock.elapsedRealtime()) })
                    BigButton("🔊 Hear it", {
                        vm.synth.play(List(spec.beats) { 60 }, noteMs = 300, gapMs = (60000 / spec.bpm))
                    }, color = PsColors.Sky)
                }
            }
            FeedbackText(session)
            StreakStars(session.streakShown)
        }
    }
    if (wide) {
        Row(Modifier.fillMaxSize().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { circle() }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) { controls() }
        }
    } else {
        Column(Modifier.fillMaxSize().padding(8.dp).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
            circle()
            controls()
        }
    }
}
