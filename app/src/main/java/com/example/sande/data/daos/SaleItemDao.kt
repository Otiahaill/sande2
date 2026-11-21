package com.example.sande.data.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.sande.data.entities.SaleItem
import kotlinx.coroutines.flow.Flow

@Dao
interface SaleItemDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(saleItem: SaleItem)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(saleItems: List<SaleItem>)

    @Query("SELECT * FROM sale_items WHERE sessionId = :sessionId")
    fun getBySession(sessionId: String): Flow<List<SaleItem>>

    @Query("SELECT * FROM sale_items WHERE productId = :productId ORDER BY createdAt DESC")
    fun getByProduct(productId: String): Flow<List<SaleItem>>

    @Query("SELECT * FROM sale_items WHERE businessId = :businessId ORDER BY createdAt DESC")
    fun getByBusiness(businessId: String): Flow<List<SaleItem>>

    @Query("SELECT * FROM sale_items WHERE businessId = :businessId AND createdAt BETWEEN :startDate AND :endDate ORDER BY createdAt DESC")
    fun getByBusinessAndDateRange(businessId: String, startDate: Long, endDate: Long): Flow<List<SaleItem>>

    @Query("SELECT COUNT(*) FROM sale_items WHERE sessionId = :sessionId")
    suspend fun countBySession(sessionId: String): Int

    @Query("SELECT SUM(totalCents) FROM sale_items WHERE sessionId = :sessionId")
    suspend fun sumTotalBySession(sessionId: String): Long?

    @Query("SELECT SUM(quantity) FROM sale_items WHERE productId = :productId")
    suspend fun sumQuantityByProduct(productId: String): Long?
}
