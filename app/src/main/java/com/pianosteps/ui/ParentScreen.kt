package com.pianosteps.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pianosteps.lessons.KeyboardMode
import com.pianosteps.lessons.Lessons
import com.pianosteps.lessons.RewardTheme
import com.pianosteps.lessons.Settings
import kotlin.math.roundToInt

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

/** Grown-up settings: lesson, difficulty, keyboard mode, reward theme, calibration. Only reachable through the parent gate. */
@Composable
fun ParentScreen(vm: AppViewModel, onDone: () -> Unit, onCalibrate: () -> Unit) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val cal by vm.calibration.collectAsStateWithLifecycle()

    Column(
        Modifier.fillMaxSize().background(PsColors.Background).systemBarsPadding().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Grown-up settings", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
            BigButton("Done", onDone)
        }

        Section("Lesson") {
            Lessons.all.forEach { l ->
                Row(
                    Modifier.fillMaxWidth().clickable { vm.updateSettings { it.copy(lessonId = l.id) } }.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    RadioButton(selected = s.lessonId == l.id, onClick = { vm.updateSettings { it.copy(lessonId = l.id) } })
                    Column {
                        Text(l.title, fontWeight = FontWeight.Bold)
                        Text(l.teaches, fontSize = 14.sp)
                    }
                }
            }
        }

        Section("Difficulty") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Easy" to Settings.EASY, "Normal" to Settings.NORMAL, "Tricky" to Settings.TRICKY).forEach { (name, p) ->
                    FilterChip(
                        selected = s.toleranceSemitones == p.toleranceSemitones && s.anyOctave == p.anyOctave && s.rhythmWindowPercent == p.rhythmWindowPercent,
                        onClick = { vm.updateSettings { it.copy(toleranceSemitones = p.toleranceSemitones, anyOctave = p.anyOctave, rhythmWindowPercent = p.rhythmWindowPercent) } },
                        label = { Text(name) },
                    )
                }
            }
            Text("Near-miss tolerance: notes up to ${s.toleranceSemitones} half-step(s) off count as a hit (and gently light the right key).")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0, 1, 2).forEach { t ->
                    FilterChip(selected = s.toleranceSemitones == t, onClick = { vm.updateSettings { it.copy(toleranceSemitones = t) } }, label = { Text("$t") })
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Any octave counts", modifier = Modifier.weight(1f))
                Switch(checked = s.anyOctave, onCheckedChange = { c -> vm.updateSettings { it.copy(anyOctave = c) } })
            }
            Text("Rhythm window: ±${s.rhythmWindowPercent}% of a beat counts as on the beat")
            Slider(
                value = s.rhythmWindowPercent.toFloat(), onValueChange = { v -> vm.updateSettings { it.copy(rhythmWindowPercent = v.roundToInt()) } },
                valueRange = 15f..50f,
            )
        }

        Section("On-screen keyboard") {
            Row(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                KeyboardMode.entries.forEach { m ->
                    FilterChip(selected = s.keyboardMode == m, onClick = { vm.updateSettings { it.copy(keyboardMode = m) } }, label = { Text(m.name.lowercase().replaceFirstChar(Char::uppercase)) })
                }
            }
            Text("Auto picks the best fit for the screen size; Relevant shows only the keys the lesson uses; Collapsed is a slim strip.", fontSize = 14.sp)
        }

        Section("Reward theme") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RewardTheme.entries.forEach { t ->
                    FilterChip(selected = s.theme == t, onClick = { vm.updateSettings { it.copy(theme = t) } }, label = { Text((t.emojis.firstOrNull() ?: "🎈") + " " + t.label) })
                }
            }
        }

        Section("Piano calibration") {
            Text(
                if (cal.calibrated) "Calibrated. Room noise ${"%.4f".format(cal.noiseFloorRms)}, tuning ${cal.centsOffset.roundToInt()} cents, ${cal.noteThresholds.size} reference notes."
                else "Not calibrated yet - the app is using generic settings.",
            )
            OutlinedButton(onClick = onCalibrate) { Text(if (cal.calibrated) "Re-run calibration" else "Calibrate now") }
        }
    }
}
