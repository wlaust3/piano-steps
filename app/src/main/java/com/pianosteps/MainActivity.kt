package com.pianosteps

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.pianosteps.ui.AppViewModel
import com.pianosteps.ui.CalibrationScreen
import com.pianosteps.ui.LessonScreen
import com.pianosteps.ui.ParentScreen
import com.pianosteps.ui.PermissionScreen
import com.pianosteps.ui.PianoStepsTheme

private enum class Screen { LESSON, PARENT, CALIBRATION }

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { PianoStepsTheme { Root() } }
    }

    @Composable
    private fun Root() {
        val context = LocalContext.current
        fun micGranted() = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        var granted by remember { mutableStateOf(micGranted()) }
        var askedBefore by rememberSaveable { mutableStateOf(false) }
        var screen by rememberSaveable { mutableStateOf(Screen.LESSON) }
        val settings by vm.settings.collectAsStateWithLifecycle()
        val calibration by vm.calibration.collectAsStateWithLifecycle()
        var firstRunHandled by rememberSaveable { mutableStateOf(false) }

        val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
            granted = ok
            askedBefore = true
        }
        // permission may be toggled in system settings while we are in the background
        val lifecycle = LocalLifecycleOwner.current.lifecycle
        DisposableEffect(lifecycle) {
            val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) granted = micGranted() }
            lifecycle.addObserver(obs)
            onDispose { lifecycle.removeObserver(obs) }
        }

        if (!granted) {
            PermissionScreen(askedBefore) { launcher.launch(Manifest.permission.RECORD_AUDIO) }
            return
        }

        // first launch: offer the grown-up calibration once
        if (!firstRunHandled && !calibration.calibrated && !settings.calibrationSkipped) {
            CalibrationScreen(
                vm,
                onFinished = { firstRunHandled = true },
                onSkip = { vm.updateSettings { it.copy(calibrationSkipped = true) }; firstRunHandled = true },
            )
            return
        }

        BackHandler(enabled = screen != Screen.LESSON) { screen = Screen.LESSON }
        when (screen) {
            Screen.LESSON -> LessonScreen(vm, onParent = { screen = Screen.PARENT })
            Screen.PARENT -> ParentScreen(vm, onDone = { screen = Screen.LESSON }, onCalibrate = { screen = Screen.CALIBRATION })
            Screen.CALIBRATION -> CalibrationScreen(vm, onFinished = { screen = Screen.PARENT }, onSkip = { screen = Screen.PARENT })
        }
    }
}
