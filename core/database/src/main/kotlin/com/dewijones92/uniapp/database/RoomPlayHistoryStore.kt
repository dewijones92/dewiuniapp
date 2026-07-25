package com.dewijones92.uniapp.database

import com.dewijones92.uniapp.data.history.PlayHistoryStore
import com.dewijones92.uniapp.domain.PlayableItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** [PlayHistoryStore] backed by Room; reuses the shared playlist-item column mapping. */
public class RoomPlayHistoryStore(
    private val dao: PlayHistoryDao,
    private val limit: Int = 50,
) : PlayHistoryStore {

    override fun observe(): Flow<List<PlayableItem>> = dao.observe(limit).map { rows ->
        rows.mapNotNull(::playlistItemFrom)
    }

    override suspend fun record(item: PlayableItem) {
        val (type, handle) = item.handle.typeAndHandle()
        dao.upsert(
            PlayHistoryEntity(
                itemId = item.item.id.value,
                lastPlayedAtEpochMs = System.currentTimeMillis(),
                title = item.item.title,
                author = item.item.author,
                thumbnailUrl = item.item.thumbnailUrl?.value,
                sourceId = item.item.sourceId.value,
                contentKind = item.item.contentKind.name,
                playbackType = type,
                handle = handle,
                mediaUrl = item.item.mediaUrl?.value,
            ),
        )
        dao.trim(limit)
    }

    override suspend fun clear(): Unit = dao.clear()
}
