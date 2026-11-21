package com.example.sande.data.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.sande.data.entities.DeviceRegistration
import kotlinx.coroutines.flow.Flow

@Dao
interface DeviceRegistrationDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(device: DeviceRegistration)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(devices: List<DeviceRegistration>)

    @Update
    suspend fun update(device: DeviceRegistration)

    @Query("SELECT * FROM device_registrations WHERE id = :id")
    suspend fun getById(id: String): DeviceRegistration?

    @Query("SELECT * FROM device_registrations WHERE deviceCode = :deviceCode")
    suspend fun getByDeviceCode(deviceCode: String): DeviceRegistration?

    @Query("SELECT * FROM device_registrations WHERE businessId = :businessId")
    fun getByBusiness(businessId: String): Flow<List<DeviceRegistration>>

    @Query("SELECT * FROM device_registrations WHERE businessId = :businessId AND status = 'active'")
    fun getActiveByBusiness(businessId: String): Flow<List<DeviceRegistration>>

    @Query("SELECT * FROM device_registrations WHERE accountId = :accountId")
    fun getByAccount(accountId: String): Flow<List<DeviceRegistration>>

    @Query("SELECT * FROM device_registrations WHERE businessId = :businessId AND accountId = :accountId AND status = 'active'")
    suspend fun getActiveByBusinessAndAccount(businessId: String, accountId: String): DeviceRegistration?

    @Query("SELECT COUNT(*) FROM device_registrations WHERE businessId = :businessId AND status = 'active'")
    suspend fun countActiveByBusiness(businessId: String): Int

    @Query("SELECT COUNT(*) FROM device_registrations WHERE accountId = :accountId AND status = 'active'")
    suspend fun countActiveByAccount(accountId: String): Int
}
