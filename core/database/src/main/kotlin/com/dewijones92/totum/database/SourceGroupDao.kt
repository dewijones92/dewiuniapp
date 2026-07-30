package com.dewijones92.totum.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
public interface SourceGroupDao {

    @Query("SELECT * FROM source_groups ORDER BY createdAtEpochMs")
    public fun observeGroups(): Flow<List<SourceGroupEntity>>

    @Query("SELECT * FROM source_group_members ORDER BY groupId, position")
    public fun observeMembers(): Flow<List<SourceGroupMemberEntity>>

    @Upsert
    public suspend fun upsertGroup(group: SourceGroupEntity)

    @Query("UPDATE source_groups SET name = :name WHERE id = :id")
    public suspend fun rename(id: String, name: String)

    @Query("DELETE FROM source_groups WHERE id = :id")
    public suspend fun deleteGroup(id: String)

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM source_group_members WHERE groupId = :id")
    public suspend fun nextPosition(id: String): Long

    @Query("SELECT COUNT(*) FROM source_group_members WHERE groupId = :groupId AND sourceId = :sourceId")
    public suspend fun memberCount(groupId: String, sourceId: String): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    public suspend fun insertMember(member: SourceGroupMemberEntity)

    @Query("DELETE FROM source_group_members WHERE groupId = :groupId AND sourceId = :sourceId")
    public suspend fun deleteMember(groupId: String, sourceId: String)
}
