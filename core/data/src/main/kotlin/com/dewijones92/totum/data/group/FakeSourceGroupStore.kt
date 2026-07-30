package com.dewijones92.totum.data.group

import com.dewijones92.totum.domain.MediaSource
import com.dewijones92.totum.domain.SourceGroup
import com.dewijones92.totum.domain.SourceGroupId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/** In-memory [SourceGroupStore] for previews and tests. */
public class FakeSourceGroupStore(initial: List<SourceGroup> = emptyList()) : SourceGroupStore {

    private val groups = MutableStateFlow(initial)
    private var nextId = 0

    override fun observeGroups(): Flow<List<SourceGroup>> = groups

    override suspend fun create(name: String): SourceGroupId {
        val id = SourceGroupId("group-${nextId++}")
        groups.update { it + SourceGroup(id, name) }
        return id
    }

    override suspend fun rename(id: SourceGroupId, name: String) {
        groups.update { all -> all.map { if (it.id == id) it.copy(name = name) else it } }
    }

    override suspend fun delete(id: SourceGroupId) {
        groups.update { all -> all.filterNot { it.id == id } }
    }

    override suspend fun toggleMember(id: SourceGroupId, source: MediaSource): Boolean {
        val nowMember = groups.value.firstOrNull { it.id == id }?.let { source.id !in it } ?: false
        groups.update { all ->
            all.map { if (it.id == id) (if (nowMember) it.with(source) else it.without(source.id)) else it }
        }
        return nowMember
    }
}
