package com.pianosteps.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pianosteps.lessons.Lesson
import com.pianosteps.lessons.RhythmRound

/**
 * The staff-free "up/down" line: one dot per step, higher notes drawn higher, coloured arrows
 * between dots (green up / orange down) and the suggested finger number inside each dot.
 */
@Composable
fun DirectionLine(lesson: Lesson, currentStep: Int, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    Canvas(modifier = modifier) {
        val steps = lesson.steps
        val n = steps.size
        val pad = 56f
        val r = (size.height * 0.16f).coerceIn(26f, 54f)
        val lo = steps.minOf { it.midi }
        val hi = steps.maxOf { it.midi }
        fun px(i: Int) = if (n == 1) size.width / 2 else pad + (size.width - 2 * pad) * i / (n - 1)
        fun py(i: Int): Float = if (hi == lo) size.height / 2 else {
            val f = (steps[i].midi - lo).toFloat() / (hi - lo)
            size.height - pad * 0.8f - (size.height - pad * 1.6f) * f
        }
        // guide line
        for (i in 1 until n) {
            val a = Offset(px(i - 1), py(i - 1))
            val b = Offset(px(i), py(i))
            val up = steps[i].midi > steps[i - 1].midi
            val col = if (up) PsColors.UpColor else PsColors.DownColor
            drawLine(col, a, b, strokeWidth = 14f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
            // arrow head in the middle
            val mid = Offset((a.x + b.x) / 2, (a.y + b.y) / 2)
            val angle = Math.toDegrees(Math.atan2((b.y - a.y).toDouble(), (b.x - a.x).toDouble())).toFloat()
            rotate(angle, mid) {
                val p = Path().apply {
                    moveTo(mid.x + 26f, mid.y)
                    lineTo(mid.x - 14f, mid.y - 24f)
                    lineTo(mid.x - 14f, mid.y + 24f)
                    close()
                }
                drawPath(p, col)
            }
        }
        for (i in 0 until n) {
            val c = Offset(px(i), py(i))
            val done = i < currentStep
            val current = i == currentStep
            val fill = when {
                done -> PsColors.Grass
                current -> PsColors.Sky
                else -> Color(0xFFE0D6C8)
            }
            drawCircle(fill, if (current) r * 1.15f else r, c)
            drawCircle(PsColors.Ink.copy(alpha = 0.6f), if (current) r * 1.15f else r, c, style = Stroke(4f))
            val label = if (done) "✓" else (steps[i].finger?.toString() ?: "")
            if (label.isNotEmpty()) {
                val tl = measurer.measure(label, TextStyle(fontSize = (r * 1.1f / density).sp, fontWeight = FontWeight.ExtraBold, color = Color.White))
                drawText(tl, topLeft = Offset(c.x - tl.size.width / 2f, c.y - tl.size.height / 2f))
            }
        }
    }
}

/** Right hand with the suggested finger highlighted (1 = thumb on the left, back-of-hand view). */
@Composable
fun FingerHand(finger: Int?, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val palmTop = h * 0.5f
        drawRoundRect(Color(0xFFFFCCBC), Offset(w * 0.1f, palmTop), Size(w * 0.8f, h * 0.5f), CornerRadius(w * 0.1f))
        val heights = floatArrayOf(0.30f, 0.46f, 0.52f, 0.46f, 0.36f)
        val fw = w * 0.14f
        for (i in 0 until 5) {
            val cx = w * (0.17f + i * 0.165f)
            val fh = h * heights[i]
            val top = palmTop - fh + h * 0.06f
            val active = finger == i + 1
            drawRoundRect(
                if (active) PsColors.Coral else Color(0xFFFFCCBC),
                Offset(cx - fw / 2, top), Size(fw, fh), CornerRadius(fw / 2),
            )
            drawRoundRect(Color(0xFF8D6E63), Offset(cx - fw / 2, top), Size(fw, fh), CornerRadius(fw / 2), style = Stroke(3f))
            val tl = measurer.measure("${i + 1}", TextStyle(fontSize = (fw * 0.6f / density).sp, fontWeight = FontWeight.ExtraBold, color = if (active) Color.White else Color(0xFF8D6E63)))
            drawText(tl, topLeft = Offset(cx - tl.size.width / 2f, top + fw * 0.2f))
        }
    }
}

/**
 * Rhythm visual: a ring shrinks onto the target circle; the beat is when it lands. The circle swells
 * briefly on the beat. [now] is the clock in elapsedRealtime ms.
 */
@Composable
fun PulseCircle(round: RhythmRound?, now: State<Long>, lastHitFlash: State<Long>, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val c = Offset(size.width / 2, size.height / 2)
        val base = minOf(size.width, size.height) * 0.22f
        var swell = 1f
        if (round != null) {
            val phase = round.phaseToNextBeat(now.value).toFloat() // 1 -> 0
            val ringR = base * (1f + 1.4f * phase)
            drawCircle(PsColors.Sky.copy(alpha = 0.25f + 0.5f * (1f - phase)), ringR, c, style = Stroke(base * 0.14f))
            if (phase > 0.8f) swell = 1f + (phase - 0.8f) * 1.0f
            // soft pocket glow shows the forgiving window
            drawCircle(PsColors.Sun.copy(alpha = 0.18f), base * (1f + 0.9f * round.windowFraction.toFloat()), c)
        }
        val sinceHit = now.value - lastHitFlash.value
        val hitFlash = if (sinceHit in 0..300) 1f - sinceHit / 300f else 0f
        drawCircle(PsColors.Coral, base * swell, c)
        if (hitFlash > 0f) drawCircle(PsColors.Grass.copy(alpha = hitFlash), base * (swell + 0.25f * hitFlash), c)
    }
}

@Composable
fun BeatDots(round: RhythmRound?, rhythmBeats: Int, countIn: Int, now: State<Long>, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val total = countIn + rhythmBeats
        val r = size.height * 0.28f
        val gap = size.width / (total + 1)
        for (i in 0 until total) {
            val c = Offset(gap * (i + 1), size.height / 2)
            if (i < countIn) {
                drawCircle(Color(0xFFBDBDBD), r * 0.6f, c)
            } else {
                val k = i - countIn
                val hit = round?.hits?.getOrNull(k) == true
                val passed = round != null && now.value > round.beatTime(k) + round.windowFraction * round.beatMs
                when {
                    hit -> drawCircle(PsColors.Grass, r, c)
                    passed -> drawCircle(Color(0xFFFFE0B2), r, c)
                    else -> drawCircle(PsColors.Sky.copy(alpha = 0.5f), r, c, style = Stroke(6f))
                }
            }
        }
    }
}
