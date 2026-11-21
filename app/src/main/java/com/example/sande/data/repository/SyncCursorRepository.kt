package com.example.sande.data.repository

import com.example.sande.data.db.AppDatabase
import com.example.sande.data.daos.SyncCursorDao
import com.example.sande.data.entities.SyncCursor
import kotlinx.coroutines.flow.Flow

class SyncCursorRepository constructor(
    private val database: AppDatabase
) {
    private val dao: SyncCursorDao = database.syncCursorDao()

    suspend fun insert(cursor: SyncCursor) = dao.insert(cursor)

    suspend fun insertAll(cursors: List<SyncCursor>) = dao.insertAll(cursors)

    suspend fun update(cursor: SyncCursor) = dao.update(cursor)

    suspend fun getById(id: String): SyncCursor? = dao.getById(id)

    fun getByBusiness(businessId: String): Flow<List<SyncCursor>> = dao.getByBusiness(businessId)

    suspend fun getByBusinessAndDevice(businessId: String, deviceCode: String): SyncCursor? = dao.getByBusinessAndDevice(businessId, deviceCode)

    suspend fun updateCursor(id: String, cursor: Long, lastPullAt: Long, pendingPushCount: Int) = dao.updateCursor(id, cursor, lastPullAt, pendingPushCount, System.currentTimeMillis())

    suspend fun updateLastPush(id: String, lastPushAt: Long) = dao.updateLastPush(id, lastPushAt, System.currentTimeMillis())

    suspend fun clearByBusiness(businessId: String): Int = dao.clearByBusiness(businessId)
}
