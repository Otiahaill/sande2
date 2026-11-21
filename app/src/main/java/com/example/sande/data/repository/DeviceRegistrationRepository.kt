package com.example.sande.data.repository

import com.example.sande.data.db.AppDatabase
import com.example.sande.data.daos.DeviceRegistrationDao
import com.example.sande.data.entities.DeviceRegistration
import kotlinx.coroutines.flow.Flow

class DeviceRegistrationRepository constructor(
    private val database: AppDatabase
) {
    private val dao: DeviceRegistrationDao = database.deviceRegistrationDao()

    suspend fun insert(device: DeviceRegistration) = dao.insert(device)

    suspend fun insertAll(devices: List<DeviceRegistration>) = dao.insertAll(devices)

    suspend fun update(device: DeviceRegistration) = dao.update(device)

    suspend fun getById(id: String): DeviceRegistration? = dao.getById(id)

    suspend fun getByDeviceCode(deviceCode: String): DeviceRegistration? = dao.getByDeviceCode(deviceCode)

    fun getByBusiness(businessId: String): Flow<List<DeviceRegistration>> = dao.getByBusiness(businessId)

    fun getActiveByBusiness(businessId: String): Flow<List<DeviceRegistration>> = dao.getActiveByBusiness(businessId)

    fun getByAccount(accountId: String): Flow<List<DeviceRegistration>> = dao.getByAccount(accountId)

    suspend fun getActiveByBusinessAndAccount(businessId: String, accountId: String): DeviceRegistration? = dao.getActiveByBusinessAndAccount(businessId, accountId)

    suspend fun countActiveByBusiness(businessId: String): Int = dao.countActiveByBusiness(businessId)

    suspend fun countActiveByAccount(accountId: String): Int = dao.countActiveByAccount(accountId)
}
