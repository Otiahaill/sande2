package com.example.sande.data.daos

import androidx.room.*
import com.example.sande.data.entities.ProductCategory
import kotlinx.coroutines.flow.Flow

@Dao
interface ProductCategoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(category: ProductCategory)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(categories: List<ProductCategory>)

    @Query("SELECT * FROM product_categories WHERE businessId = :businessId AND isActive = 1 ORDER BY name ASC")
    fun getByBusiness(businessId: String): Flow<List<ProductCategory>>

    @Query("SELECT * FROM product_categories WHERE id = :id")
    suspend fun getById(id: String): ProductCategory?

    @Update
    suspend fun update(category: ProductCategory)

    @Delete
    suspend fun delete(category: ProductCategory)
}
