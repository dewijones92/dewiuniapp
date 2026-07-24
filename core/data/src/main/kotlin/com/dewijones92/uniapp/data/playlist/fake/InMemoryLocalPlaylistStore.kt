package com.dewijones92.uniapp.data.playlist.fake

import com.dewijones92.uniapp.data.playlist.LocalPlaylistStore
import com.dewijones92.uniapp.data.playlist.PlaylistItem
import com.dewijones92.uniapp.domain.LocalPlaylist
import com.dewijones92.uniapp.domain.MediaItemId
import com.dewijones92.uniapp.domain.PlaylistId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** In-memory [LocalPlaylistStore] for tests and previews. */
public class InMemoryLocalPlaylistStore : LocalPlaylistStore {

    private data class Entry(val name: String, val items: List<PlaylistItem>)

    private val playlists = MutableStateFlow<Map<String, Entry>>(emptyMap())
    private var nextId = 0

    override fun observePlaylists(): Flow<List<LocalPlaylist>> = playlists.map { map ->
        map.entries.map { (id, e) -> LocalPlaylist(PlaylistId(id), e.name, e.items.size) }
    }

    override fun observeItems(id: PlaylistId): Flow<List<PlaylistItem>> =
        playlists.map { it[id.value]?.items.orEmpty() }

    override suspend fun create(name: String): PlaylistId {
        val id = "pl-${nextId++}"
        playlists.update { it + (id to Entry(name, emptyList())) }
        return PlaylistId(id)
    }

    override suspend fun rename(id: PlaylistId, name: String) {
        playlists.update { map -> map[id.value]?.let { map + (id.value to it.copy(name = name)) } ?: map }
    }

    override suspend fun delete(id: PlaylistId) {
        playlists.update { it - id.value }
    }

    override suspend fun addItem(id: PlaylistId, item: PlaylistItem) {
        playlists.update { map ->
            val entry = map[id.value] ?: return@update map
            if (entry.items.any { it.item.id == item.item.id }) return@update map
            map + (id.value to entry.copy(items = entry.items + item))
        }
    }

    override suspend fun removeItem(id: PlaylistId, itemId: MediaItemId) {
        playlists.update { map ->
            val entry = map[id.value] ?: return@update map
            map + (id.value to entry.copy(items = entry.items.filterNot { it.item.id == itemId }))
        }
    }
}
