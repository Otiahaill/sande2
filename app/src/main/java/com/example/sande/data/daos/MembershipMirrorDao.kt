package com.example.sande.data.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.sande.data.entities.MembershipMirror
import kotlinx.coroutines.flow.Flow

@Dao
interface MembershipMirrorDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(membership: MembershipMirror)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(memberships: List<MembershipMirror>)

    @Update
    suspend fun update(membership: MembershipMirror)

    @Query("SELECT * FROM membership_mirrors WHERE id = :id")
    suspend fun getById(id: String): MembershipMirror?

    @Query("SELECT * FROM membership_mirrors WHERE businessId = :businessId")
    fun getByBusiness(businessId: String): Flow<List<MembershipMirror>>

    @Query("SELECT * FROM membership_mirrors WHERE businessId = :businessId AND status = 'active'")
    fun getActiveByBusiness(businessId: String): Flow<List<MembershipMirror>>

    @Query("SELECT * FROM membership_mirrors WHERE accountId = :accountId")
    fun getByAccount(accountId: String): Flow<List<MembershipMirror>>

    @Query("SELECT * FROM membership_mirrors WHERE businessId = :businessId AND accountId = :accountId")
    suspend fun getByBusinessAndAccount(businessId: String, accountId: String): MembershipMirror?

    @Query("SELECT * FROM membership_mirrors WHERE deviceCode = :deviceCode")
    suspend fun getByDeviceCode(deviceCode: String): MembershipMirror?

    @Query("SELECT * FROM membership_mirrors WHERE lastSyncedAt < :threshold")
    fun getStale(threshold: Long): Flow<List<MembershipMirror>>
}
