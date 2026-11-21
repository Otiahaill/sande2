package com.example.sande.data.repository

import com.example.sande.data.db.AppDatabase
import com.example.sande.data.daos.BusinessProfileDao
import com.example.sande.data.entities.BusinessProfile
import kotlinx.coroutines.flow.Flow

class BusinessProfileRepository constructor(
    private val database: AppDatabase
) {
    private val dao: BusinessProfileDao = database.businessProfileDao()

    suspend fun insert(businessProfile: BusinessProfile) = dao.insert(businessProfile)

    suspend fun update(businessProfile: BusinessProfile) = dao.update(businessProfile)

    suspend fun getById(id: String): BusinessProfile? = dao.getById(id)

    suspend fun getActiveById(id: String): BusinessProfile? = dao.getActiveById(id)

    fun getAllActive(): Flow<List<BusinessProfile>> = dao.getAllActive()

    suspend fun softDelete(id: String) = dao.softDelete(id, System.currentTimeMillis(), System.currentTimeMillis())

    suspend fun countActive(): Int = dao.countActive()
}
