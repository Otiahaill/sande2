package com.example.sande.data.repository

import com.example.sande.data.db.AppDatabase
import com.example.sande.data.daos.ProductPriceHistoryDao
import com.example.sande.data.entities.ProductPriceHistory
import kotlinx.coroutines.flow.Flow

class ProductPriceHistoryRepository constructor(
    private val database: AppDatabase
) {
    private val dao: ProductPriceHistoryDao = database.productPriceHistoryDao()

    suspend fun insert(priceHistory: ProductPriceHistory) = dao.insert(priceHistory)

    suspend fun insertAll(priceHistories: List<ProductPriceHistory>) = dao.insertAll(priceHistories)

    fun getByProduct(productId: String): Flow<List<ProductPriceHistory>> = dao.getByProduct(productId)

    suspend fun getLatestByProduct(productId: String): ProductPriceHistory? = dao.getLatestByProduct(productId)

    fun getByDateRange(startDate: Long, endDate: Long): Flow<List<ProductPriceHistory>> = dao.getByDateRange(startDate, endDate)

    suspend fun countByProduct(productId: String): Int = dao.countByProduct(productId)
}
