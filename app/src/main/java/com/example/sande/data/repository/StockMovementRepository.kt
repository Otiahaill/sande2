package com.example.sande.data.repository

import com.example.sande.data.db.AppDatabase
import com.example.sande.data.daos.StockMovementDao
import com.example.sande.data.entities.StockMovement
import kotlinx.coroutines.flow.Flow

class StockMovementRepository constructor(
    private val database: AppDatabase
) {
    private val dao: StockMovementDao = database.stockMovementDao()

    suspend fun insert(stockMovement: StockMovement) = dao.insert(stockMovement)

    suspend fun insertAll(stockMovements: List<StockMovement>) = dao.insertAll(stockMovements)

    suspend fun getById(id: String): StockMovement? = dao.getById(id)

    fun getByProduct(productId: String): Flow<List<StockMovement>> = dao.getByProduct(productId)

    fun getByBusiness(businessId: String): Flow<List<StockMovement>> = dao.getByBusiness(businessId)

    fun getByActor(businessId: String, actorId: String): Flow<List<StockMovement>> = dao.getByActor(businessId, actorId)

    fun getByType(businessId: String, movementType: String): Flow<List<StockMovement>> = dao.getByType(businessId, movementType)

    fun getByReference(businessId: String, referenceId: String): Flow<List<StockMovement>> = dao.getByReference(businessId, referenceId)

    fun getByBusinessAndDateRange(businessId: String, startDate: Long, endDate: Long): Flow<List<StockMovement>> = dao.getByBusinessAndDateRange(businessId, startDate, endDate)

    fun getReversals(businessId: String): Flow<List<StockMovement>> = dao.getReversals(businessId)

    fun getUnsynced(): Flow<List<StockMovement>> = dao.getUnsynced()

    suspend fun markSynced(ids: List<String>) = dao.markSynced(ids)

    suspend fun getStockBalance(productId: String): Long = dao.getStockBalance(productId)

    fun observeBalances(businessId: String): Flow<Map<String, Long>> = dao.observeBalances(businessId)

    suspend fun countByBusiness(businessId: String): Int = dao.countByBusiness(businessId)

    suspend fun countByBusinessAndType(businessId: String, type: String): Int = dao.countByBusinessAndType(businessId, type)
}
