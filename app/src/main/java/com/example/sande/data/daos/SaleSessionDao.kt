package com.example.sande.data.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.sande.data.entities.SaleSession
import kotlinx.coroutines.flow.Flow

@Dao
interface SaleSessionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(saleSession: SaleSession)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(saleSessions: List<SaleSession>)

    @Update
    suspend fun update(saleSession: SaleSession)

    @Update
    suspend fun updateAll(saleSessions: List<SaleSession>)

    @Query("SELECT * FROM sale_sessions WHERE id = :id")
    suspend fun getById(id: String): SaleSession?

    @Query("SELECT * FROM sale_sessions WHERE businessId = :businessId ORDER BY createdAt DESC")
    fun getByBusiness(businessId: String): Flow<List<SaleSession>>

    @Query("SELECT * FROM sale_sessions WHERE businessId = :businessId AND status = :status ORDER BY createdAt DESC")
    fun getByBusinessAndStatus(businessId: String, status: String): Flow<List<SaleSession>>

    @Query("SELECT * FROM sale_sessions WHERE salespersonId = :salespersonId ORDER BY createdAt DESC")
    fun getBySalesperson(salespersonId: String): Flow<List<SaleSession>>

    @Query("SELECT * FROM sale_sessions WHERE businessId = :businessId AND salespersonId = :salespersonId ORDER BY createdAt DESC")
    fun getByBusinessAndSalesperson(businessId: String, salespersonId: String): Flow<List<SaleSession>>

    @Query("SELECT * FROM sale_sessions WHERE deviceId = :deviceId ORDER BY createdAt DESC")
    fun getByDevice(deviceId: String): Flow<List<SaleSession>>

    @Query("SELECT * FROM sale_sessions WHERE businessId = :businessId AND createdAt BETWEEN :startDate AND :endDate ORDER BY createdAt DESC")
    fun getByBusinessAndDateRange(businessId: String, startDate: Long, endDate: Long): Flow<List<SaleSession>>

    @Query("SELECT * FROM sale_sessions WHERE synced = 0 ORDER BY createdAt ASC")
    fun getUnsynced(): Flow<List<SaleSession>>

    @Query("SELECT COUNT(*) FROM sale_sessions WHERE businessId = :businessId")
    suspend fun countByBusiness(businessId: String): Int

    @Query("SELECT COUNT(*) FROM sale_sessions WHERE businessId = :businessId AND status = 'completed'")
    suspend fun countCompletedByBusiness(businessId: String): Int

    @Query("SELECT SUM(totalCents) FROM sale_sessions WHERE businessId = :businessId AND status = 'completed'")
    suspend fun sumTotalByBusiness(businessId: String): Long?

    @Query("SELECT SUM(totalCents) FROM sale_sessions WHERE businessId = :businessId AND status = 'completed' AND createdAt BETWEEN :startDate AND :endDate")
    suspend fun sumTotalByBusinessAndDateRange(businessId: String, startDate: Long, endDate: Long): Long?

    @Query("UPDATE sale_sessions SET synced = 1, updatedAt = :updatedAt WHERE id IN (:ids)")
    suspend fun markSynced(ids: List<String>, updatedAt: Long)

    @Query("UPDATE sale_sessions SET status = :status, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateStatus(id: String, status: String, updatedAt: Long)
}
