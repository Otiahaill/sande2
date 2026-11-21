package com.example.sande.data.repository

import com.example.sande.data.db.AppDatabase
import com.example.sande.data.daos.SyncExceptionDao
import com.example.sande.data.entities.SyncException
import kotlinx.coroutines.flow.Flow

class SyncExceptionRepository constructor(
    private val database: AppDatabase
) {
    private val dao: SyncExceptionDao = database.syncExceptionDao()

    suspend fun insert(exception: SyncException) = dao.insert(exception)

    suspend fun insertAll(exceptions: List<SyncException>) = dao.insertAll(exceptions)

    suspend fun update(exception: SyncException) = dao.update(exception)

    suspend fun getById(id: String): SyncException? = dao.getById(id)

    fun getByBusiness(businessId: String): Flow<List<SyncException>> = dao.getByBusiness(businessId)

    fun getOpenByBusiness(businessId: String): Flow<List<SyncException>> = dao.getOpenByBusiness(businessId)

    fun getByBusinessAndDevice(businessId: String, deviceCode: String): Flow<List<SyncException>> = dao.getByBusinessAndDevice(businessId, deviceCode)

    fun getByType(exceptionType: String): Flow<List<SyncException>> = dao.getByType(exceptionType)

    suspend fun countOpenByBusiness(businessId: String): Int = dao.countOpenByBusiness(businessId)

    suspend fun resolve(id: String, resolution: String, resolvedBy: String) = dao.resolve(id, resolution, resolvedBy, System.currentTimeMillis())
}
