package com.example.sande.data.daos

import androidx.room.*
import com.example.sande.data.entities.DraftSale
import kotlinx.coroutines.flow.Flow

@Dao
interface DraftSaleDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(draftSale: DraftSale)

    @Query("SELECT * FROM draft_sales WHERE salespersonId = :salespersonId ORDER BY updatedAt DESC")
    fun getBySalesperson(salespersonId: String): Flow<List<DraftSale>>

    @Query("SELECT * FROM draft_sales WHERE businessId = :businessId ORDER BY updatedAt DESC")
    fun getByBusiness(businessId: String): Flow<List<DraftSale>>

    @Query("SELECT * FROM draft_sales WHERE id = :id")
    suspend fun getById(id: String): DraftSale?

    @Query("DELETE FROM draft_sales WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM draft_sales WHERE salespersonId = :salespersonId")
    suspend fun deleteBySalesperson(salespersonId: String)

    @Query("DELETE FROM draft_sales WHERE updatedAt < :olderThan")
    suspend fun deleteOlderThan(olderThan: Long)
}
