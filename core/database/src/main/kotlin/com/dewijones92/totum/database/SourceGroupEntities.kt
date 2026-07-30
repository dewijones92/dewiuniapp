package com.dewijones92.totum.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** A user-created group of sources, read as one merged feed. */
@Entity(tableName = "source_groups")
public data class SourceGroupEntity(
    @PrimaryKey public val id: String,
    public val name: String,
    public val createdAtEpochMs: Long,
)

/**
 * One source's membership of a group.
 *
 * Denormalized like a playlist item, and for a directly analogous reason. A group may hold a
 * channel you never subscribed to, so there is nothing to look the membership up against —
 * it has to carry enough to be read on its own. Storing only the id looked tidier and made
 * exactly those members invisible.
 */
@Entity(
    tableName = "source_group_members",
    primaryKeys = ["groupId", "sourceId"],
    foreignKeys = [
        ForeignKey(
            entity = SourceGroupEntity::class,
            parentColumns = ["id"],
            childColumns = ["groupId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("groupId")],
)
public data class SourceGroupMemberEntity(
    public val groupId: String,
    public val sourceId: String,
    public val position: Long,
    public val title: String,
    /** VIDEO | PODCAST — which MediaSource to rebuild; mirrors the queue's playbackType. */
    public val kind: String,
    /** The channel or feed URL, which is what its pillar needs to fetch it. */
    public val url: String,
)
