package com.example.sande.data.receipt

import com.example.sande.data.db.AppDatabase
import com.example.sande.data.daos.ReceiptDao
import com.example.sande.data.daos.SaleItemDao
import com.example.sande.data.daos.SaleSessionDao
import com.example.sande.data.daos.BusinessProfileDao
import com.example.sande.data.entities.BusinessProfile
import com.example.sande.data.entities.Receipt
import com.example.sande.data.entities.SaleItem
import com.example.sande.data.entities.SaleSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Repository for loading complete receipt data (session + items + business + receipt).
 */
class ReceiptRepository constructor(
    private val database: AppDatabase
) {
    private val receiptDao: ReceiptDao = database.receiptDao()
    private val sessionDao: SaleSessionDao = database.saleSessionDao()
    private val itemDao: SaleItemDao = database.saleItemDao()
    private val businessDao: BusinessProfileDao = database.businessProfileDao()

    /**
     * Loads complete receipt data by receipt ID.
     */
    suspend fun getReceiptData(receiptId: String): ReceiptData? {
        val receipt = receiptDao.getById(receiptId) ?: return null
        return loadReceiptData(receipt)
    }

    /**
     * Loads complete receipt data by session ID.
     */
    suspend fun getReceiptDataBySession(sessionId: String): ReceiptData? {
        val receipt = receiptDao.getBySession(sessionId) ?: return null
        return loadReceiptData(receipt)
    }

    /**
     * Loads complete receipt data by receipt number.
     */
    suspend fun getReceiptDataByNumber(businessId: String, receiptNumber: String): ReceiptData? {
        val receipt = receiptDao.searchByNumber(businessId, receiptNumber).firstOrNull() ?: return null
        return loadReceiptData(receipt)
    }

    /**
     * Gets all receipts for a business with full data.
     */
    fun getAllReceiptsData(businessId: String): Flow<List<ReceiptData>> {
        return receiptDao.getByBusiness(businessId).map { receipts ->
            receipts.mapNotNull { receipt ->
                loadReceiptData(receipt)
            }
        }
    }

    /**
     * Gets receipts for a business and device.
     */
    fun getReceiptsByBusinessAndDevice(businessId: String, deviceCode: String): Flow<List<ReceiptData>> {
        return receiptDao.getByBusinessAndDevice(businessId, deviceCode).map { receipts ->
            receipts.mapNotNull { receipt ->
                loadReceiptData(receipt)
            }
        }
    }

    /**
     * Searches receipts by number/date.
     */
    fun searchReceipts(businessId: String, query: String): Flow<List<ReceiptData>> {
        return receiptDao.searchByNumber(businessId, "%$query%").map { receipts ->
            receipts.mapNotNull { receipt ->
                loadReceiptData(receipt)
            }
        }
    }

    /**
     * Gets receipts in a date range.
     */
    fun getReceiptsByDateRange(businessId: String, startDate: Long, endDate: Long): Flow<List<ReceiptData>> {
        return receiptDao.getByBusinessAndDateRange(businessId, startDate, endDate).map { receipts ->
            receipts.mapNotNull { receipt ->
                loadReceiptData(receipt)
            }
        }
    }

    private fun loadReceiptData(receipt: Receipt): ReceiptData? {
        val session = sessionDao.getById(receipt.sessionId).firstOrNull() ?: return null
        val items = itemDao.getBySession(receipt.sessionId).first()
        val business = businessDao.getActiveById(receipt.businessId) ?: BusinessProfile(
            id = receipt.businessId,
            name = "Unknown Business",
            category = "retail",
            countryCode = "KE",
            currencyCode = "KES",
            timezone = "Africa/Nairobi",
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        return ReceiptData(session, items, business, receipt)
    }
}
