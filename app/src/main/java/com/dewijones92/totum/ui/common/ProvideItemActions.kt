package com.dewijones92.totum.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import com.dewijones92.totum.di.AppContainer
import com.dewijones92.totum.domain.MediaItem
import com.dewijones92.totum.domain.MediaItemId
import com.dewijones92.totum.domain.MediaSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Builds the app-wide [ItemActions] and provides it to every row beneath.
 *
 * [onOpenChannel] is the one genuinely screen-shaped piece — where "go to channel" lands —
 * so the shell supplies it once instead of each screen hosting its own channel overlay.
 */
@Composable
internal fun ProvideItemActions(
    container: AppContainer,
    onOpenChannel: (MediaSource.VideoChannel) -> Unit,
    content: @Composable () -> Unit,
) {
    val rowActions = rememberMediaItemActions(container)
    val scope = rememberCoroutineScope()
    val actions = remember(container, rowActions, scope, onOpenChannel) {
        ContainerItemActions(container, rowActions, scope, onOpenChannel)
    }
    CompositionLocalProvider(LocalItemActions provides actions, content = content)
}

private class ContainerItemActions(
    private val container: AppContainer,
    private val rows: MediaItemActions,
    private val scope: CoroutineScope,
    private val onOpenChannel: (MediaSource.VideoChannel) -> Unit,
) : ItemActions {
    override fun playNext(item: MediaItem) = rows.playNext(item)
    override fun addToQueue(item: MediaItem) = rows.addToQueue(item)
    override fun addToPlaylist(item: MediaItem) = rows.addToPlaylist(item)
    override fun peek(item: MediaItem) = rows.peek(item)

    override fun download(item: MediaItem, audioOnly: Boolean) {
        scope.launch { container.downloadManager.download(item, audioOnly) }
    }

    override fun deleteDownload(id: MediaItemId) {
        scope.launch { container.downloadManager.delete(id) }
    }

    override fun setPlayed(id: MediaItemId, played: Boolean) {
        scope.launch { container.playbackProgressStore.setPlayed(id, played) }
    }

    override fun goToSource(item: MediaItem) {
        rows.goToSource(item) { source -> (source as? MediaSource.VideoChannel)?.let(onOpenChannel) }
    }

    override val audioMode: Boolean get() = rows.audioMode

    override fun switchMode(item: MediaItem) {
        // Labels come from the row; the mode change and its announcement live in MediaItemActions.
        rows.switchMode(item, toAudio = !rows.audioMode, audioOnMessage = "", videoOnMessage = "")
    }
}
