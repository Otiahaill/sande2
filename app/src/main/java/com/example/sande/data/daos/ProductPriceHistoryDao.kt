package com.example.sande.data.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.sande.data.entities.ProductPriceHistory
import kotlinx.coroutines.flow.Flow

@Dao
interface ProductPriceHistoryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(priceHistory: ProductPriceHistory)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(priceHistories: List<ProductPriceHistory>)

    @Query("SELECT * FROM product_price_history WHERE id = :id")
    suspend fun getById(id: String): ProductPriceHistory?

    @Query("SELECT * FROM product_price_history WHERE productId = :productId ORDER BY createdAt DESC")
    fun getByProduct(productId: String): Flow<List<ProductPriceHistory>>

    @Query("SELECT * FROM product_price_history WHERE productId = :productId ORDER BY createdAt DESC LIMIT 1")
    suspend fun getLatestByProduct(productId: String): ProductPriceHistory?

    @Query("SELECT * FROM product_price_history WHERE createdAt BETWEEN :startDate AND :endDate ORDER BY createdAt DESC")
    fun getByDateRange(startDate: Long, endDate: Long): Flow<List<ProductPriceHistory>>

    @Query("SELECT COUNT(*) FROM product_price_history WHERE productId = :productId")
    suspend fun countByProduct(productId: String): Int
}
