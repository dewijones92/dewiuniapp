package com.dewijones92.uniapp.data.history.fake

import com.dewijones92.uniapp.data.history.PlayHistoryStore
import com.dewijones92.uniapp.data.playlist.PlaylistItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/** In-memory [PlayHistoryStore] for tests and previews. */
public class InMemoryPlayHistoryStore(private val limit: Int = 50) : PlayHistoryStore {

    private val history = MutableStateFlow<List<PlaylistItem>>(emptyList())

    override fun observe(): Flow<List<PlaylistItem>> = history

    override suspend fun record(item: PlaylistItem) {
        history.update { current ->
            (listOf(item) + current.filterNot { it.item.id == item.item.id }).take(limit)
        }
    }

    override suspend fun clear() {
        history.value = emptyList()
    }
}
