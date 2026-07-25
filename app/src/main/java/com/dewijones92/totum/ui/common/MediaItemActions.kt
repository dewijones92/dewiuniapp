package com.dewijones92.totum.ui.common

import android.widget.Toast
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import com.dewijones92.totum.data.source.SourceLocator
import com.dewijones92.totum.di.AppContainer
import com.dewijones92.totum.domain.MediaItem
import com.dewijones92.totum.domain.MediaSource
import com.dewijones92.totum.playlist.toPlayableOrNull
import com.dewijones92.totum.queue.PlaybackQueue
import com.dewijones92.totum.settings.AppPreferences
import com.dewijones92.totum.settings.PlaybackMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The per-row long-press actions shared by every feed, both pillars — queue and
 * playlist wiring lives here once so no screen re-implements it. A feed item's
 * pillar/handle is inferred from its media URL ([toPlayableOrNull]); items
 * without a playable URL yet simply can't be queued (the action no-ops).
 */
class MediaItemActions internal constructor(
    private val queue: PlaybackQueue,
    private val openPlaylistPicker: (MediaItem) -> Unit,
    private val locator: SourceLocator,
    private val scope: CoroutineScope,
    private val preferences: AppPreferences,
    private val announce: (String) -> Unit,
) {
    /** The mode right now, so a row can label its action "Listen only" vs "Watch with video". */
    val audioMode: Boolean get() = preferences.settings.value.playbackMode == PlaybackMode.AUDIO

    /**
     * Plays [item] the other way round and **makes that the mode**, announcing it —
     * a row action that silently changed a global setting would be baffling, and
     * hiding the mode in a settings screen would be worse.
     */
    fun switchMode(item: MediaItem, toAudio: Boolean, audioOnMessage: String, videoOnMessage: String) {
        preferences.setPlaybackMode(if (toAudio) PlaybackMode.AUDIO else PlaybackMode.VIDEO)
        announce(if (toAudio) audioOnMessage else videoOnMessage)
        val playable = item.toPlayableOrNull() ?: return
        scope.launch { queue.playNow(playable) }
    }
    fun playNext(item: MediaItem) {
        item.toPlayableOrNull()?.let(queue::playNext)
    }

    fun addToQueue(item: MediaItem) {
        item.toPlayableOrNull()?.let(queue::enqueue)
    }

    fun addToPlaylist(item: MediaItem) {
        openPlaylistPicker(item)
    }

    /**
     * Plays the item **without touching the queue** — a one-off, so a carefully
     * built queue survives. The counterpart to tapping, which queues.
     */
    fun peek(item: MediaItem) {
        val playable = item.toPlayableOrNull() ?: return
        scope.launch { queue.peek(playable) }
    }

    /**
     * Resolves the item's source and hands it to [onResolved] to navigate to.
     * A subscribed podcast feed is a local lookup; a video's channel is resolved
     * through the engine, so this may take a moment. Does nothing when the source
     * can't be determined.
     */
    fun goToSource(item: MediaItem, onResolved: (MediaSource) -> Unit) {
        scope.launch { locator.locate(item)?.let(onResolved) }
    }
}

/** Wires [MediaItemActions] from the container and hosts the add-to-playlist picker dialog. */
/**
 * Wires [MediaItemActions] from the container, hosting the add-to-playlist picker and
 * the snackbar its mode switch announces through. [snackbar] lets a screen that
 * already has a host share it; otherwise messages fall back to a toast.
 */
@Composable
fun rememberMediaItemActions(
    container: AppContainer,
    snackbar: SnackbarHostState? = null,
): MediaItemActions {
    val adder = com.dewijones92.totum.ui.playlist.rememberPlaylistAdder(container)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    return remember(container, adder, scope, snackbar) {
        MediaItemActions(
            queue = container.playbackQueue,
            openPlaylistPicker = adder,
            locator = container.sourceLocator,
            scope = scope,
            preferences = container.appPreferences,
            announce = { message ->
                if (snackbar != null) {
                    scope.launch { snackbar.showSnackbar(message) }
                } else {
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                }
            },
        )
    }
}
