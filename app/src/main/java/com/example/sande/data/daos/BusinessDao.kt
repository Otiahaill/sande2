package com.example.sande.data.daos

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.sande.data.entities.Business

/**
 * DAO for the legacy [Business] row.
 *
 * The `businesses` table is a minimal placeholder kept for backwards compatibility;
 * the authoritative record is `business_profiles` (see [BusinessProfileDao]).
 */
@Dao
interface BusinessDao {

    @Query("SELECT * FROM businesses WHERE id = :id")
    suspend fun getById(id: String): Business?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(business: Business)

    @Delete
    suspend fun delete(business: Business)

    @Query("SELECT COUNT(*) FROM businesses")
    suspend fun countAll(): Int
}
