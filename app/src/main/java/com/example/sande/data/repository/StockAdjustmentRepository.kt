package com.example.sande.data.repository

import com.example.sande.data.db.AppDatabase
import com.example.sande.data.daos.StockAdjustmentDao
import com.example.sande.data.entities.StockAdjustment
import kotlinx.coroutines.flow.Flow

class StockAdjustmentRepository constructor(
    private val database: AppDatabase
) {
    private val dao: StockAdjustmentDao = database.stockAdjustmentDao()

    suspend fun insert(stockAdjustment: StockAdjustment) = dao.insert(stockAdjustment)

    suspend fun insertAll(stockAdjustments: List<StockAdjustment>) = dao.insertAll(stockAdjustments)

    fun getByProduct(productId: String): Flow<List<StockAdjustment>> = dao.getByProduct(productId)

    fun getByBusiness(businessId: String): Flow<List<StockAdjustment>> = dao.getByBusiness(businessId)

    fun getByDateRange(startDate: Long, endDate: Long): Flow<List<StockAdjustment>> = dao.getByDateRange(startDate, endDate)

    fun getByUser(createdBy: String): Flow<List<StockAdjustment>> = dao.getByUser(createdBy)

    suspend fun getById(id: String): StockAdjustment? = dao.getById(id)
}
