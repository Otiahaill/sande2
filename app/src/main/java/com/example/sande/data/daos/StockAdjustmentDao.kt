package com.example.sande.data.daos

import androidx.room.*
import com.example.sande.data.entities.StockAdjustment
import kotlinx.coroutines.flow.Flow

@Dao
interface StockAdjustmentDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(stockAdjustment: StockAdjustment)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(stockAdjustments: List<StockAdjustment>)

    @Query("SELECT * FROM stock_adjustments WHERE productId = :productId ORDER BY createdAt DESC")
    fun getByProduct(productId: String): Flow<List<StockAdjustment>>

    @Query("SELECT * FROM stock_adjustments WHERE businessId = :businessId ORDER BY createdAt DESC")
    fun getByBusiness(businessId: String): Flow<List<StockAdjustment>>

    @Query("SELECT * FROM stock_adjustments WHERE createdAt >= :startDate AND createdAt <= :endDate ORDER BY createdAt DESC")
    fun getByDateRange(startDate: Long, endDate: Long): Flow<List<StockAdjustment>>

    @Query("SELECT * FROM stock_adjustments WHERE createdBy = :createdBy ORDER BY createdAt DESC")
    fun getByUser(createdBy: String): Flow<List<StockAdjustment>>

    @Query("SELECT * FROM stock_adjustments WHERE id = :id")
    suspend fun getById(id: String): StockAdjustment?
}
