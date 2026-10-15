package com.pianosteps.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Friendly rationale shown before the system microphone prompt. */
@Composable
fun PermissionScreen(askedBefore: Boolean, onAllow: () -> Unit) {
    val context = LocalContext.current
    Column(
        Modifier.fillMaxSize().background(PsColors.Background).systemBarsPadding().padding(24.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("🎹👂", fontSize = 64.sp)
        Spacer(Modifier.height(8.dp))
        Text("Piano Steps needs to listen", style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Text(
            "To know which piano key was played, the app listens through the microphone while a lesson is on. " +
                "Sound is analysed on this device only, right away. It is never recorded, saved or sent anywhere.",
            style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        BigButton("Allow the microphone", onAllow)
        if (askedBefore) {
            Spacer(Modifier.height(8.dp))
            Text("If nothing pops up, the permission was turned off. Open Settings to switch it on:", textAlign = TextAlign.Center)
            OutlinedButton(onClick = {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }) { Text("Open app settings") }
        }
    }
}
