package com.dewijones92.totum.data.group

import com.dewijones92.totum.domain.SourceGroup
import com.dewijones92.totum.domain.SourceGroupId
import com.dewijones92.totum.domain.SourceId
import kotlinx.coroutines.flow.Flow

/**
 * Port: the user's source groups and their membership. Storage-agnostic, like every other
 * port here — the Room implementation lives in `:core:database`, the fake in tests.
 *
 * Membership is a toggle rather than add/remove-with-position: a group is a SET of sources
 * read as one feed, and the only edit that means anything to the reader is "is this channel
 * in it or not".
 */
public interface SourceGroupStore {
    public fun observeGroups(): Flow<List<SourceGroup>>

    public suspend fun create(name: String): SourceGroupId

    public suspend fun rename(id: SourceGroupId, name: String)

    public suspend fun delete(id: SourceGroupId)

    /** Adds [sourceId] to the group, or removes it if already a member. Returns the new state. */
    public suspend fun toggleMember(id: SourceGroupId, sourceId: SourceId): Boolean
}
