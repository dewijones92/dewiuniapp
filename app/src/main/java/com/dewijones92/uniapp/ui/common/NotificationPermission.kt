package com.dewijones92.uniapp.ui.common

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/**
 * Asks for POST_NOTIFICATIONS the first time [playbackActive] becomes true —
 * the moment the media notification is about to matter. Without the grant,
 * Android 13+ never shows Media3's playback notification.
 */
@Composable
fun RequestNotificationPermissionOnFirstPlay(playbackActive: Boolean) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* Denial is respected; playback still works, just without a notification. */ }

    LaunchedEffect(playbackActive) {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        if (playbackActive && !granted) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
