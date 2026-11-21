package com.example.sande.data.daos

import androidx.room.*
import com.example.sande.data.entities.Member
import kotlinx.coroutines.flow.Flow

@Dao
interface MemberDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(member: Member)

    @Update
    suspend fun update(member: Member)

    @Query("SELECT * FROM members WHERE uid = :uid")
    suspend fun getByUid(uid: String): Member?

    @Query("SELECT * FROM members WHERE businessId = :businessId")
    fun getByBusiness(businessId: String): Flow<List<Member>>

    @Query("SELECT COUNT(*) FROM members WHERE businessId = :businessId")
    suspend fun countByBusiness(businessId: String): Int

    @Query("SELECT * FROM members WHERE businessId = :businessId")
    suspend fun getAllByBusiness(businessId: String): List<Member>

    @Query("SELECT * FROM members")
    fun getAll(): Flow<List<Member>>

    @Query("DELETE FROM members")
    suspend fun deleteAll()
}
