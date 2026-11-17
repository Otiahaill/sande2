package com.example.sande.data.daos

import androidx.room.*
import com.example.sande.data.entities.SaleHeader
import kotlinx.coroutines.flow.Flow

@Dao
interface SaleHeaderDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(saleHeader: SaleHeader)

    @Query("SELECT * FROM sale_headers ORDER BY createdAt DESC")
    fun getAll(): Flow<List<SaleHeader>>

    @Query("SELECT * FROM sale_headers WHERE businessId = :businessId ORDER BY createdAt DESC")
    fun getByBusiness(businessId: String): Flow<List<SaleHeader>>

    @Query("SELECT * FROM sale_headers WHERE businessId = :businessId AND status = :status ORDER BY createdAt DESC")
    fun getByBusinessAndStatus(businessId: String, status: String): Flow<List<SaleHeader>>

    @Query("SELECT * FROM sale_headers WHERE salespersonId = :salespersonId ORDER BY createdAt DESC")
    fun getBySalesperson(salespersonId: String): Flow<List<SaleHeader>>

    @Query("SELECT * FROM sale_headers WHERE businessId = :businessId AND salespersonId = :salespersonId ORDER BY createdAt DESC")
    fun getByBusinessAndSalesperson(businessId: String, salespersonId: String): Flow<List<SaleHeader>>

    @Query("SELECT * FROM sale_headers WHERE id = :id")
    suspend fun getById(id: String): SaleHeader?

    @Query("SELECT COUNT(*) FROM sale_headers")
    suspend fun countAll(): Int

    @Query("SELECT COUNT(*) FROM sale_headers WHERE businessId = :businessId")
    suspend fun countByBusiness(businessId: String): Int

    @Query("SELECT * FROM sale_headers WHERE createdAt >= :startDate AND createdAt <= :endDate ORDER BY createdAt DESC")
    fun getByDateRange(startDate: Long, endDate: Long): Flow<List<SaleHeader>>

    @Query("SELECT * FROM sale_headers WHERE businessId = :businessId AND createdAt >= :startDate AND createdAt <= :endDate ORDER BY createdAt DESC")
    fun getByBusinessAndDateRange(businessId: String, startDate: Long, endDate: Long): Flow<List<SaleHeader>>

    @Query("UPDATE sale_headers SET synced = 1 WHERE id IN (:ids)")
    suspend fun markSynced(ids: List<String>)

    @Query("UPDATE sale_headers SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: String, status: String)
}
