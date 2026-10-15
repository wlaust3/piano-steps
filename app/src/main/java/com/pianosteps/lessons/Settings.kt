package com.pianosteps.lessons

enum class KeyboardMode { AUTO, FULL, RELEVANT, COLLAPSED, HIDDEN }

enum class RewardTheme(val label: String, val emojis: List<String>) {
    BALLOONS("Balloons", emptyList()),
    STARS("Stars", listOf("⭐", "🌟", "✨")),
    ANIMALS("Animals", listOf("🐶", "🐱", "🐰", "🐼", "🦁", "🐸")),
    SPACE("Space", listOf("🚀", "🛸", "🌙", "⭐")),
}

data class Settings(
    val lessonId: String = Lessons.all.first().id,
    /** Notes this many semitones away still count as a hit (0 = exact, 1 = half step off ok, 2 = whole step). */
    val toleranceSemitones: Int = 1,
    val anyOctave: Boolean = true,
    /** Rhythm window, as percent of a beat either side of the pulse. */
    val rhythmWindowPercent: Int = 40,
    val keyboardMode: KeyboardMode = KeyboardMode.AUTO,
    val theme: RewardTheme = RewardTheme.BALLOONS,
    val calibrationSkipped: Boolean = false,
) {
    companion object {
        val EASY = Settings(toleranceSemitones = 1, anyOctave = true, rhythmWindowPercent = 45)
        val NORMAL = Settings(toleranceSemitones = 1, anyOctave = false, rhythmWindowPercent = 35)
        val TRICKY = Settings(toleranceSemitones = 0, anyOctave = false, rhythmWindowPercent = 25)
    }
}
