package com.dewijones92.uniapp.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
public interface PodcastDao {

    @Query("SELECT * FROM podcast_feeds WHERE sourceType = :sourceType ORDER BY subscribedAtEpochMs DESC")
    public fun observeFeeds(sourceType: String): Flow<List<FeedEntity>>

    @Query(
        "SELECT e.* FROM podcast_episodes e " +
            "JOIN podcast_feeds f ON e.feedId = f.id " +
            "WHERE f.sourceType = :sourceType " +
            "ORDER BY e.publishedAtEpochMs DESC NULLS LAST, e.id",
    )
    public fun observeEpisodes(sourceType: String): Flow<List<EpisodeEntity>>

    @Query("SELECT COUNT(*) FROM podcast_feeds WHERE id = :id")
    public suspend fun countFeeds(id: String): Int

    @Upsert
    public suspend fun upsertFeed(feed: FeedEntity)

    @Upsert
    public suspend fun upsertEpisodes(episodes: List<EpisodeEntity>)

    @Transaction
    public suspend fun upsertFeedWithEpisodes(feed: FeedEntity, episodes: List<EpisodeEntity>) {
        upsertFeed(feed)
        upsertEpisodes(episodes)
    }

    @Query("DELETE FROM podcast_feeds WHERE id = :id")
    public suspend fun deleteFeed(id: String)

    /** Inserts only feeds whose id isn't already stored (existing origin preserved). */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    public suspend fun insertFeedsIgnoring(feeds: List<FeedEntity>): List<Long>

    @Query("DELETE FROM podcast_feeds WHERE sourceType = :sourceType AND origin = :origin AND id NOT IN (:keepIds)")
    public suspend fun deleteByOriginNotIn(sourceType: String, origin: String, keepIds: List<String>): Int

    /**
     * Adds newly-imported feeds and prunes previously-imported ones no longer
     * present, in one transaction. Inserts ignore existing ids, so a matching
     * manually-added row keeps its origin (and its protection); only rows with
     * [origin] are pruned. An empty [keepIds] means no subscriptions of this
     * type remain, so every imported row goes. Returns the added/pruned counts.
     */
    @Transaction
    public suspend fun reconcileImported(
        sourceType: String,
        origin: String,
        feeds: List<FeedEntity>,
        keepIds: List<String>,
    ): ReconcileCounts {
        val added = insertFeedsIgnoring(feeds).count { it != INSERT_IGNORED }
        // No real id is blank, so a sentinel keep-list prunes every imported row.
        val keep = keepIds.ifEmpty { listOf("") }
        val removed = deleteByOriginNotIn(sourceType, origin, keep)
        return ReconcileCounts(added = added, removed = removed)
    }

    public companion object {
        /** Row id Room returns for a row skipped by an IGNORE-conflict insert. */
        private const val INSERT_IGNORED = -1L
    }
}

/** How many rows a [PodcastDao.reconcileImported] inserted and pruned. */
public data class ReconcileCounts(val added: Int, val removed: Int)
