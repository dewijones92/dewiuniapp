package com.dewijones92.totum.data.queue.fake

import com.dewijones92.totum.data.queue.QueueSnapshot
import com.dewijones92.totum.data.queue.QueueStore

/** In-memory [QueueStore] for tests and previews. */
public class InMemoryQueueStore(initial: QueueSnapshot = QueueSnapshot()) : QueueStore {

    private var snapshot: QueueSnapshot = initial

    override suspend fun load(): QueueSnapshot = snapshot

    override suspend fun save(snapshot: QueueSnapshot) {
        this.snapshot = snapshot
    }
}
