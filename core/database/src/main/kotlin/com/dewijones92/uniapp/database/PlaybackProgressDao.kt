package com.dewijones92.uniapp.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
public interface PlaybackProgressDao {

    @Query("SELECT * FROM playback_progress WHERE mediaItemId = :id")
    public suspend fun get(id: String): PlaybackProgressEntity?

    @Query("SELECT * FROM playback_progress")
    public fun observeAll(): Flow<List<PlaybackProgressEntity>>

    @Upsert
    public suspend fun upsert(entity: PlaybackProgressEntity)

    @Query("DELETE FROM playback_progress WHERE mediaItemId = :id")
    public suspend fun delete(id: String)
}
