package com.dewijones92.totum.database

import com.dewijones92.totum.common.HttpUrl
import com.dewijones92.totum.data.group.SourceGroupStore
import com.dewijones92.totum.domain.MediaSource
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
                    sources = byGroup[group.id].orEmpty().mapNotNull { it.toSource() },
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

    override suspend fun toggleMember(id: SourceGroupId, source: MediaSource): Boolean {
        val member = dao.memberCount(id.value, source.id.value) > 0
        if (member) {
            dao.deleteMember(id.value, source.id.value)
        } else {
            dao.insertMember(source.toEntity(id.value, dao.nextPosition(id.value)))
        }
        return !member
    }

    private fun MediaSource.toEntity(groupId: String, position: Long) = SourceGroupMemberEntity(
        groupId = groupId,
        sourceId = id.value,
        position = position,
        title = title,
        kind = when (this) {
            is MediaSource.VideoChannel -> VIDEO
            is MediaSource.PodcastFeed -> PODCAST
        },
        url = when (this) {
            is MediaSource.VideoChannel -> channelUrl.value
            is MediaSource.PodcastFeed -> feedUrl.value
        },
    )

    /**
     * Null for a row whose kind or URL no longer parses. Dropping the member is right: a
     * source we cannot rebuild cannot be fetched either, and keeping a half-formed one would
     * only push the failure to somewhere with less context.
     */
    private fun SourceGroupMemberEntity.toSource(): MediaSource? {
        val parsed = HttpUrl.parse(url) ?: return null
        return when (kind) {
            VIDEO -> MediaSource.VideoChannel(SourceId(sourceId), title, parsed)
            PODCAST -> MediaSource.PodcastFeed(SourceId(sourceId), title, parsed)
            else -> null
        }
    }

    private companion object {
        const val VIDEO = "VIDEO"
        const val PODCAST = "PODCAST"
    }
}
