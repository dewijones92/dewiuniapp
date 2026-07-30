package com.dewijones92.totum.database

import com.dewijones92.totum.data.group.SourceGroupStore
import com.dewijones92.totum.domain.SourceGroup
import com.dewijones92.totum.domain.SourceGroupId
import com.dewijones92.totum.domain.SourceId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.util.UUID

/** [SourceGroupStore] backed by Room; the one place group entities meet domain types. */
public class RoomSourceGroupStore(private val dao: SourceGroupDao) : SourceGroupStore {

    /**
     * Groups and memberships are observed as two streams and joined here rather than by a
     * Room relation query, so a membership change emits ONE combined list. A per-group
     * query would emit a list of flows, and every screen would have to flatten it.
     */
    override fun observeGroups(): Flow<List<SourceGroup>> =
        combine(dao.observeGroups(), dao.observeMembers()) { groups, members ->
            val byGroup = members.groupBy { it.groupId }
            groups.map { group ->
                SourceGroup(
                    id = SourceGroupId(group.id),
                    name = group.name,
                    sourceIds = byGroup[group.id].orEmpty().map { SourceId(it.sourceId) },
                )
            }
        }

    override suspend fun create(name: String): SourceGroupId {
        val id = UUID.randomUUID().toString()
        dao.upsertGroup(SourceGroupEntity(id, name, System.currentTimeMillis()))
        return SourceGroupId(id)
    }

    override suspend fun rename(id: SourceGroupId, name: String): Unit = dao.rename(id.value, name)

    override suspend fun delete(id: SourceGroupId): Unit = dao.deleteGroup(id.value)

    override suspend fun toggleMember(id: SourceGroupId, sourceId: SourceId): Boolean {
        val member = dao.memberCount(id.value, sourceId.value) > 0
        if (member) {
            dao.deleteMember(id.value, sourceId.value)
        } else {
            dao.insertMember(SourceGroupMemberEntity(id.value, sourceId.value, dao.nextPosition(id.value)))
        }
        return !member
    }
}
