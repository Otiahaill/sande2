package com.example.sande.data.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.MapColumn
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.sande.data.entities.StockMovement
import kotlinx.coroutines.flow.Flow

@Dao
interface StockMovementDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(stockMovement: StockMovement)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(stockMovements: List<StockMovement>)

    @Update
    suspend fun update(stockMovement: StockMovement)

    @Query("SELECT * FROM stock_movements WHERE id = :id")
    suspend fun getById(id: String): StockMovement?

    @Query("SELECT * FROM stock_movements WHERE productId = :productId ORDER BY createdAt ASC")
    fun getByProduct(productId: String): Flow<List<StockMovement>>

    @Query("SELECT * FROM stock_movements WHERE businessId = :businessId ORDER BY createdAt DESC")
    fun getByBusiness(businessId: String): Flow<List<StockMovement>>

    @Query("SELECT * FROM stock_movements WHERE businessId = :businessId AND actorId = :actorId ORDER BY createdAt DESC")
    fun getByActor(businessId: String, actorId: String): Flow<List<StockMovement>>

    @Query("SELECT * FROM stock_movements WHERE businessId = :businessId AND movementType = :movementType ORDER BY createdAt DESC")
    fun getByType(businessId: String, movementType: String): Flow<List<StockMovement>>

    @Query("SELECT * FROM stock_movements WHERE businessId = :businessId AND referenceId = :referenceId ORDER BY createdAt ASC")
    fun getByReference(businessId: String, referenceId: String): Flow<List<StockMovement>>

    @Query("SELECT * FROM stock_movements WHERE businessId = :businessId AND createdAt BETWEEN :startDate AND :endDate ORDER BY createdAt DESC")
    fun getByBusinessAndDateRange(businessId: String, startDate: Long, endDate: Long): Flow<List<StockMovement>>

    @Query("SELECT * FROM stock_movements WHERE businessId = :businessId AND reversalOf IS NOT NULL ORDER BY createdAt DESC")
    fun getReversals(businessId: String): Flow<List<StockMovement>>

    @Query("SELECT * FROM stock_movements WHERE synced = 0 ORDER BY createdAt ASC")
    fun getUnsynced(): Flow<List<StockMovement>>

    @Query("UPDATE stock_movements SET synced = 1 WHERE id IN (:ids)")
    suspend fun markSynced(ids: List<String>)

    // Balance calculation: sum of quantityDelta for a product
    @Query("SELECT COALESCE(SUM(quantityDelta), 0) FROM stock_movements WHERE productId = :productId")
    suspend fun getStockBalance(productId: String): Long

    // Real-time balance for all products in a business.
    // Room cannot infer which column is the Map key and which is the value,
    // so both type arguments carry @MapColumn naming the source column.
    @Query("SELECT productId, COALESCE(SUM(quantityDelta), 0) AS balance FROM stock_movements WHERE businessId = :businessId GROUP BY productId")
    fun observeBalances(businessId: String): Flow<Map<@MapColumn("productId") String, @MapColumn("balance") Long>>

    @Query("SELECT COUNT(*) FROM stock_movements WHERE businessId = :businessId")
    suspend fun countByBusiness(businessId: String): Int

    @Query("SELECT COUNT(*) FROM stock_movements WHERE businessId = :businessId AND movementType = :type")
    suspend fun countByBusinessAndType(businessId: String, type: String): Int
}
