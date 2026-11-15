package com.example.sande.data.repository

import com.example.sande.data.db.AppDatabase
import com.example.sande.data.daos.ProductCategoryDao
import com.example.sande.data.entities.ProductCategory
import kotlinx.coroutines.flow.Flow

class ProductCategoryRepository(
    private val database: AppDatabase
) {
    private val dao: ProductCategoryDao = database.productCategoryDao()

    suspend fun insert(category: ProductCategory): String {
        dao.insert(category)
        return category.id
    }

    suspend fun insertAll(categories: List<ProductCategory>) = dao.insertAll(categories)

    suspend fun getById(id: String): ProductCategory? = dao.getById(id)

    fun getByBusiness(businessId: String): Flow<List<ProductCategory>> = dao.getByBusiness(businessId)

    suspend fun update(category: ProductCategory) = dao.update(category)

    suspend fun delete(category: ProductCategory) = dao.delete(category)
}
