package com.dewijones92.uniapp.data.download

import com.dewijones92.uniapp.common.Diag
import com.dewijones92.uniapp.domain.DownloadState
import com.dewijones92.uniapp.domain.MediaItem
import com.dewijones92.uniapp.domain.MediaItemId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.io.File

/**
 * Routes every download through one [strategy] and records progress in the
 * [store]. Both pillars share this: a podcast enclosure and a resolved video
 * stream are both just a [MediaItem] with a fetchable [MediaItem.mediaUrl].
 */
public class DefaultDownloadManager(
    private val downloadDir: File,
    private val store: DownloadStore,
    private val strategy: DownloadStrategy,
    private val scope: CoroutineScope,
) : DownloadManager {

    init {
        // A "Downloading" record at startup means the process died mid-download;
        // its coroutine is gone, so clear it rather than show a stuck spinner.
        scope.launch {
            store.observeAll().first()
                .filterValues { it is DownloadState.Downloading }
                .keys.forEach { store.put(it, DownloadState.NotDownloaded) }
        }
    }

    override fun observeDownloads(): Flow<Map<MediaItemId, DownloadState>> = store.observeAll()

    override fun observe(id: MediaItemId): Flow<DownloadState> =
        store.observeAll().map { it[id] ?: DownloadState.NotDownloaded }.distinctUntilChanged()

    override suspend fun download(item: MediaItem, audioOnly: Boolean) {
        if (store.get(item.id).satisfies(audioOnly)) return

        store.put(item.id, DownloadState.Downloading(0, null))
        val target = File(downloadDir.apply { mkdirs() }, item.id.fileName())
        Diag.log("download", "start audioOnly=$audioOnly ${item.title}")
        scope.launch {
            strategy.download(item, target, audioOnly).collect { state ->
                store.put(item.id, state)
                when (state) {
                    is DownloadState.Failed -> Diag.warn("download", "failed ${item.title}: ${state.reason}")
                    is DownloadState.Downloaded -> Diag.log("download", "done ${item.title}")
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

    /** Opaque, filesystem-safe name derived from the stable item id. */
    private fun MediaItemId.fileName(): String = "${value.hashCode().toUInt()}.media"
}
