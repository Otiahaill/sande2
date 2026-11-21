package com.example.sande.data.repository

import com.example.sande.data.db.AppDatabase
import com.example.sande.data.daos.ReceiptDao
import com.example.sande.data.entities.Receipt
import kotlinx.coroutines.flow.Flow

class ReceiptRepository constructor(
    private val database: AppDatabase
) {
    private val dao: ReceiptDao = database.receiptDao()

    suspend fun insert(receipt: Receipt) = dao.insert(receipt)

    suspend fun insertAll(receipts: List<Receipt>) = dao.insertAll(receipts)

    suspend fun getById(id: String): Receipt? = dao.getById(id)

    suspend fun getBySession(sessionId: String): Receipt? = dao.getBySession(sessionId)

    fun getByBusiness(businessId: String): Flow<List<Receipt>> = dao.getByBusiness(businessId)

    fun getByBusinessAndDevice(businessId: String, deviceCode: String): Flow<List<Receipt>> = dao.getByBusinessAndDevice(businessId, deviceCode)

    fun searchByNumber(businessId: String, search: String): Flow<List<Receipt>> = dao.searchByNumber(businessId, "%$search%")

    fun getByBusinessAndDateRange(businessId: String, startDate: Long, endDate: Long): Flow<List<Receipt>> = dao.getByBusinessAndDateRange(businessId, startDate, endDate)

    suspend fun getMaxReceiptCounter(businessId: String, deviceCode: String): Long = dao.getMaxReceiptCounter(businessId, deviceCode) ?: 0L

    suspend fun countByBusiness(businessId: String): Int = dao.countByBusiness(businessId)
}
