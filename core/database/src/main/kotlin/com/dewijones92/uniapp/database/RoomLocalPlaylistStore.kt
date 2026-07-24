package com.dewijones92.uniapp.database

import com.dewijones92.uniapp.common.HttpUrl
import com.dewijones92.uniapp.data.playlist.LocalPlaylistStore
import com.dewijones92.uniapp.data.playlist.PlaylistItem
import com.dewijones92.uniapp.data.playlist.PlaylistPlayback
import com.dewijones92.uniapp.domain.LocalPlaylist
import com.dewijones92.uniapp.domain.MediaContentKind
import com.dewijones92.uniapp.domain.MediaItem
import com.dewijones92.uniapp.domain.MediaItemId
import com.dewijones92.uniapp.domain.PlaylistId
import com.dewijones92.uniapp.domain.SourceId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

/** [LocalPlaylistStore] backed by Room; the one place playlist entities meet domain types. */
public class RoomLocalPlaylistStore(private val dao: LocalPlaylistDao) : LocalPlaylistStore {

    override fun observePlaylists(): Flow<List<LocalPlaylist>> =
        dao.observePlaylists().map { rows -> rows.map { LocalPlaylist(PlaylistId(it.id), it.name, it.itemCount) } }

    override fun observeItems(id: PlaylistId): Flow<List<PlaylistItem>> =
        dao.observeItems(id.value).map { list -> list.mapNotNull { it.toPlaylistItem() } }

    override suspend fun create(name: String): PlaylistId {
        val id = UUID.randomUUID().toString()
        dao.upsertPlaylist(LocalPlaylistEntity(id, name, System.currentTimeMillis()))
        return PlaylistId(id)
    }

    override suspend fun rename(id: PlaylistId, name: String): Unit = dao.rename(id.value, name)

    override suspend fun delete(id: PlaylistId): Unit = dao.deletePlaylist(id.value)

    override suspend fun addItem(id: PlaylistId, item: PlaylistItem) {
        dao.insertItem(item.toEntity(id.value, dao.nextPosition(id.value)))
    }

    override suspend fun removeItem(id: PlaylistId, itemId: MediaItemId): Unit =
        dao.deleteItem(id.value, itemId.value)

    private fun LocalPlaylistItemEntity.toPlaylistItem(): PlaylistItem? {
        val playback = when (playbackType) {
            "VIDEO" -> PlaylistPlayback.Video(HttpUrl.parse(handle ?: return null) ?: return null)
            "LOCAL_VIDEO" -> PlaylistPlayback.LocalVideo(handle ?: return null)
            else -> PlaylistPlayback.Podcast(localPath = handle)
        }
        val item = MediaItem(
            id = MediaItemId(itemId),
            sourceId = SourceId(sourceId),
            title = title,
            publishedAt = null,
            duration = null,
            author = author,
            thumbnailUrl = thumbnailUrl?.let(HttpUrl::parse),
            mediaUrl = mediaUrl?.let(HttpUrl::parse),
            contentKind = runCatching { MediaContentKind.valueOf(contentKind) }.getOrDefault(MediaContentKind.STANDARD),
        )
        return PlaylistItem(item, playback)
    }

    private fun PlaylistItem.toEntity(playlistId: String, position: Long): LocalPlaylistItemEntity {
        val (type, handle) = when (val p = playback) {
            is PlaylistPlayback.Video -> "VIDEO" to p.watchUrl.value
            is PlaylistPlayback.LocalVideo -> "LOCAL_VIDEO" to p.localPath
            is PlaylistPlayback.Podcast -> "PODCAST" to p.localPath
        }
        return LocalPlaylistItemEntity(
            playlistId = playlistId,
            itemId = item.id.value,
            position = position,
            title = item.title,
            author = item.author,
            thumbnailUrl = item.thumbnailUrl?.value,
            sourceId = item.sourceId.value,
            contentKind = item.contentKind.name,
            playbackType = type,
            handle = handle,
            mediaUrl = item.mediaUrl?.value,
        )
    }
}
