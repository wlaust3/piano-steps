package com.pianosteps.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.pianosteps.audio.AnalysisFrame
import com.pianosteps.audio.Calibration
import com.pianosteps.audio.CalibrationMath
import com.pianosteps.audio.MicUnavailableException
import com.pianosteps.audio.NoteCapture
import com.pianosteps.audio.Notes
import com.pianosteps.lessons.KeyboardPolicy
import kotlin.math.roundToInt

/**
 * Parent-run calibration: 2 s of silence (noise floor), then three prompted notes. Each note gives an
 * amplitude threshold and a tuning offset. Compose-state wrapper over the pure [CalibrationMath].
 */
class CalibrationController(val promptNotes: List<Int> = listOf(Notes.C4, Notes.G4, Notes.C5)) {
    enum class Stage { INTRO, SILENCE, NOTE, DONE, FAILED }

    var stage by mutableStateOf(Stage.INTRO); private set
    var noteIndex by mutableIntStateOf(0); private set
    var progress by mutableFloatStateOf(0f); private set
    var level by mutableFloatStateOf(0f); private set
    var heardNotes by mutableIntStateOf(0); private set
    var result by mutableStateOf<Calibration?>(null); private set
    var status by mutableStateOf(""); private set

    private val silence = ArrayList<Double>()
    private val captures = ArrayList<NoteCapture>()
    private val frames = ArrayList<AnalysisFrame>()
    private var stageStart = -1L
    private var pauseUntil = 0L

    fun begin() {
        silence.clear(); captures.clear(); frames.clear()
        noteIndex = 0; progress = 0f; stageStart = -1L; pauseUntil = 0L; heardNotes = 0
        stage = Stage.SILENCE
        status = "Shhh… keep the room quiet for a moment."
    }

    fun skipNote(nowMs: Long) = advance(nowMs)

    fun onFrame(f: AnalysisFrame) {
        level = (f.rms / 0.1).toFloat().coerceIn(0f, 1f)
        when (stage) {
            Stage.SILENCE -> {
                if (stageStart < 0) stageStart = f.timeMs
                silence += f.rms
                progress = ((f.timeMs - stageStart) / SILENCE_MS.toFloat()).coerceIn(0f, 1f)
                if (f.timeMs - stageStart >= SILENCE_MS) startNotes(f.timeMs)
            }
            Stage.NOTE -> {
                if (f.timeMs < pauseUntil) return
                if (stageStart < 0) stageStart = f.timeMs
                frames += f
                val target = promptNotes[noteIndex]
                val good = frames.count { CalibrationMath.isGoodFrame(it, target) }
                progress = (good / GOOD_FRAMES_NEEDED.toFloat()).coerceIn(0f, 1f)
                if (good >= GOOD_FRAMES_NEEDED) {
                    captures += NoteCapture(target, frames.toList())
                    heardNotes++
                    advance(f.timeMs, pause = true)
                } else if (f.timeMs - stageStart > NOTE_TIMEOUT_MS) {
                    status = "I couldn't hear ${Notes.name(target)} - moving on."
                    advance(f.timeMs, pause = true)
                }
            }
            else -> {}
        }
    }

    private fun startNotes(nowMs: Long) {
        stage = Stage.NOTE
        noteIndex = 0
        frames.clear(); progress = 0f; stageStart = -1L
        pauseUntil = nowMs + 300
        status = ""
    }

    private fun advance(nowMs: Long, pause: Boolean = false) {
        frames.clear(); progress = 0f; stageStart = -1L
        noteIndex++
        pauseUntil = if (pause) nowMs + 1500 else 0L
        if (noteIndex >= promptNotes.size) finish()
    }

    private fun finish() {
        val c = CalibrationMath.compute(silence, captures)
        result = c
        stage = if (c == null) Stage.FAILED else Stage.DONE
    }

    companion object {
        const val SILENCE_MS = 2000L
        const val GOOD_FRAMES_NEEDED = 30
        const val NOTE_TIMEOUT_MS = 20_000L
    }
}

@Composable
fun CalibrationScreen(vm: AppViewModel, onFinished: () -> Unit, onSkip: () -> Unit) {
    val controller = remember { CalibrationController() }
    var micError by remember { mutableStateOf<String?>(null) }
    var retry by remember { mutableIntStateOf(0) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val existing by vm.calibration.collectAsStateWithLifecycle()

    // Calibrate from raw measurements: use a neutral Calibration so old values don't bias the result.
    LaunchedEffect(retry) {
        micError = null
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            try {
                vm.mic.frames { Calibration() }.collect { controller.onFrame(it) }
            } catch (e: MicUnavailableException) {
                micError = e.message
            } catch (e: SecurityException) {
                micError = "Microphone permission is off."
            }
        }
    }

    Column(
        Modifier.fillMaxSize().background(PsColors.Background).systemBarsPadding().padding(20.dp).verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Grown-up setup: teach the app your piano", style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
        if (micError != null) {
            Text("Microphone problem: $micError", color = androidx.compose.ui.graphics.Color(0xFFD84315))
            BigButton("Try again", { retry++ })
            OutlinedButton(onClick = onSkip) { Text("Back") }
            return@Column
        }
        when (controller.stage) {
            CalibrationController.Stage.INTRO -> {
                Text(
                    "Place the phone or tablet near the piano or keyboard, like it will be during lessons. " +
                        "First the app listens to the quiet room (2 seconds). Then you play three notes, " +
                        "each held for about a second, so it can learn how loud and how in-tune this piano sounds.",
                    style = MaterialTheme.typography.bodyLarge,
                )
                if (existing.calibrated) Text("This piano is already calibrated. Running it again replaces the old numbers.")
                BigButton("Start", { controller.begin() })
                OutlinedButton(onClick = onSkip) { Text(if (existing.calibrated) "Cancel" else "Skip for now") }
            }
            CalibrationController.Stage.SILENCE -> {
                Text("🤫 Quiet please!", style = MaterialTheme.typography.titleLarge)
                Text(controller.status)
                LinearProgressIndicator(progress = { controller.progress }, modifier = Modifier.fillMaxWidth().height(12.dp))
            }
            CalibrationController.Stage.NOTE -> {
                val target = controller.promptNotes[controller.noteIndex]
                Text(
                    "Note ${controller.noteIndex + 1} of ${controller.promptNotes.size}: play ${Notes.name(target)}" +
                        if (target == Notes.C4) " (middle C) and hold it" else " and hold it",
                    style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center,
                )
                Box0(Modifier.fillMaxWidth().height(110.dp)) {
                    PianoKeyboard(KeyboardPolicy.relevantRange(Notes.C4..Notes.C5), target, null, { vm.synth.play(it, 700) }, Modifier.fillMaxSize())
                }
                Text("Sound level")
                LinearProgressIndicator(progress = { controller.level }, modifier = Modifier.fillMaxWidth().height(10.dp))
                Text("Heard so far")
                LinearProgressIndicator(progress = { controller.progress }, modifier = Modifier.fillMaxWidth().height(12.dp), color = PsColors.Grass)
                if (controller.status.isNotEmpty()) Text(controller.status)
                OutlinedButton(onClick = { controller.skipNote(android.os.SystemClock.elapsedRealtime()) }) { Text("Skip this note") }
            }
            CalibrationController.Stage.DONE -> {
                val c = controller.result!!
                Text("✅ Got it!", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Heard ${controller.heardNotes} of ${controller.promptNotes.size} notes.\n" +
                        "Room noise level: ${"%.4f".format(c.noiseFloorRms)}\n" +
                        "This piano is ${c.centsOffset.roundToInt()} cents ${if (c.centsOffset >= 0) "sharp" else "flat"} of standard pitch.\n" +
                        "Smallest accepted note volume: ${"%.4f".format(c.noteThresholds.values.min())}",
                    style = MaterialTheme.typography.bodyLarge,
                )
                BigButton("Save", { vm.setCalibration(c); onFinished() })
                OutlinedButton(onClick = { controller.begin() }) { Text("Do it again") }
            }
            CalibrationController.Stage.FAILED -> {
                Text("😕 I couldn't hear the piano", style = MaterialTheme.typography.titleLarge)
                Text("Move the device closer, play a bit louder, and make sure the room is quiet. Then try again.")
                BigButton("Try again", { controller.begin() })
                OutlinedButton(onClick = onSkip) { Text("Skip for now") }
            }
        }
    }
}

@Composable
private fun Box0(modifier: Modifier, content: @Composable () -> Unit) {
    androidx.compose.foundation.layout.Box(modifier) { content() }
}
