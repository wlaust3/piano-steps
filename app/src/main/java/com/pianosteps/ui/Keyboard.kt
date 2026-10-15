package com.pianosteps.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import com.pianosteps.audio.Notes

/**
 * On-screen keyboard showing [range]. [targetMidi] is always tinted blue; [hintMidi] pulses with a
 * soft yellow glow (near-miss / gentle help). Tapping a key calls [onKey] (used to hear the note).
 */
@Composable
fun PianoKeyboard(
    range: IntRange,
    targetMidi: Int?,
    hintMidi: Int?,
    onKey: (Int) -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val measurer = rememberTextMeasurer()
    val transition = rememberInfiniteTransition(label = "glow")
    val glow by transition.animateFloat(
        initialValue = 0.35f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "glowAlpha",
    )
    val whites = range.filter { !Notes.isBlack(it) }
    val blacks = range.filter { Notes.isBlack(it) }

    fun layout(width: Float, height: Float): Map<Int, Pair<Offset, Size>> {
        val ww = width / whites.size
        val bw = ww * 0.62f
        val bh = height * 0.62f
        val map = HashMap<Int, Pair<Offset, Size>>()
        whites.forEachIndexed { i, m -> map[m] = Offset(i * ww, 0f) to Size(ww, height) }
        for (m in blacks) {
            val leftWhite = whites.indexOfLast { it < m }
            if (leftWhite < 0 || leftWhite >= whites.size - 0) continue
            val x = (leftWhite + 1) * ww - bw / 2
            map[m] = Offset(x, 0f) to Size(bw, bh)
        }
        return map
    }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(range) {
                detectTapGestures { pos ->
                    val rects = layout(size.width.toFloat(), size.height.toFloat())
                    val hit = (blacks + whites).firstOrNull { m ->
                        val (o, s) = rects[m] ?: return@firstOrNull false
                        pos.x in o.x..(o.x + s.width) && pos.y in o.y..(o.y + s.height)
                    }
                    if (hit != null) onKey(hit)
                }
            },
    ) {
        val rects = layout(size.width, size.height)
        val corner = CornerRadius(size.width / whites.size * 0.12f)
        fun drawKey(m: Int) {
            val (o, s) = rects[m] ?: return
            val black = Notes.isBlack(m)
            val base = if (black) Color(0xFF3B2F2F) else Color.White
            val fill = when (m) {
                targetMidi -> if (black) Color(0xFF0288D1) else Color(0xFFB3E5FC)
                else -> base
            }
            val inset = if (black) 0f else 1.5f
            drawRoundRect(fill, o + Offset(inset, 0f), Size(s.width - 2 * inset, s.height), corner)
            drawRoundRect(Color(0xFF6D5D5D), o + Offset(inset, 0f), Size(s.width - 2 * inset, s.height), corner, style = Stroke(2f))
            if (m == hintMidi) {
                drawRoundRect(Color(0xFFFFD54F).copy(alpha = 0.75f * glow), o + Offset(inset, 0f), Size(s.width - 2 * inset, s.height), corner)
                drawRoundRect(Color(0xFFFFB300).copy(alpha = glow), o + Offset(inset, 0f), Size(s.width - 2 * inset, s.height), corner, style = Stroke(6f))
            }
        }
        whites.forEach { drawKey(it) }
        blacks.forEach { drawKey(it) }
        if (!compact) {
            val ww = size.width / whites.size
            for (m in whites) if (Notes.pitchClass(m) == 0) {
                val label = "C"
                val layoutResult = measurer.measure(
                    label,
                    TextStyle(fontSize = (ww * 0.28f / density).coerceIn(9f, 18f).sp, fontWeight = FontWeight.Bold, color = Color(0xFF6D5D5D)),
                )
                val o = rects.getValue(m).first
                drawText(layoutResult, topLeft = Offset(o.x + (ww - layoutResult.size.width) / 2, size.height - layoutResult.size.height - 6f))
                if (m == Notes.C4) drawCircle(Color(0xFFFF7A59), ww * 0.12f, Offset(o.x + ww / 2, size.height - layoutResult.size.height - 6f - ww * 0.2f))
            }
        }
    }
}
