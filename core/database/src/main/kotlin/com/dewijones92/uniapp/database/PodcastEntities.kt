package com.dewijones92.uniapp.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "podcast_feeds")
public data class FeedEntity(
    @PrimaryKey val id: String,
    val title: String,
    val feedUrl: String,
    val websiteUrl: String?,
    val subscribedAtEpochMs: Long,
)

@Entity(
    tableName = "podcast_episodes",
    foreignKeys = [
        ForeignKey(
            entity = FeedEntity::class,
            parentColumns = ["id"],
            childColumns = ["feedId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("feedId")],
)
public data class EpisodeEntity(
    @PrimaryKey val id: String,
    val feedId: String,
    val title: String,
    val publishedAtEpochMs: Long?,
    val durationSeconds: Long?,
    val description: String?,
    val thumbnailUrl: String?,
    val mediaUrl: String?,
)
