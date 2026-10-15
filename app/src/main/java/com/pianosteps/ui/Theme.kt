package com.pianosteps.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object PsColors {
    val Background = Color(0xFFFFF8E7)
    val Coral = Color(0xFFFF7A59)
    val Sky = Color(0xFF4FC3F7)
    val Grass = Color(0xFF66BB6A)
    val Sun = Color(0xFFFFD54F)
    val Grape = Color(0xFF9575CD)
    val Ink = Color(0xFF3B2F2F)
    val UpColor = Color(0xFF43A047)
    val DownColor = Color(0xFFFB8C00)
}

private val scheme = lightColorScheme(
    primary = PsColors.Coral,
    onPrimary = Color.White,
    secondary = PsColors.Sky,
    tertiary = PsColors.Grape,
    background = PsColors.Background,
    surface = Color.White,
    onBackground = PsColors.Ink,
    onSurface = PsColors.Ink,
)

private val typography = Typography(
    headlineMedium = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.ExtraBold),
    titleLarge = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold),
    bodyLarge = TextStyle(fontSize = 20.sp),
)

@Composable
fun PianoStepsTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = typography, content = content)
}
