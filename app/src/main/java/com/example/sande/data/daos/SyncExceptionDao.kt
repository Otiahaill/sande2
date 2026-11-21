package com.example.sande.data.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.sande.data.entities.SyncException
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncExceptionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(exception: SyncException)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(exceptions: List<SyncException>)

    @Update
    suspend fun update(exception: SyncException)

    @Query("SELECT * FROM sync_exceptions WHERE id = :id")
    suspend fun getById(id: String): SyncException?

    @Query("SELECT * FROM sync_exceptions WHERE businessId = :businessId ORDER BY createdAt DESC")
    fun getByBusiness(businessId: String): Flow<List<SyncException>>

    @Query("SELECT * FROM sync_exceptions WHERE businessId = :businessId AND status = 'open' ORDER BY createdAt DESC")
    fun getOpenByBusiness(businessId: String): Flow<List<SyncException>>

    @Query("SELECT * FROM sync_exceptions WHERE businessId = :businessId AND deviceCode = :deviceCode ORDER BY createdAt DESC")
    fun getByBusinessAndDevice(businessId: String, deviceCode: String): Flow<List<SyncException>>

    @Query("SELECT * FROM sync_exceptions WHERE exceptionType = :exceptionType ORDER BY createdAt DESC")
    fun getByType(exceptionType: String): Flow<List<SyncException>>

    @Query("SELECT COUNT(*) FROM sync_exceptions WHERE businessId = :businessId AND status = 'open'")
    suspend fun countOpenByBusiness(businessId: String): Int

    @Query("UPDATE sync_exceptions SET resolution = :resolution, resolvedBy = :resolvedBy, resolvedAt = :resolvedAt, status = 'resolved' WHERE id = :id")
    suspend fun resolve(id: String, resolution: String, resolvedBy: String, resolvedAt: Long)
}
