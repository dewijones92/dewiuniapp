package com.dewijones92.uniapp.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
public interface PlaybackProgressDao {

    @Query("SELECT * FROM playback_progress WHERE mediaItemId = :id")
    public suspend fun get(id: String): PlaybackProgressEntity?

    @Upsert
    public suspend fun upsert(entity: PlaybackProgressEntity)

    @Query("DELETE FROM playback_progress WHERE mediaItemId = :id")
    public suspend fun delete(id: String)
}
