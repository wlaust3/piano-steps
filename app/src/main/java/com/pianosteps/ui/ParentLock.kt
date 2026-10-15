package com.pianosteps.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pianosteps.lessons.ParentGate
import kotlinx.coroutines.launch

/**
 * Small lock icon in the corner of the child screen. Press and hold ~1.5 s, then answer a
 * multiplication question. Only then does [onUnlocked] fire. Nothing on the child screen reveals settings.
 */
@Composable
fun ParentLockButton(onUnlocked: () -> Unit, modifier: Modifier = Modifier) {
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var question by remember { mutableStateOf<com.pianosteps.lessons.GateQuestion?>(null) }

    Box(
        modifier = modifier
            .size(52.dp)
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    val job = scope.launch {
                        progress.snapTo(0f)
                        progress.animateTo(1f, tween(ParentGate.HOLD_MS.toInt(), easing = LinearEasing))
                        question = ParentGate.newQuestion()
                    }
                    tryAwaitRelease()
                    job.cancel()
                    scope.launch { progress.snapTo(0f) }
                })
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(44.dp)) {
            drawCircle(Color(0x22000000), style = Stroke(4f))
            drawArc(
                Color(0xFF9575CD), -90f, 360f * progress.value, false, style = Stroke(8f),
            )
        }
        Text("🔒", fontSize = 20.sp)
    }

    question?.let { q ->
        GateDialog(q, onDismiss = { question = null }, onPassed = {
            question = null
            onUnlocked()
        }, onWrong = { question = ParentGate.newQuestion() })
    }
}

@Composable
private fun GateDialog(
    q: com.pianosteps.lessons.GateQuestion,
    onDismiss: () -> Unit,
    onPassed: () -> Unit,
    onWrong: () -> Unit,
) {
    var text by remember(q) { mutableStateOf("") }
    var wrong by remember(q) { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Grown-ups only") },
        text = {
            Column {
                Text("To open the parent settings, answer:")
                Spacer(Modifier.height(8.dp))
                Text(q.text, fontSize = 26.sp)
                OutlinedTextField(
                    value = text, onValueChange = { text = it.filter(Char::isDigit).take(3) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true,
                )
                if (wrong) Text("Not quite - try this new one.", color = Color(0xFFD84315))
            }
        },
        confirmButton = {
            TextButton(onClick = { if (q.check(text)) onPassed() else { wrong = true; onWrong() } }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
