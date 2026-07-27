package com.dewijones92.totum.ui.player

import android.app.Activity
import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Rect
import android.graphics.drawable.Icon
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.PictureInPictureModeChangedInfo
import androidx.core.content.ContextCompat
import androidx.core.util.Consumer
import com.dewijones92.totum.R
import com.dewijones92.totum.common.Diag

/**
 * Keeps the video playing in a floating window when you leave the app.
 *
 * **Video only, deliberately.** This is the one place a pillar asymmetry is honest rather
 * than a design failure: a podcast has no picture to float, and audio already continues
 * in the background through the media session. Everything else about playback stays on
 * the one shared seam.
 *
 * Nothing here forces PiP. On Android 12+ the system enters it automatically when the
 * user leaves, because [PictureInPictureParams.Builder.setAutoEnterEnabled] is set while
 * a video is playing — which is the behaviour people actually expect, rather than a
 * button they must remember to press.
 */
internal const val PIP_ACTION = "com.dewijones92.totum.PIP_TOGGLE_PLAY"

/**
 * Where the video currently sits on screen, so the system can animate *from* the picture
 * into the floating window instead of cross-fading the whole app. Reported by the video
 * stage, which is the only composable that knows; null when no video is on screen.
 */
internal class VideoBounds {
    var onScreen: Rect? by mutableStateOf(null)
}

internal val LocalVideoBounds = staticCompositionLocalOf<VideoBounds?> { null }

/** Reports this composable's window position as the PiP source rectangle. */
internal fun Modifier.reportVideoBounds(bounds: VideoBounds?): Modifier =
    if (bounds == null) {
        this
    } else {
        onGloballyPositioned { coordinates ->
            val topLeft = coordinates.positionInWindow()
            bounds.onScreen = Rect(
                topLeft.x.toInt(),
                topLeft.y.toInt(),
                topLeft.x.toInt() + coordinates.size.width,
                topLeft.y.toInt() + coordinates.size.height,
            )
        }
    }

/**
 * Publishes PiP parameters while [hasVideo], and registers the play/pause control shown
 * inside the floating window.
 *
 * [aspectRatio] shapes the window to the video, so a 16:9 clip is not letterboxed into a
 * square. [isPlaying] only changes which icon the control shows.
 */
@Composable
internal fun PictureInPictureEffect(
    hasVideo: Boolean,
    isPlaying: Boolean,
    aspectRatio: Float?,
    bounds: VideoBounds,
    onTogglePlayPause: () -> Unit,
) {
    val context = LocalContext.current
    val activity = context.findActivity()

    DisposableEffect(activity, hasVideo, isPlaying, aspectRatio, bounds.onScreen) {
        // No API guard: minSdk is 34, so auto-enter and seamless resize always exist.
        if (activity == null) return@DisposableEffect onDispose {}
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == PIP_ACTION) onTogglePlayPause()
            }
        }
        ContextCompat.registerReceiver(
            activity,
            receiver,
            IntentFilter(PIP_ACTION),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        runCatching {
            activity.setPictureInPictureParams(
                activity.pipParams(hasVideo, isPlaying, aspectRatio, bounds.onScreen),
            )
        }.onFailure { Diag.warn("pip", "could not publish params", it) }

        onDispose {
            runCatching { activity.unregisterReceiver(receiver) }
            // Auto-enter is cleared when the video goes away, or closing the player would
            // float an audio item's blank stage.
            runCatching {
                activity.setPictureInPictureParams(
                    activity.pipParams(enabled = false, isPlaying = false, aspectRatio = null, source = null),
                )
            }
        }
    }
}

private fun Activity.pipParams(enabled: Boolean, isPlaying: Boolean, aspectRatio: Float?, source: Rect?) =
    PictureInPictureParams.Builder()
        .setAutoEnterEnabled(enabled)
        .setSeamlessResizeEnabled(true)
        .setSourceRectHint(source)
        .apply { aspectRatio?.let { setAspectRatio(it.toSafeRational()) } }
        .setActions(if (enabled) listOf(playPauseAction(isPlaying)) else emptyList())
        .build()

private fun Activity.playPauseAction(isPlaying: Boolean): RemoteAction {
    val label = getString(if (isPlaying) R.string.pause else R.string.play)
    val icon = if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
    val intent = PendingIntent.getBroadcast(
        this,
        0,
        Intent(PIP_ACTION).setPackage(packageName),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
    return RemoteAction(Icon.createWithResource(this, icon), label, label, intent)
}

/**
 * Android rejects a PiP aspect ratio outside roughly 1:2.39..2.39:1 by throwing, so an
 * extreme one is clamped rather than allowed to crash the activity.
 */
private fun Float.toSafeRational(): Rational {
    val clamped = coerceIn(MIN_PIP_ASPECT, MAX_PIP_ASPECT)
    return Rational((clamped * RATIONAL_SCALE).toInt(), RATIONAL_SCALE)
}

private const val MIN_PIP_ASPECT = 0.42f
private const val MAX_PIP_ASPECT = 2.39f
private const val RATIONAL_SCALE = 1000

/**
 * Whether the app is currently in the floating window. The shell uses it to render the
 * picture alone: a PiP window a few centimetres across has no room for a nav bar, a mini
 * player or a scrolling description, and showing them makes the video unwatchably small.
 */
@Composable
internal fun rememberIsInPictureInPicture(): Boolean {
    val activity = LocalContext.current.findActivity() as? ComponentActivity
    var inPip by remember { mutableStateOf(activity?.isInPictureInPictureMode == true) }
    DisposableEffect(activity) {
        if (activity == null) return@DisposableEffect onDispose {}
        val listener = Consumer<PictureInPictureModeChangedInfo> { info ->
            inPip = info.isInPictureInPictureMode
            Diag.log("pip", "in picture-in-picture = ${info.isInPictureInPictureMode}")
        }
        activity.addOnPictureInPictureModeChangedListener(listener)
        onDispose { activity.removeOnPictureInPictureModeChangedListener(listener) }
    }
    return inPip
}

/** Walks the context wrapper chain to the hosting [Activity], if any. */
private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
