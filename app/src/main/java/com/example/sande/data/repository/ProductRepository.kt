package com.example.sande.data.repository

import com.example.sande.data.db.AppDatabase
import com.example.sande.data.daos.ProductDao
import com.example.sande.data.entities.Product
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


class ProductRepository constructor(
    private val database: AppDatabase
) {
    private val dao: ProductDao = database.productDao()

    val getAllActive: Flow<List<Product>> = dao.getAllActive()

    suspend fun insert(product: Product) = dao.insert(product)

    suspend fun insertAll(products: List<Product>) = dao.insertAll(products)

    suspend fun getById(id: String): Product? = dao.getById(id)

    suspend fun update(product: Product) = dao.update(product)

    fun getByCategory(category: String): Flow<List<Product>> = dao.getByCategory(category)

    suspend fun seedSampleData() {
        val existing = dao.getById("prod-sample-1")
        if (existing != null) return
        insertAll(listOf(
            Product(id = "prod-sample-1", businessId = "seed", name = "Maize Flour 2kg", sku = "MF-001", categoryId = null, category = "Grains", priceCents = 22000, unitOfMeasure = null, conversionRatio = null, isActive = true),
            Product(id = "prod-sample-2", businessId = "seed", name = "Rice 5kg", sku = "RC-002", categoryId = null, category = "Grains", priceCents = 65000, unitOfMeasure = null, conversionRatio = null, isActive = true),
            Product(id = "prod-sample-3", businessId = "seed", name = "Cooking Oil 2L", sku = "OIL-003", categoryId = null, category = "Oils", priceCents = 85000, unitOfMeasure = null, conversionRatio = null, isActive = true),
            Product(id = "prod-sample-4", businessId = "seed", name = "Sugar 2kg", sku = "SUG-004", categoryId = null, category = "Staples", priceCents = 32000, unitOfMeasure = null, conversionRatio = null, isActive = true),
            Product(id = "prod-sample-5", businessId = "seed", name = "Salt 1kg", sku = "SAL-005", categoryId = null, category = "Staples", priceCents = 15000, unitOfMeasure = null, conversionRatio = null, isActive = true)
        ))
    }
}
