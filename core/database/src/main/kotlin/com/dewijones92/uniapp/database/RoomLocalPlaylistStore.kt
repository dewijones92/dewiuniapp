package com.dewijones92.uniapp.database

import com.dewijones92.uniapp.data.playlist.LocalPlaylistStore
import com.dewijones92.uniapp.data.playlist.PlaylistItem
import com.dewijones92.uniapp.domain.LocalPlaylist
import com.dewijones92.uniapp.domain.MediaItemId
import com.dewijones92.uniapp.domain.PlaylistId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

/** [LocalPlaylistStore] backed by Room; the one place playlist entities meet domain types. */
public class RoomLocalPlaylistStore(private val dao: LocalPlaylistDao) : LocalPlaylistStore {

    override fun observePlaylists(): Flow<List<LocalPlaylist>> =
        dao.observePlaylists().map { rows -> rows.map { LocalPlaylist(PlaylistId(it.id), it.name, it.itemCount) } }

    override fun observeItems(id: PlaylistId): Flow<List<PlaylistItem>> =
        dao.observeItems(id.value).map { list -> list.mapNotNull(::playlistItemFrom) }

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

    private fun PlaylistItem.toEntity(playlistId: String, position: Long): LocalPlaylistItemEntity {
        val (type, handle) = playback.typeAndHandle()
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
