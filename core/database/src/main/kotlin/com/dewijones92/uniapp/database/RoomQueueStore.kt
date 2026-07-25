package com.dewijones92.uniapp.database

import com.dewijones92.uniapp.data.queue.QueueEntry
import com.dewijones92.uniapp.data.queue.QueueGroup
import com.dewijones92.uniapp.data.queue.QueueStore

/** [QueueStore] backed by Room, reusing the shared playable-item column mapping. */
public class RoomQueueStore(private val dao: QueueDao) : QueueStore {

    override suspend fun load(): List<QueueEntry> = dao.all().mapNotNull { row ->
        val item = playlistItemFrom(row) ?: return@mapNotNull null
        val group = row.groupId?.let { QueueGroup(it, row.groupTitle.orEmpty()) }
        QueueEntry(item, group)
    }

    override suspend fun save(entries: List<QueueEntry>) {
        dao.replaceAll(
            entries.mapIndexed { index, entry ->
                val (type, handle) = entry.item.handle.typeAndHandle()
                val media = entry.item.item
                QueueEntity(
                    position = index.toLong(),
                    groupId = entry.group?.id,
                    groupTitle = entry.group?.title,
                    itemId = media.id.value,
                    title = media.title,
                    author = media.author,
                    thumbnailUrl = media.thumbnailUrl?.value,
                    sourceId = media.sourceId.value,
                    contentKind = media.contentKind.name,
                    playbackType = type,
                    handle = handle,
                    mediaUrl = media.mediaUrl?.value,
                )
            },
        )
    }
}
