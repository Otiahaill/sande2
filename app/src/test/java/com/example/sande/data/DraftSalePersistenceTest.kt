package com.example.sande.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.sande.data.db.AppDatabase
import com.example.sande.data.entities.DraftSale
import com.example.sande.data.entities.Product
import com.example.sande.data.repository.DraftSaleRepository
import com.example.sande.data.repository.ProductRepository
import com.example.sande.ui.viewmodel.SalesViewModel
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * Tests DraftSaleRepository persistence and SalesViewModel cart restoration.
 */
class DraftSalePersistenceTest {

    private lateinit var database: AppDatabase
    private lateinit var draftSaleRepository: DraftSaleRepository
    private lateinit var productRepository: ProductRepository

    @Before
    fun setup() {
        val context: Context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        draftSaleRepository = DraftSaleRepository(database)
        productRepository = ProductRepository(database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun draftSalePersistsAndRestores() = runBlocking {
        // Insert a draft sale
        val cartJson = """[{"productId":"prod1","productName":"Test Product","quantity":2,"unitPriceCents":1000}]"""
        val draft = DraftSale(
            id = "biz1:user1",
            businessId = "biz1",
            salespersonId = "user1",
            salespersonRole = "owner",
            cartItems = cartJson,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        draftSaleRepository.insert(draft)

        // Retrieve it
        val retrieved = draftSaleRepository.getById("biz1:user1").first()
        assertThat(retrieved).isNotNull()
        assertThat(retrieved!!.cartItems).isEqualTo(cartJson)
        assertThat(retrieved.businessId).isEqualTo("biz1")
        assertThat(retrieved.salespersonId).isEqualTo("user1")
    }

    @Test
    fun draftSaleDelete() = runBlocking {
        val cartJson = """[{"productId":"prod1","productName":"Test Product","quantity":2,"unitPriceCents":1000}]"""
        val draft = DraftSale(
            id = "biz1:user1",
            businessId = "biz1",
            salespersonId = "user1",
            salespersonRole = "owner",
            cartItems = cartJson,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        draftSaleRepository.insert(draft)

        // Delete it
        draftSaleRepository.delete("biz1:user1")

        val retrieved = draftSaleRepository.getById("biz1:user1").first()
        assertThat(retrieved).isNull()
    }

    @Test
    fun draftSaleDeleteBySalesperson() = runBlocking {
        val cartJson = """[{"productId":"prod1","productName":"Test Product","quantity":2,"unitPriceCents":1000}]"""
        draftSaleRepository.insert(DraftSale(
            id = "biz1:user1",
            businessId = "biz1",
            salespersonId = "user1",
            salespersonRole = "owner",
            cartItems = cartJson,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        ))
        draftSaleRepository.insert(DraftSale(
            id = "biz1:user2",
            businessId = "biz1",
            salespersonId = "user2",
            salespersonRole = "staff",
            cartItems = cartJson,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        ))

        // Delete all for user1
        draftSaleRepository.deleteBySalesperson("user1")

        val user1Draft = draftSaleRepository.getById("biz1:user1").first()
        val user2Draft = draftSaleRepository.getById("biz1:user2").first()

        assertThat(user1Draft).isNull()
        assertThat(user2Draft).isNotNull()
    }

    @Test
    fun draftSaleDeleteOlderThan() = runBlocking {
        val oldTime = System.currentTimeMillis() - 86400000 * 10 // 10 days ago
        val newTime = System.currentTimeMillis()

        draftSaleRepository.insert(DraftSale(
            id = "biz1:user1",
            businessId = "biz1",
            salespersonId = "user1",
            salespersonRole = "owner",
            cartItems = """[]""",
            createdAt = oldTime,
            updatedAt = oldTime
        ))
        draftSaleRepository.insert(DraftSale(
            id = "biz1:user2",
            businessId = "biz1",
            salespersonId = "user2",
            salespersonRole = "staff",
            cartItems = """[]""",
            createdAt = newTime,
            updatedAt = newTime
        ))

        // Delete older than 5 days
        draftSaleRepository.deleteOlderThan(System.currentTimeMillis() - 86400000 * 5)

        val user1Draft = draftSaleRepository.getById("biz1:user1").first()
        val user2Draft = draftSaleRepository.getById("biz1:user2").first()

        assertThat(user1Draft).isNull()
        assertThat(user2Draft).isNotNull()
    }
}
