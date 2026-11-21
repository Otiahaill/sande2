package com.example.sande.data.repository

import com.example.sande.data.db.AppDatabase
import com.example.sande.data.daos.CounterDao
import com.example.sande.data.entities.Counter
import kotlinx.coroutines.flow.Flow

class CounterRepository constructor(
    private val database: AppDatabase
) {
    private val dao: CounterDao = database.counterDao()

    suspend fun insert(counter: Counter) = dao.insert(counter)

    suspend fun insertAll(counters: List<Counter>) = dao.insertAll(counters)

    suspend fun getById(id: String): Counter? = dao.getById(id)

    suspend fun getByBusinessDeviceAndType(businessId: String, deviceCode: String, counterType: String): Counter? = dao.getByBusinessDeviceAndType(businessId, deviceCode, counterType)

    fun getByBusiness(businessId: String): Flow<List<Counter>> = dao.getByBusiness(businessId)

    // Atomically get and increment the counter, returning the new value
    suspend fun getNextValue(businessId: String, deviceCode: String, counterType: String): Long {
        return dao.getNextValue(businessId, deviceCode, counterType, System.currentTimeMillis())
    }

    suspend fun getCurrentValue(businessId: String, deviceCode: String, counterType: String): Long = dao.getCurrentValue(businessId, deviceCode, counterType) ?: 0L
}
