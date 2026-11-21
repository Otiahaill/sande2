package com.example.sande.data.repository

import com.example.sande.data.db.AppDatabase
import com.example.sande.data.daos.SaleSessionDao
import com.example.sande.data.entities.SaleSession
import kotlinx.coroutines.flow.Flow

class SaleSessionRepository constructor(
    private val database: AppDatabase
) {
    private val dao: SaleSessionDao = database.saleSessionDao()

    suspend fun insert(saleSession: SaleSession) = dao.insert(saleSession)

    suspend fun insertAll(saleSessions: List<SaleSession>) = dao.insertAll(saleSessions)

    suspend fun update(saleSession: SaleSession) = dao.update(saleSession)

    fun getById(id: String): Flow<SaleSession?> = dao.getById(id).distinctUntilChanged()

    fun getByBusiness(businessId: String): Flow<List<SaleSession>> = dao.getByBusiness(businessId)

    fun getByBusinessAndStatus(businessId: String, status: String): Flow<List<SaleSession>> = dao.getByBusinessAndStatus(businessId, status)

    fun getBySalesperson(salespersonId: String): Flow<List<SaleSession>> = dao.getBySalesperson(salespersonId)

    fun getByBusinessAndSalesperson(businessId: String, salespersonId: String): Flow<List<SaleSession>> = dao.getByBusinessAndSalesperson(businessId, salespersonId)

    fun getByDevice(deviceId: String): Flow<List<SaleSession>> = dao.getByDevice(deviceId)

    fun getByBusinessAndDateRange(businessId: String, startDate: Long, endDate: Long): Flow<List<SaleSession>> = dao.getByBusinessAndDateRange(businessId, startDate, endDate)

    fun getUnsynced(): Flow<List<SaleSession>> = dao.getUnsynced()

    suspend fun countByBusiness(businessId: String): Int = dao.countByBusiness(businessId)

    suspend fun countCompletedByBusiness(businessId: String): Int = dao.countCompletedByBusiness(businessId)

    suspend fun sumTotalByBusiness(businessId: String): Long = dao.sumTotalByBusiness(businessId) ?: 0L

    suspend fun sumTotalByBusinessAndDateRange(businessId: String, startDate: Long, endDate: Long): Long = dao.sumTotalByBusinessAndDateRange(businessId, startDate, endDate) ?: 0L

    suspend fun markSynced(ids: List<String>) = dao.markSynced(ids, System.currentTimeMillis())

    suspend fun updateStatus(id: String, status: String) = dao.updateStatus(id, status, System.currentTimeMillis())
}
