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

    @Query("SELECT * FROM sale_headers WHERE id = :id")
    suspend fun getById(id: String): SaleHeader?

    @Query("SELECT COUNT(*) FROM sale_headers")
    suspend fun countAll(): Int

    @Query("UPDATE sale_headers SET synced = 1 WHERE id IN (:ids)")
    suspend fun markSynced(ids: List<String>)
}
