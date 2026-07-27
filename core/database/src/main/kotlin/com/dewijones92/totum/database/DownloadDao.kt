package com.dewijones92.totum.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
public interface DownloadDao {

    @Query("SELECT * FROM downloads")
    public fun observeAll(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE itemId = :id")
    public suspend fun get(id: String): DownloadEntity?

    @Upsert
    public suspend fun upsert(entity: DownloadEntity)

    @Query("DELETE FROM downloads WHERE itemId = :id")
    public suspend fun delete(id: String)
}
