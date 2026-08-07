package com.dewijones92.totum.database

import com.dewijones92.totum.data.download.DownloadStore
import com.dewijones92.totum.domain.DownloadState
import com.dewijones92.totum.domain.DownloadedMedia
import com.dewijones92.totum.domain.MediaItemId
import com.dewijones92.totum.domain.PlayableItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Room-backed [DownloadStore]; the only place download entities and domain state meet. */
public class RoomDownloadStore(private val dao: DownloadDao) : DownloadStore {

    override fun observeAll(): Flow<Map<MediaItemId, DownloadState>> =
        dao.observeAll().map { rows -> rows.associate { MediaItemId(it.itemId) to it.toState() } }

    override fun observeDownloaded(): Flow<List<DownloadedMedia>> =
        dao.observeAll().map { rows -> rows.mapNotNull { it.toDownloaded() } }

    override suspend fun get(id: MediaItemId): DownloadState =
        dao.get(id.value)?.toState() ?: DownloadState.NotDownloaded

    override suspend fun put(item: PlayableItem, state: DownloadState) {
        dao.upsert(state.toEntity(item))
    }

    override suspend fun remove(id: MediaItemId) {
        dao.delete(id.value)
    }

    private fun DownloadEntity.toState(): DownloadState = when (status) {
        STATUS_DOWNLOADING -> DownloadState.Downloading(downloadedBytes, totalBytes)
        STATUS_DOWNLOADED -> DownloadState.Downloaded(localPath.orEmpty(), audioOnly)
        STATUS_FAILED -> DownloadState.Failed(failureReason.orEmpty())
        else -> DownloadState.NotDownloaded
    }

    /** Null unless the row is a finished download that still describes a playable item. */
    private fun DownloadEntity.toDownloaded(): DownloadedMedia? {
        if (status != STATUS_DOWNLOADED) return null
        val path = localPath?.takeIf { it.isNotEmpty() } ?: return null
        return DownloadedMedia(playlistItemFrom(this) ?: return null, path, audioOnly)
    }

    private fun DownloadState.toEntity(item: PlayableItem): DownloadEntity {
        val (playbackType, handleValue) = item.handle.typeAndHandle()
        val media = item.item
        return DownloadEntity(
            itemId = media.id.value,
            status = statusKey(),
            downloadedBytes = (this as? DownloadState.Downloading)?.downloadedBytes ?: 0,
            totalBytes = (this as? DownloadState.Downloading)?.totalBytes,
            localPath = (this as? DownloadState.Downloaded)?.localPath,
            failureReason = (this as? DownloadState.Failed)?.reason,
            audioOnly = (this as? DownloadState.Downloaded)?.audioOnly == true,
            title = media.title,
            author = media.author,
            thumbnailUrl = media.thumbnailUrl?.value,
            sourceId = media.sourceId.value,
            contentKind = media.contentKind.name,
            // The listing's own facts, or they are lost the moment the row is written -- see
            // PlaylistItemColumns.
            viewsText = media.viewsText,
            publishedText = media.publishedText,
            publishedAtEpochMs = media.publishedAt?.toEpochMilli(),
            playbackType = playbackType,
            handle = handleValue,
            mediaUrl = media.mediaUrl?.value,
        )
    }

    private fun DownloadState.statusKey(): String = when (this) {
        DownloadState.NotDownloaded -> STATUS_NOT_DOWNLOADED
        is DownloadState.Downloading -> STATUS_DOWNLOADING
        is DownloadState.Downloaded -> STATUS_DOWNLOADED
        is DownloadState.Failed -> STATUS_FAILED
    }

    private companion object {
        const val STATUS_NOT_DOWNLOADED = "not_downloaded"
        const val STATUS_DOWNLOADING = "downloading"
        const val STATUS_DOWNLOADED = "downloaded"
        const val STATUS_FAILED = "failed"
    }
}
