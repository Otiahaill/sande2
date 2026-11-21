package com.example.sande.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.sande.data.db.AppDatabase
import com.example.sande.data.entities.AuditEvent
import com.example.sande.data.entities.Product
import com.example.sande.data.entities.ProductPriceHistory
import com.example.sande.data.repository.ProductRepository
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * Tests ProductRepository price history tracking and audit events.
 */
class ProductPriceHistoryTest {

    private lateinit var database: AppDatabase
    private lateinit var productRepository: ProductRepository

    @Before
    fun setup() {
        val context: Context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        productRepository = ProductRepository(database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun updatePriceRecordsHistoryAndAudit() = runBlocking {
        // Insert product
        val product = Product(
            id = "prod1",
            name = "Test Product",
            categoryId = "cat1",
            businessId = "biz1",
            priceCents = 1000,
            isActive = true,
            createdAt = System.currentTimeMillis()
        )
        productRepository.insert(product)

        // Update price
        val updated = productRepository.updatePrice("prod1", 1500, "user1", "owner", "LOC", "biz1", "Cost increase")
        assertThat(updated).isNotNull()
        assertThat(updated!!.priceCents).isEqualTo(1500L)

        // Verify price history recorded
        val history = database.productPriceHistoryDao().getByProduct("prod1").first()
        assertThat(history).hasSize(1)
        val record = history[0]
        assertThat(record.productId).isEqualTo("prod1")
        assertThat(record.oldPriceCents).isEqualTo(1000L)
        assertThat(record.newPriceCents).isEqualTo(1500L)
        assertThat(record.changedBy).isEqualTo("user1")
        assertThat(record.reason).isEqualTo("Cost increase")

        // Verify audit event recorded
        val auditEvents = database.auditEventDao().getByEntity("biz1", "prod1").first()
        assertThat(auditEvents).hasSize(1)
        val audit = auditEvents[0]
        assertThat(audit.entityType).isEqualTo("product")
        assertThat(audit.action).isEqualTo("updated")
        assertThat(audit.actorId).isEqualTo("user1")
        assertThat(audit.beforeJson).contains("1000")
        assertThat(audit.afterJson).contains("1500")
    }

    @Test
    fun updatePriceToSameValueDoesNothing() = runBlocking {
        val product = Product(
            id = "prod1",
            name = "Test Product",
            categoryId = "cat1",
            businessId = "biz1",
            priceCents = 1000,
            isActive = true,
            createdAt = System.currentTimeMillis()
        )
        productRepository.insert(product)

        // Update to same price
        val updated = productRepository.updatePrice("prod1", 1000, "user1", "owner", "LOC", "biz1", "No change")
        assertThat(updated).isNotNull()
        assertThat(updated!!.priceCents).isEqualTo(1000L)

        // No history should be recorded
        val history = database.productPriceHistoryDao().getByProduct("prod1").first()
        assertThat(history).isEmpty()

        // No audit event should be recorded
        val auditEvents = database.auditEventDao().getByEntity("biz1", "prod1").first()
        assertThat(auditEvents).isEmpty()
    }

    @Test
    fun multiplePriceUpdatesRecorded() = runBlocking {
        val product = Product(
            id = "prod1",
            name = "Test Product",
            categoryId = "cat1",
            businessId = "biz1",
            priceCents = 1000,
            isActive = true,
            createdAt = System.currentTimeMillis()
        )
        productRepository.insert(product)

        // Multiple updates
        productRepository.updatePrice("prod1", 1200, "user1", "owner", "LOC", "biz1", "Update 1")
        productRepository.updatePrice("prod1", 1100, "user2", "staff", "LOC", "biz1", "Update 2")
        productRepository.updatePrice("prod1", 1300, "user1", "owner", "LOC", "biz1", "Update 3")

        val history = database.productPriceHistoryDao().getByProduct("prod1").first()
        assertThat(history).hasSize(3)

        // Most recent first
        assertThat(history[0].newPriceCents).isEqualTo(1300L)
        assertThat(history[1].newPriceCents).isEqualTo(1100L)
        assertThat(history[2].newPriceCents).isEqualTo(1200L)

        // Verify 3 audit events
        val auditEvents = database.auditEventDao().getByEntity("biz1", "prod1").first()
        assertThat(auditEvents).hasSize(3)
    }

    @Test
    fun getLatestByProductReturnsMostRecent() = runBlocking {
        val product = Product(
            id = "prod1",
            name = "Test Product",
            categoryId = "cat1",
            businessId = "biz1",
            priceCents = 1000,
            isActive = true,
            createdAt = System.currentTimeMillis()
        )
        productRepository.insert(product)

        productRepository.updatePrice("prod1", 1200, "user1", "owner", "LOC", "biz1")
        productRepository.updatePrice("prod1", 1100, "user2", "staff", "LOC", "biz1")

        val latest = database.productPriceHistoryDao().getLatestByProduct("prod1")
        assertThat(latest).isNotNull()
        assertThat(latest!!.newPriceCents).isEqualTo(1100L)
        assertThat(latest.oldPriceCents).isEqualTo(1200L)
    }

    @Test
    fun archiveRecordsAuditEvent() = runBlocking {
        val product = Product(
            id = "prod1",
            name = "Test Product",
            categoryId = "cat1",
            businessId = "biz1",
            priceCents = 1000,
            isActive = true,
            createdAt = System.currentTimeMillis()
        )
        productRepository.insert(product)

        productRepository.archive("prod1", "user1", "owner", "LOC", "biz1")

        val auditEvents = database.auditEventDao().getByEntity("biz1", "prod1").first()
        assertThat(auditEvents).hasSize(1)
        assertThat(auditEvents[0].action).isEqualTo("archived")
    }

    @Test
    fun softDeleteRecordsAuditEvent() = runBlocking {
        val product = Product(
            id = "prod1",
            name = "Test Product",
            categoryId = "cat1",
            businessId = "biz1",
            priceCents = 1000,
            isActive = true,
            createdAt = System.currentTimeMillis()
        )
        productRepository.insert(product)

        productRepository.softDelete("prod1", "user1", "owner", "LOC", "biz1")

        val auditEvents = database.auditEventDao().getByEntity("biz1", "prod1").first()
        assertThat(auditEvents).hasSize(1)
        assertThat(auditEvents[0].action).isEqualTo("deleted")
        assertThat(auditEvents[0].severity).isEqualTo("warning")
    }

    @Test
    fun setActiveRecordsAuditEvent() = runBlocking {
        val product = Product(
            id = "prod1",
            name = "Test Product",
            categoryId = "cat1",
            businessId = "biz1",
            priceCents = 1000,
            isActive = true,
            createdAt = System.currentTimeMillis()
        )
        productRepository.insert(product)

        productRepository.setActive("prod1", false, "user1", "owner", "LOC", "biz1")

        val auditEvents = database.auditEventDao().getByEntity("biz1", "prod1").first()
        assertThat(auditEvents).hasSize(1)
        assertThat(auditEvents[0].action).isEqualTo("deactivated")
    }
}
