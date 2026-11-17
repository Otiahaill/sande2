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

    val getAllActiveByBusiness: (String) -> Flow<List<Product>> = { businessId ->
        dao.getAllActiveByBusiness(businessId)
    }

    suspend fun insert(product: Product) = dao.insert(product)

    suspend fun insertAll(products: List<Product>) = dao.insertAll(products)

    suspend fun getById(id: String): Product? = dao.getById(id)

    suspend fun getByIdNotDeleted(id: String): Product? = dao.getByIdNotDeleted(id)

    suspend fun update(product: Product) = dao.update(product)

    suspend fun archive(id: String) {
        val now = System.currentTimeMillis()
        dao.archive(id, now, now)
    }

    suspend fun softDelete(id: String) {
        val now = System.currentTimeMillis()
        dao.softDelete(id, now, now)
    }

    suspend fun setActive(id: String, isActive: Boolean) {
        val now = System.currentTimeMillis()
        dao.setActive(id, isActive, now)
    }

    fun getByCategory(category: String): Flow<List<Product>> = dao.getByCategory(category)

    suspend fun searchProducts(businessId: String, query: String): Flow<List<Product>> = dao.searchProducts(businessId, "%$query%")

    fun getArchived(): Flow<List<Product>> = dao.getArchived()

    fun getDeleted(): Flow<List<Product>> = dao.getDeleted()

    suspend fun seedSampleData() {
        val existing = dao.getById("prod-sample-1")
        if (existing != null) return
        insertAll(listOf(
            Product(
                id = "prod-sample-1",
                businessId = "default-business",
                name = "Maize Flour 2kg",
                sku = "MF-001",
                categoryId = "cat-grains",
                category = "Grains",
                priceCents = 22000,
                unitOfMeasure = "piece",
                conversionRatio = 1,
                originalPriceCents = 22000,
                isActive = true
            ),
            Product(
                id = "prod-sample-2",
                businessId = "default-business",
                name = "Rice 5kg",
                sku = "RC-002",
                categoryId = "cat-grains",
                category = "Grains",
                priceCents = 65000,
                unitOfMeasure = "piece",
                conversionRatio = 1,
                originalPriceCents = 65000,
                isActive = true
            ),
            Product(
                id = "prod-sample-3",
                businessId = "default-business",
                name = "Cooking Oil 2L",
                sku = "OIL-003",
                categoryId = "cat-oils",
                category = "Oils",
                priceCents = 85000,
                unitOfMeasure = "liter",
                conversionRatio = 1,
                originalPriceCents = 85000,
                isActive = true
            ),
            Product(
                id = "prod-sample-4",
                businessId = "default-business",
                name = "Sugar 2kg",
                sku = "SUG-004",
                categoryId = "cat-staples",
                category = "Staples",
                priceCents = 32000,
                unitOfMeasure = "piece",
                conversionRatio = 1,
                originalPriceCents = 32000,
                isActive = true
            ),
            Product(
                id = "prod-sample-5",
                businessId = "default-business",
                name = "Salt 1kg",
                sku = "SAL-005",
                categoryId = "cat-staples",
                category = "Staples",
                priceCents = 15000,
                unitOfMeasure = "piece",
                conversionRatio = 1,
                originalPriceCents = 15000,
                isActive = true
            )
        ))
    }
}
