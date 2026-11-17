package com.example.sande.data.repository

import com.example.sande.data.db.AppDatabase
import com.example.sande.data.daos.DraftSaleDao
import com.example.sande.data.entities.DraftSale
import kotlinx.coroutines.flow.Flow

class DraftSaleRepository constructor(
    private val database: AppDatabase
) {
    private val dao: DraftSaleDao = database.draftSaleDao()

    suspend fun insert(draftSale: DraftSale) = dao.insert(draftSale)

    fun getBySalesperson(salespersonId: String): Flow<List<DraftSale>> = dao.getBySalesperson(salespersonId)

    fun getByBusiness(businessId: String): Flow<List<DraftSale>> = dao.getByBusiness(businessId)

    suspend fun getById(id: String): DraftSale? = dao.getById(id)

    suspend fun delete(id: String) = dao.delete(id)

    suspend fun deleteBySalesperson(salespersonId: String) = dao.deleteBySalesperson(salespersonId)

    suspend fun deleteOlderThan(olderThan: Long) = dao.deleteOlderThan(olderThan)
}
