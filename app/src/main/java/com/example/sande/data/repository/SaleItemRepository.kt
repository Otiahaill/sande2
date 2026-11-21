package com.example.sande.data.repository

import com.example.sande.data.db.AppDatabase
import com.example.sande.data.daos.SaleItemDao
import com.example.sande.data.entities.SaleItem
import kotlinx.coroutines.flow.Flow

class SaleItemRepository constructor(
    private val database: AppDatabase
) {
    private val dao: SaleItemDao = database.saleItemDao()

    suspend fun insert(saleItem: SaleItem) = dao.insert(saleItem)

    suspend fun insertAll(saleItems: List<SaleItem>) = dao.insertAll(saleItems)

    fun getBySession(sessionId: String): Flow<List<SaleItem>> = dao.getBySession(sessionId)

    fun getByProduct(productId: String): Flow<List<SaleItem>> = dao.getByProduct(productId)

    fun getByBusiness(businessId: String): Flow<List<SaleItem>> = dao.getByBusiness(businessId)

    fun getByBusinessAndDateRange(businessId: String, startDate: Long, endDate: Long): Flow<List<SaleItem>> = dao.getByBusinessAndDateRange(businessId, startDate, endDate)

    suspend fun countBySession(sessionId: String): Int = dao.countBySession(sessionId)

    suspend fun sumTotalBySession(sessionId: String): Long = dao.sumTotalBySession(sessionId) ?: 0L

    suspend fun sumQuantityByProduct(productId: String): Long = dao.sumQuantityByProduct(productId) ?: 0L
}
