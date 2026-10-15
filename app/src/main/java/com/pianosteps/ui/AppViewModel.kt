package com.pianosteps.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.pianosteps.audio.Calibration
import com.pianosteps.audio.MicEngine
import com.pianosteps.audio.MuteGate
import com.pianosteps.audio.ToneSynth
import com.pianosteps.data.Stores
import com.pianosteps.lessons.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val stores = Stores(app)
    val muteGate = MuteGate()
    val mic = MicEngine(muteGate)
    val synth = ToneSynth(muteGate)

    private val _settings = MutableStateFlow(stores.loadSettings())
    val settings: StateFlow<Settings> = _settings.asStateFlow()

    private val _calibration = MutableStateFlow(stores.loadCalibration())
    val calibration: StateFlow<Calibration> = _calibration.asStateFlow()

    fun updateSettings(f: (Settings) -> Settings) {
        _settings.update(f)
        stores.saveSettings(_settings.value)
    }

    fun setCalibration(c: Calibration) {
        _calibration.value = c
        stores.saveCalibration(c)
    }

    override fun onCleared() {
        synth.stop()
    }
}
