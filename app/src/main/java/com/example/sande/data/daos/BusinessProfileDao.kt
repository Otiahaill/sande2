package com.example.sande.data.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.sande.data.entities.BusinessProfile
import kotlinx.coroutines.flow.Flow

@Dao
interface BusinessProfileDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(businessProfile: BusinessProfile)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(businessProfiles: List<BusinessProfile>)

    @Update
    suspend fun update(businessProfile: BusinessProfile)

    @Query("SELECT * FROM business_profiles WHERE id = :id")
    suspend fun getById(id: String): BusinessProfile?

    @Query("SELECT * FROM business_profiles WHERE deletedAt = 0 ORDER BY createdAt DESC")
    fun getAllActive(): Flow<List<BusinessProfile>>

    @Query("SELECT * FROM business_profiles WHERE id = :id AND deletedAt = 0")
    suspend fun getActiveById(id: String): BusinessProfile?

    @Query("UPDATE business_profiles SET deletedAt = :deletedAt, updatedAt = :updatedAt WHERE id = :id")
    suspend fun softDelete(id: String, deletedAt: Long, updatedAt: Long)

    @Query("SELECT COUNT(*) FROM business_profiles WHERE deletedAt = 0")
    suspend fun countActive(): Int
}
