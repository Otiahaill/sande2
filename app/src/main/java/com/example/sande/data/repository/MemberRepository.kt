package com.example.sande.data.repository

import com.example.sande.data.db.AppDatabase
import com.example.sande.data.daos.MemberDao
import com.example.sande.data.entities.Member
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


class MemberRepository constructor(
    private val database: AppDatabase
) {
    private val dao: MemberDao = database.memberDao()

    val getAll: Flow<List<Member>> = dao.getAll()

    suspend fun getByUid(uid: String): Member? = dao.getByUid(uid)

    suspend fun insert(member: Member) = dao.insert(member)

    fun getByBusiness(businessId: String): Flow<List<Member>> = dao.getByBusiness(businessId)

    suspend fun upsert(member: Member) = dao.insert(member)
}
