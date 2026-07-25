package com.dewijones92.uniapp.data.queue.fake

import com.dewijones92.uniapp.data.queue.QueueEntry
import com.dewijones92.uniapp.data.queue.QueueStore

/** In-memory [QueueStore] for tests and previews. */
public class InMemoryQueueStore(initial: List<QueueEntry> = emptyList()) : QueueStore {

    private var entries: List<QueueEntry> = initial

    override suspend fun load(): List<QueueEntry> = entries

    override suspend fun save(entries: List<QueueEntry>) {
        this.entries = entries
    }
}
