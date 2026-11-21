package com.example.sande.data.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.sande.data.entities.Receipt
import kotlinx.coroutines.flow.Flow

@Dao
interface ReceiptDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(receipt: Receipt)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(receipts: List<Receipt>)

    @Query("SELECT * FROM receipts WHERE id = :id")
    suspend fun getById(id: String): Receipt?

    @Query("SELECT * FROM receipts WHERE sessionId = :sessionId")
    suspend fun getBySession(sessionId: String): Receipt?

    @Query("SELECT * FROM receipts WHERE businessId = :businessId ORDER BY createdAt DESC")
    fun getByBusiness(businessId: String): Flow<List<Receipt>>

    @Query("SELECT * FROM receipts WHERE businessId = :businessId AND deviceCode = :deviceCode ORDER BY createdAt DESC")
    fun getByBusinessAndDevice(businessId: String, deviceCode: String): Flow<List<Receipt>>

    @Query("SELECT * FROM receipts WHERE businessId = :businessId AND receiptNumber LIKE :search ORDER BY createdAt DESC")
    fun searchByNumber(businessId: String, search: String): Flow<List<Receipt>>

    @Query("SELECT * FROM receipts WHERE businessId = :businessId AND createdAt BETWEEN :startDate AND :endDate ORDER BY createdAt DESC")
    fun getByBusinessAndDateRange(businessId: String, startDate: Long, endDate: Long): Flow<List<Receipt>>

    @Query("SELECT MAX(counter) FROM counters WHERE businessId = :businessId AND deviceCode = :deviceCode AND counterType = 'receipt'")
    suspend fun getMaxReceiptCounter(businessId: String, deviceCode: String): Long?

    @Query("SELECT COUNT(*) FROM receipts WHERE businessId = :businessId")
    suspend fun countByBusiness(businessId: String): Int
}
