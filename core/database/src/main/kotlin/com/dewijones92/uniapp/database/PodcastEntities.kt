package com.dewijones92.uniapp.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A subscribed source — a podcast feed or a video channel, distinguished by
 * [sourceType] ("podcast" | "channel"). `url` holds the feed URL or channel
 * URL; `websiteUrl` is podcast-only.
 */
@Entity(tableName = "podcast_feeds")
public data class FeedEntity(
    @PrimaryKey val id: String,
    val sourceType: String,
    val title: String,
    val feedUrl: String,
    val websiteUrl: String?,
    val subscribedAtEpochMs: Long,
)

/**
 * A download record, keyed by media item id. Status is a small string enum;
 * localPath is set only when status == "downloaded".
 */
@Entity(tableName = "downloads")
public data class DownloadEntity(
    @PrimaryKey val mediaItemId: String,
    val status: String,
    val downloadedBytes: Long,
    val totalBytes: Long?,
    val localPath: String?,
    val failureReason: String?,
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
    val author: String?,
    val publishedAtEpochMs: Long?,
    val durationSeconds: Long?,
    val description: String?,
    val thumbnailUrl: String?,
    val mediaUrl: String?,
)
