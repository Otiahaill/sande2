package com.example.sande.data.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.sande.data.entities.SyncCursor
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncCursorDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(cursor: SyncCursor)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(cursors: List<SyncCursor>)

    @Update
    suspend fun update(cursor: SyncCursor)

    @Query("SELECT * FROM sync_cursors WHERE id = :id")
    suspend fun getById(id: String): SyncCursor?

    @Query("SELECT * FROM sync_cursors WHERE businessId = :businessId")
    fun getByBusiness(businessId: String): Flow<List<SyncCursor>>

    @Query("SELECT * FROM sync_cursors WHERE businessId = :businessId AND deviceCode = :deviceCode")
    suspend fun getByBusinessAndDevice(businessId: String, deviceCode: String): SyncCursor?

    @Query("UPDATE sync_cursors SET cursor = :cursor, lastPullAt = :lastPullAt, pendingPushCount = :pendingPushCount, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateCursor(id: String, cursor: Long, lastPullAt: Long, pendingPushCount: Int, updatedAt: Long)

    @Query("UPDATE sync_cursors SET lastPushAt = :lastPushAt, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateLastPush(id: String, lastPushAt: Long, updatedAt: Long)

    @Query("DELETE FROM sync_cursors WHERE businessId = :businessId")
    suspend fun clearByBusiness(businessId: String): Int
}
