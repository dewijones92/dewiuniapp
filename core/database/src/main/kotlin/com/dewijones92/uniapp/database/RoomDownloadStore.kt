package com.dewijones92.uniapp.database

import com.dewijones92.uniapp.data.download.DownloadStore
import com.dewijones92.uniapp.domain.DownloadState
import com.dewijones92.uniapp.domain.MediaItemId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Room-backed [DownloadStore]; the only place download entities and domain state meet. */
public class RoomDownloadStore(private val dao: DownloadDao) : DownloadStore {

    override fun observeAll(): Flow<Map<MediaItemId, DownloadState>> =
        dao.observeAll().map { rows -> rows.associate { MediaItemId(it.mediaItemId) to it.toState() } }

    override suspend fun get(id: MediaItemId): DownloadState =
        dao.get(id.value)?.toState() ?: DownloadState.NotDownloaded

    override suspend fun put(id: MediaItemId, state: DownloadState) {
        dao.upsert(state.toEntity(id.value))
    }

    override suspend fun remove(id: MediaItemId) {
        dao.delete(id.value)
    }

    private fun DownloadEntity.toState(): DownloadState = when (status) {
        STATUS_DOWNLOADING -> DownloadState.Downloading(downloadedBytes, totalBytes)
        STATUS_DOWNLOADED -> DownloadState.Downloaded(localPath.orEmpty())
        STATUS_FAILED -> DownloadState.Failed(failureReason.orEmpty())
        else -> DownloadState.NotDownloaded
    }

    private fun DownloadState.toEntity(id: String): DownloadEntity = when (this) {
        DownloadState.NotDownloaded ->
            DownloadEntity(id, STATUS_NOT_DOWNLOADED, 0, null, null, null)
        is DownloadState.Downloading ->
            DownloadEntity(id, STATUS_DOWNLOADING, downloadedBytes, totalBytes, null, null)
        is DownloadState.Downloaded ->
            DownloadEntity(id, STATUS_DOWNLOADED, 0, null, localPath, null)
        is DownloadState.Failed ->
            DownloadEntity(id, STATUS_FAILED, 0, null, null, reason)
    }

    private companion object {
        const val STATUS_NOT_DOWNLOADED = "not_downloaded"
        const val STATUS_DOWNLOADING = "downloading"
        const val STATUS_DOWNLOADED = "downloaded"
        const val STATUS_FAILED = "failed"
    }
}
