package com.dewijones92.totum.data.download

import com.dewijones92.totum.common.Diag
import com.dewijones92.totum.domain.DownloadState
import com.dewijones92.totum.domain.DownloadedMedia
import com.dewijones92.totum.domain.MediaItemId
import com.dewijones92.totum.domain.PlayableItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.io.File

/**
 * Routes every download through one [strategy] and records progress in the
 * [store]. Both pillars share this: a podcast enclosure and a video are both just a
 * [PlayableItem], and the handle says which mechanics apply.
 */
public class DefaultDownloadManager(
    private val downloadDir: File,
    private val store: DownloadStore,
    private val strategy: DownloadStrategy,
    private val scope: CoroutineScope,
) : DownloadManager {

    init {
        // A "Downloading" record at startup means the process died mid-download;
        // its coroutine is gone, so drop it rather than show a stuck spinner. An absent
        // record already reads as NotDownloaded, so there is nothing to write.
        scope.launch {
            store.observeAll().first()
                .filterValues { it is DownloadState.Downloading }
                .keys.forEach { store.remove(it) }
        }
    }

    // Replay nothing and never suspend the producer: a download must not be held up by
    // whether anyone is listening to notifications.
    private val _events = MutableSharedFlow<DownloadEvent>(extraBufferCapacity = EVENT_BUFFER)

    override fun events(): Flow<DownloadEvent> = _events.asSharedFlow()

    override fun observeDownloads(): Flow<Map<MediaItemId, DownloadState>> = store.observeAll()

    override fun observeDownloaded(): Flow<List<DownloadedMedia>> = store.observeDownloaded()

    override fun observe(id: MediaItemId): Flow<DownloadState> =
        store.observeAll().map { it[id] ?: DownloadState.NotDownloaded }.distinctUntilChanged()

    override suspend fun download(item: PlayableItem, audioOnly: Boolean) {
        val media = item.item
        if (store.get(media.id).satisfies(audioOnly)) return

        store.put(item, DownloadState.Downloading(0, null))
        _events.tryEmit(DownloadEvent(media, DownloadState.Downloading(0, null)))
        val target = File(downloadDir.apply { mkdirs() }, media.id.fileName())
        Diag.log("download", "start audioOnly=$audioOnly ${media.title}")
        scope.launch {
            strategy.download(item, target, audioOnly).collect { state ->
                store.put(item, state)
                _events.tryEmit(DownloadEvent(media, state))
                when (state) {
                    is DownloadState.Failed -> Diag.warn("download", "failed ${media.title}: ${state.reason}")
                    is DownloadState.Downloaded -> Diag.log("download", "done ${media.title}")
                    else -> Unit
                }
            }
        }
    }

    /**
     * Whether an existing record already covers a request. An audio-only file does
     * NOT cover a request for the full media — otherwise the queue's automatic audio
     * download would make "Download" look done when the video was never fetched.
     */
    private fun DownloadState.satisfies(audioOnly: Boolean): Boolean = when (this) {
        is DownloadState.Downloading -> true
        is DownloadState.Downloaded -> audioOnly || !this.audioOnly
        else -> false
    }

    override suspend fun delete(id: MediaItemId) {
        (store.get(id) as? DownloadState.Downloaded)?.let { File(it.localPath).delete() }
        store.remove(id)
    }

    private companion object {
        const val EVENT_BUFFER = 64
    }

    /** Opaque, filesystem-safe name derived from the stable item id. */
    private fun MediaItemId.fileName(): String = "${value.hashCode().toUInt()}.media"
}
