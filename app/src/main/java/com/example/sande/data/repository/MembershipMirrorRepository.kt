package com.example.sande.data.repository

import com.example.sande.data.db.AppDatabase
import com.example.sande.data.daos.MembershipMirrorDao
import com.example.sande.data.entities.MembershipMirror
import kotlinx.coroutines.flow.Flow

class MembershipMirrorRepository constructor(
    private val database: AppDatabase
) {
    private val dao: MembershipMirrorDao = database.membershipMirrorDao()

    suspend fun insert(membership: MembershipMirror) = dao.insert(membership)

    suspend fun insertAll(memberships: List<MembershipMirror>) = dao.insertAll(memberships)

    suspend fun update(membership: MembershipMirror) = dao.update(membership)

    suspend fun getById(id: String): MembershipMirror? = dao.getById(id)

    fun getByBusiness(businessId: String): Flow<List<MembershipMirror>> = dao.getByBusiness(businessId)

    fun getActiveByBusiness(businessId: String): Flow<List<MembershipMirror>> = dao.getActiveByBusiness(businessId)

    fun getByAccount(accountId: String): Flow<List<MembershipMirror>> = dao.getByAccount(accountId)

    suspend fun getByBusinessAndAccount(businessId: String, accountId: String): MembershipMirror? = dao.getByBusinessAndAccount(businessId, accountId)

    suspend fun getByDeviceCode(deviceCode: String): MembershipMirror? = dao.getByDeviceCode(deviceCode)

    fun getStale(threshold: Long): Flow<List<MembershipMirror>> = dao.getStale(threshold)
}
