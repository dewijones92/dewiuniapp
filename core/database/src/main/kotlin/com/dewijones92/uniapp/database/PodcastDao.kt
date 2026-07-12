package com.dewijones92.uniapp.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
public interface PodcastDao {

    @Query("SELECT * FROM podcast_feeds ORDER BY subscribedAtEpochMs DESC")
    public fun observeFeeds(): Flow<List<FeedEntity>>

    @Query("SELECT * FROM podcast_episodes ORDER BY publishedAtEpochMs DESC NULLS LAST, id")
    public fun observeEpisodes(): Flow<List<EpisodeEntity>>

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
