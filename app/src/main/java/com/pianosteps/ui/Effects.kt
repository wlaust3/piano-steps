package com.pianosteps.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import com.pianosteps.lessons.RewardTheme
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private class Sprite(
    var x: Float, var y: Float, val vy: Float, val sway: Float, val phase: Float,
    val color: Color, val emoji: String?, val size: Float, val popY: Float, var delay: Float,
)

private class Spark(
    var x: Float, var y: Float, var vx: Float, var vy: Float, var life: Float, val maxLife: Float,
    val color: Color, val size: Float, val gravity: Float,
)

private class Burst(var delay: Float, val x: Float, val y: Float, val colors: List<Color>)

/** Particle state for balloon-pops (every success) and fireworks (3-in-a-row streak). */
class EffectsState {
    private val sprites = ArrayList<Sprite>()
    private val sparks = ArrayList<Spark>()
    private val bursts = ArrayList<Burst>()
    private val rnd = Random(System.nanoTime())
    internal var tick by mutableIntStateOf(0)
    var running by mutableStateOf(false)
        private set
    private var elapsed = 0f

    private fun palette(theme: RewardTheme): List<Color> = when (theme) {
        RewardTheme.BALLOONS -> listOf(Color(0xFFEF5350), Color(0xFFFFCA28), Color(0xFF42A5F5), Color(0xFF66BB6A), Color(0xFFAB47BC), Color(0xFFFF7043))
        RewardTheme.STARS -> listOf(Color(0xFFFFD54F), Color(0xFFFFF176), Color(0xFFFFB300), Color.White)
        RewardTheme.ANIMALS -> listOf(Color(0xFFFFAB91), Color(0xFFA5D6A7), Color(0xFF90CAF9), Color(0xFFF48FB1))
        RewardTheme.SPACE -> listOf(Color(0xFF7E57C2), Color(0xFF42A5F5), Color(0xFFE1BEE7), Color.White)
    }

    fun balloons(w: Float, h: Float, theme: RewardTheme) {
        val colors = palette(theme)
        val s = minOf(w, h) * 0.085f
        repeat(7) { i ->
            sprites += Sprite(
                x = w * (0.1f + 0.8f * rnd.nextFloat()), y = h + s * 2,
                vy = -h * (0.55f + 0.35f * rnd.nextFloat()),
                sway = 20f + 30f * rnd.nextFloat(), phase = rnd.nextFloat() * 6.28f,
                color = colors[rnd.nextInt(colors.size)],
                emoji = if (theme.emojis.isEmpty()) null else theme.emojis[rnd.nextInt(theme.emojis.size)],
                size = s * (0.9f + 0.4f * rnd.nextFloat()),
                popY = h * (0.15f + 0.5f * rnd.nextFloat()), delay = i * 0.12f,
            )
        }
        start()
    }

    fun fireworks(w: Float, h: Float, theme: RewardTheme) {
        val colors = palette(theme)
        repeat(8) { i ->
            bursts += Burst(
                delay = i * 0.3f + rnd.nextFloat() * 0.15f,
                x = w * (0.15f + 0.7f * rnd.nextFloat()), y = h * (0.12f + 0.4f * rnd.nextFloat()), colors = colors,
            )
        }
        start()
    }

    private fun start() {
        running = true
    }

    private fun spawnSparks(x: Float, y: Float, colors: List<Color>, count: Int, speed: Float, gravity: Float, size: Float) {
        repeat(count) {
            val a = rnd.nextFloat() * 6.2832f
            val v = speed * (0.3f + 0.7f * rnd.nextFloat())
            sparks += Spark(x, y, cos(a) * v, sin(a) * v, 0f, 0.8f + 0.7f * rnd.nextFloat(), colors[rnd.nextInt(colors.size)], size * (0.6f + 0.8f * rnd.nextFloat()), gravity)
        }
    }

    fun step(dt: Float) {
        elapsed += dt
        val it = sprites.iterator()
        while (it.hasNext()) {
            val s = it.next()
            if (s.delay > 0f) { s.delay -= dt; continue }
            s.y += s.vy * dt
            s.x += sin(elapsed * 3f + s.phase) * s.sway * dt
            if (s.y < s.popY) {
                spawnSparks(s.x, s.y, listOf(s.color, Color.White), 16, s.size * 9f, 300f, s.size * 0.18f)
                it.remove()
            }
        }
        val bi = bursts.iterator()
        while (bi.hasNext()) {
            val b = bi.next()
            b.delay -= dt
            if (b.delay <= 0f) {
                spawnSparks(b.x, b.y, b.colors, 46, 420f, 260f, 9f)
                bi.remove()
            }
        }
        val si = sparks.iterator()
        while (si.hasNext()) {
            val p = si.next()
            p.life += dt
            if (p.life >= p.maxLife) { si.remove(); continue }
            p.vy += p.gravity * dt
            p.x += p.vx * dt
            p.y += p.vy * dt
            p.vx *= 0.985f
        }
        tick++
        if (sprites.isEmpty() && sparks.isEmpty() && bursts.isEmpty()) running = false
    }

    internal fun draw(scope: androidx.compose.ui.graphics.drawscope.DrawScope, measurer: androidx.compose.ui.text.TextMeasurer, fontSizeFor: (Float) -> androidx.compose.ui.unit.TextUnit) {
        tick // observe
        with(scope) {
            for (s in sprites) {
                if (s.delay > 0f) continue
                if (s.emoji != null) {
                    val tl = measurer.measure(s.emoji, TextStyle(fontSize = fontSizeFor(s.size * 2f)))
                    drawText(tl, topLeft = Offset(s.x - tl.size.width / 2f, s.y - tl.size.height / 2f))
                } else {
                    val rx = s.size * 0.8f
                    val ry = s.size
                    // string
                    drawLine(Color(0xFF8D6E63), Offset(s.x, s.y + ry), Offset(s.x + sin(elapsed * 4f + s.phase) * 10f, s.y + ry * 2.2f), 3f)
                    val knot = Path().apply {
                        moveTo(s.x, s.y + ry - 2f); lineTo(s.x - 8f, s.y + ry + 12f); lineTo(s.x + 8f, s.y + ry + 12f); close()
                    }
                    drawPath(knot, s.color)
                    drawOval(s.color, Offset(s.x - rx, s.y - ry), Size(rx * 2, ry * 2))
                    drawOval(Color.White.copy(alpha = 0.45f), Offset(s.x - rx * 0.55f, s.y - ry * 0.7f), Size(rx * 0.4f, ry * 0.5f))
                }
            }
            for (p in sparks) {
                val a = 1f - p.life / p.maxLife
                drawCircle(p.color.copy(alpha = a), p.size, Offset(p.x, p.y))
            }
        }
    }
}

/**
 * Full-size overlay that animates when [balloonTrigger] / [fireworksTrigger] increase.
 * It never intercepts touches.
 */
@Composable
fun EffectsOverlay(balloonTrigger: Int, fireworksTrigger: Int, theme: RewardTheme, modifier: Modifier = Modifier) {
    val state = remember { EffectsState() }
    var size by remember { mutableStateOf(Size(1000f, 1500f)) }
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current

    LaunchedEffect(balloonTrigger) { if (balloonTrigger > 0) state.balloons(size.width, size.height, theme) }
    LaunchedEffect(fireworksTrigger) { if (fireworksTrigger > 0) state.fireworks(size.width, size.height, theme) }
    LaunchedEffect(state.running) {
        if (state.running) {
            var last = withFrameNanosCompat()
            while (state.running) {
                val now = withFrameNanosCompat()
                state.step(((now - last) / 1e9f).coerceIn(0f, 0.05f))
                last = now
            }
        }
    }
    Canvas(modifier.fillMaxSize().onSizeChanged { size = Size(it.width.toFloat(), it.height.toFloat()) }) {
        state.draw(this, measurer) { px -> with(density) { px.toSp() } }
    }
}

private suspend fun withFrameNanosCompat(): Long = androidx.compose.runtime.withFrameNanos { it }
