package com.dewijones92.uniapp.database

import androidx.room.Dao
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
}
