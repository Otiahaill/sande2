package com.example.sande.data.repository

import com.example.sande.data.db.AppDatabase
import com.example.sande.data.daos.SaleHeaderDao
import com.example.sande.data.entities.SaleHeader
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class SaleHeaderRepository constructor(
    private val database: AppDatabase
) {
    private val dao: SaleHeaderDao = database.saleHeaderDao()

    suspend fun insert(saleHeader: SaleHeader): String {
        dao.insert(saleHeader)
        return saleHeader.id
    }

    fun getAll(): Flow<List<SaleHeader>> = dao.getAll()

    fun getByBusiness(businessId: String): Flow<List<SaleHeader>> = dao.getByBusiness(businessId)

    fun getByBusinessAndStatus(businessId: String, status: String): Flow<List<SaleHeader>> = dao.getByBusinessAndStatus(businessId, status)

    fun getBySalesperson(salespersonId: String): Flow<List<SaleHeader>> = dao.getBySalesperson(salespersonId)

    fun getByBusinessAndSalesperson(businessId: String, salespersonId: String): Flow<List<SaleHeader>> = dao.getByBusinessAndSalesperson(businessId, salespersonId)

    suspend fun getById(id: String): SaleHeader? = dao.getById(id)

    suspend fun countAll(): Int = dao.countAll()

    suspend fun countByBusiness(businessId: String): Int = dao.countByBusiness(businessId)

    fun getByDateRange(startDate: Long, endDate: Long): Flow<List<SaleHeader>> = dao.getByDateRange(startDate, endDate)

    fun getByBusinessAndDateRange(businessId: String, startDate: Long, endDate: Long): Flow<List<SaleHeader>> = dao.getByBusinessAndDateRange(businessId, startDate, endDate)

    suspend fun markSynced(ids: List<String>) = dao.markSynced(ids)

    suspend fun updateStatus(id: String, status: String) = dao.updateStatus(id, status)

    /**
     * Generates a receipt number in the format {prefix}-{deviceShort}-{counter}.
     * counter is the next sequence number based on existing sale headers for the business.
     */
    suspend fun generateReceiptNumber(prefix: String, deviceShort: String, businessId: String): String {
        val count = dao.countByBusiness(businessId)
        val counter = count + 1
        return "$prefix-$deviceShort-${String.format("%05d", counter)}"
    }
}
