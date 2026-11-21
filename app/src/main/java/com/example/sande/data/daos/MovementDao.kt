package com.example.sande.data.daos

import androidx.room.*
import com.example.sande.data.entities.Movement
import com.example.sande.data.entities.MovementType
import kotlinx.coroutines.flow.Flow

@Dao
interface MovementDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(movement: Movement)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(movements: List<Movement>)

    @Query("SELECT * FROM movements WHERE productId = :productId ORDER BY createdAt DESC")
    fun getByProduct(productId: String): Flow<List<Movement>>

    @Query("SELECT * FROM movements WHERE businessId = :businessId ORDER BY createdAt DESC")
    fun getByBusiness(businessId: String): Flow<List<Movement>>

    @Query("SELECT COUNT(*) FROM movements WHERE businessId = :businessId")
    suspend fun countByBusiness(businessId: String): Int

    @Query("SELECT * FROM movements WHERE businessId = :businessId")
    suspend fun getAllByBusiness(businessId: String): List<Movement>

    @Query("SELECT * FROM movements ORDER BY createdAt DESC")
    fun getAll(): Flow<List<Movement>>

    @Query("""
        SELECT COALESCE(SUM(CASE type
            WHEN 'RECEIVE' THEN quantity
            WHEN 'SALE' THEN -quantity
            WHEN 'ADJUST' THEN quantity
            WHEN 'VOID' THEN quantity
            ELSE 0
        END), 0) as balance
        FROM movements
        WHERE productId = :productId
    """)
    suspend fun getStockBalance(productId: String): Long

    @Query("SELECT * FROM movements WHERE synced = 0 ORDER BY createdAt ASC")
    fun getUnsynced(): Flow<List<Movement>>

    @Update
    suspend fun update(movement: Movement)

    @Query("UPDATE movements SET synced = 1 WHERE id IN (:ids)")
    suspend fun markSynced(ids: List<String>)
}
