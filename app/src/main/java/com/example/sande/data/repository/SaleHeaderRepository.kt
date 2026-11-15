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

    suspend fun getById(id: String): SaleHeader? = dao.getById(id)

    fun getUnsynced(limit: Int): Flow<List<SaleHeader>> = dao.getUnsynced(limit)

    suspend fun markSynced(ids: List<String>) = dao.markSynced(ids)

    /**
     * Generates a receipt number in the format {prefix}-{deviceShort}-{counter}.
     * counter is the next sequence number based on existing sale headers.
     */
    suspend fun generateReceiptNumber(prefix: String, deviceShort: String): String {
        val count = dao.countAll()
        val counter = count + 1
        return "$prefix-$deviceShort-${String.format("%05d", counter)}"
    }
}
