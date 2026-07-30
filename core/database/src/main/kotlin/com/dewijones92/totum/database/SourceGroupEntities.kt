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
 * Only the source id, unlike a playlist item — a group points at subscriptions the app
 * already knows about, so denormalizing their titles here would be a second copy to keep
 * true. A playlist denormalizes because its items must survive offline and stream-URL
 * expiry; a membership has nothing to survive.
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
)
