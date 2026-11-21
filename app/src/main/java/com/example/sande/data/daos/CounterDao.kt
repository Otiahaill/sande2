package com.example.sande.data.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.sande.data.entities.Counter
import kotlinx.coroutines.flow.Flow

@Dao
interface CounterDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(counter: Counter)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(counters: List<Counter>)

    @Update
    suspend fun update(counter: Counter)

    @Query("SELECT * FROM counters WHERE id = :id")
    suspend fun getById(id: String): Counter?

    @Query("SELECT * FROM counters WHERE businessId = :businessId AND deviceCode = :deviceCode AND counterType = :counterType")
    suspend fun getByBusinessDeviceAndType(businessId: String, deviceCode: String, counterType: String): Counter?

    @Query("SELECT * FROM counters WHERE businessId = :businessId")
    fun getByBusiness(businessId: String): Flow<List<Counter>>

    @Query("SELECT currentValue FROM counters WHERE businessId = :businessId AND deviceCode = :deviceCode AND counterType = :counterType")
    suspend fun getCurrentValue(businessId: String, deviceCode: String, counterType: String): Long?

    @Query("UPDATE counters SET currentValue = currentValue + 1, updatedAt = :updatedAt WHERE businessId = :businessId AND deviceCode = :deviceCode AND counterType = :counterType")
    suspend fun incrementAndGet(businessId: String, deviceCode: String, counterType: String, updatedAt: Long): Int

    @Query("INSERT OR IGNORE INTO counters (id, businessId, deviceCode, counterType, currentValue, updatedAt) VALUES (:id, :businessId, :deviceCode, :counterType, 0, :updatedAt)")
    suspend fun ensureExists(id: String, businessId: String, deviceCode: String, counterType: String, updatedAt: Long)

    @Transaction
    suspend fun getNextValue(businessId: String, deviceCode: String, counterType: String, updatedAt: Long): Long {
        val id = "$businessId:$deviceCode:$counterType"
        ensureExists(id, businessId, deviceCode, counterType, updatedAt)
        val rowsAffected = incrementAndGet(businessId, deviceCode, counterType, updatedAt)
        return if (rowsAffected > 0) {
            getCurrentValue(businessId, deviceCode, counterType) ?: 1
        } else {
            1
        }
    }
}
