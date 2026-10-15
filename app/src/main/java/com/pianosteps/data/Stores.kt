package com.pianosteps.data

import android.content.Context
import com.pianosteps.audio.Calibration
import com.pianosteps.lessons.KeyboardMode
import com.pianosteps.lessons.RewardTheme
import com.pianosteps.lessons.Settings

/** SharedPreferences-backed storage for parent settings and the keyboard calibration. */
class Stores(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("piano_steps", Context.MODE_PRIVATE)

    fun loadSettings(): Settings {
        val d = Settings()
        return Settings(
            lessonId = prefs.getString("lessonId", d.lessonId) ?: d.lessonId,
            toleranceSemitones = prefs.getInt("tolerance", d.toleranceSemitones),
            anyOctave = prefs.getBoolean("anyOctave", d.anyOctave),
            rhythmWindowPercent = prefs.getInt("rhythmWindow", d.rhythmWindowPercent),
            keyboardMode = runCatching { KeyboardMode.valueOf(prefs.getString("keyboardMode", "")!!) }.getOrDefault(d.keyboardMode),
            theme = runCatching { RewardTheme.valueOf(prefs.getString("theme", "")!!) }.getOrDefault(d.theme),
            calibrationSkipped = prefs.getBoolean("calSkipped", false),
        )
    }

    fun saveSettings(s: Settings) {
        prefs.edit()
            .putString("lessonId", s.lessonId)
            .putInt("tolerance", s.toleranceSemitones)
            .putBoolean("anyOctave", s.anyOctave)
            .putInt("rhythmWindow", s.rhythmWindowPercent)
            .putString("keyboardMode", s.keyboardMode.name)
            .putString("theme", s.theme.name)
            .putBoolean("calSkipped", s.calibrationSkipped)
            .apply()
    }

    fun loadCalibration(): Calibration = Calibration.decode(prefs.getString("calibration", null))

    fun saveCalibration(c: Calibration) {
        prefs.edit().putString("calibration", c.encode()).apply()
    }
}
