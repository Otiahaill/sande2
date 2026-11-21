package com.example.sande.data.daos

import androidx.room.*
import com.example.sande.data.entities.Product
import kotlinx.coroutines.flow.Flow

@Dao
interface ProductDao {
    @Query("SELECT * FROM products WHERE isActive = 1 AND archivedAt = 0 AND deletedAt = 0 ORDER BY name ASC")
    fun getAllActive(): Flow<List<Product>>

    @Query("SELECT * FROM products WHERE isActive = 1 AND archivedAt = 0 AND deletedAt = 0 AND businessId = :businessId ORDER BY name ASC")
    fun getAllActiveByBusiness(businessId: String): Flow<List<Product>>

    @Query("SELECT * FROM products WHERE id = :id")
    suspend fun getById(id: String): Product?

    @Query("SELECT * FROM products WHERE id = :id AND deletedAt = 0")
    suspend fun getByIdNotDeleted(id: String): Product?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(product: Product)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(products: List<Product>)

    @Update
    suspend fun update(product: Product)

    @Query("UPDATE products SET archivedAt = :archivedAt, updatedAt = :updatedAt WHERE id = :id")
    suspend fun archive(id: String, archivedAt: Long, updatedAt: Long)

    @Query("UPDATE products SET deletedAt = :deletedAt, updatedAt = :updatedAt WHERE id = :id")
    suspend fun softDelete(id: String, deletedAt: Long, updatedAt: Long)

    @Query("UPDATE products SET isActive = :isActive, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setActive(id: String, isActive: Boolean, updatedAt: Long)

    @Query("SELECT * FROM products WHERE categoryId = :categoryId AND isActive = 1 AND archivedAt = 0 AND deletedAt = 0 ORDER BY name ASC")
    fun getByCategory(categoryId: String): Flow<List<Product>>

    @Query("SELECT * FROM products WHERE businessId = :businessId AND (name LIKE :query OR sku LIKE :query) AND isActive = 1 AND archivedAt = 0 AND deletedAt = 0 ORDER BY name ASC")
    fun searchProducts(businessId: String, query: String): Flow<List<Product>>

    /** Count of every product owned by a business, including archived and soft-deleted rows. */
    @Query("SELECT COUNT(*) FROM products WHERE businessId = :businessId")
    suspend fun countByBusiness(businessId: String): Int

    /** Every product owned by a business, including archived and soft-deleted rows. */
    @Query("SELECT * FROM products WHERE businessId = :businessId")
    suspend fun getAllByBusiness(businessId: String): List<Product>

    @Query("SELECT * FROM products WHERE archivedAt > 0 AND deletedAt = 0 ORDER BY archivedAt DESC")
    fun getArchived(): Flow<List<Product>>

    @Query("SELECT * FROM products WHERE deletedAt > 0 ORDER BY deletedAt DESC")
    fun getDeleted(): Flow<List<Product>>
}
