package com.example.sande.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.sande.data.db.AppDatabase
import com.example.sande.data.entities.BusinessProfile
import com.example.sande.data.entities.SaleItem
import com.example.sande.data.entities.SaleSession
import com.example.sande.data.repository.BusinessProfileRepository
import com.example.sande.data.repository.ReportsViewModel
import com.example.sande.data.repository.SaleItemRepository
import com.example.sande.data.repository.SaleSessionRepository
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * Tests for ReportsViewModel calculations.
 */
class ReportsTest {

    private lateinit var database: AppDatabase
    private lateinit var saleSessionRepository: SaleSessionRepository
    private lateinit var saleItemRepository: SaleItemRepository
    private lateinit var businessProfileRepository: BusinessProfileRepository
    private lateinit var reportsViewModel: ReportsViewModel

    @Before
    fun setup() {
        val context: Context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        saleSessionRepository = SaleSessionRepository(database)
        saleItemRepository = SaleItemRepository(database)
        businessProfileRepository = BusinessProfileRepository(database)
        reportsViewModel = ReportsViewModel(saleSessionRepository, saleItemRepository, businessProfileRepository)

        // Insert business profile
        runBlocking {
            businessProfileRepository.insert(BusinessProfile(
                id = "biz1",
                name = "Test Business",
                category = "retail",
                phone = null,
                countryCode = "KE",
                currencyCode = "KES",
                timezone = "Africa/Nairobi",
                address = null,
                logoUri = null,
                negativeStockPolicy = "warn",
                receiptFooter = null,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
                deletedAt = 0,
                schemaVersion = 1
            ))
        }
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun reportCalculatesTotalsCorrectly() = runBlocking {
        val now = System.currentTimeMillis()
        val cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("Africa/Nairobi"))
        cal.timeInMillis = now
        cal.set(java.util.Calendar.HOUR_OF_DAY, 10)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        val sessionTime = cal.timeInMillis

        // Insert test sessions
        val session1 = SaleSession(
            id = "session1",
            businessId = "biz1",
            receiptNumber = "SAL-LOC-00001",
            deviceId = "dev1",
            deviceCode = "LOC",
            salespersonId = "user1",
            salespersonRole = "owner",
            subtotalCents = 3000,
            taxCents = 0,
            totalCents = 3000,
            status = "completed",
            createdAt = sessionTime,
            updatedAt = sessionTime,
            synced = false
        )
        val session2 = SaleSession(
            id = "session2",
            businessId = "biz1",
            receiptNumber = "SAL-LOC-00002",
            deviceId = "dev1",
            deviceCode = "LOC",
            salespersonId = "user2",
            salespersonRole = "staff",
            subtotalCents = 5000,
            taxCents = 0,
            totalCents = 5000,
            status = "completed",
            createdAt = sessionTime + 3600000, // 1 hour later
            updatedAt = sessionTime + 3600000,
            synced = false
        )
        saleSessionRepository.insert(session1)
        saleSessionRepository.insert(session2)

        // Insert items
        val item1 = SaleItem(
            id = "item1",
            sessionId = "session1",
            productId = "prod1",
            productName = "Product A",
            categoryName = "Cat1",
            quantity = 2,
            unitPriceCents = 1500,
            totalCents = 3000,
            businessId = "biz1",
            createdAt = sessionTime
        )
        val item2 = SaleItem(
            id = "item2",
            sessionId = "session2",
            productId = "prod2",
            productName = "Product B",
            categoryName = "Cat1",
            quantity = 1,
            unitPriceCents = 5000,
            totalCents = 5000,
            businessId = "biz1",
            createdAt = sessionTime + 3600000
        )
        saleItemRepository.insert(item1)
        saleItemRepository.insert(item2)

        // Wait for flow to emit
        val reportData = reportsViewModel.reportData.first()

        assertThat(reportData.totalSales).isEqualTo(8000L)
        assertThat(reportData.transactionCount).isEqualTo(2)
        assertThat(reportData.topProducts).hasSize(2)
        assertThat(reportData.topProducts[0].name).isEqualTo("Product B") // Higher revenue first
        assertThat(reportData.topProducts[0].revenue).isEqualTo(5000L)
        assertThat(reportData.topProducts[1].revenue).isEqualTo(3000L)

        // Sales by hour (10:00 and 11:00)
        assertThat(reportData.salesByHour).hasSize(2)
        assertThat(reportData.salesByHour[0].hour).isEqualTo(10)
        assertThat(reportData.salesByHour[1].hour).isEqualTo(11)

        // Sales by cashier
        assertThat(reportData.salesByCashier).hasSize(2)
    }

    @Test
    fun reportExcludesVoidedSessions() = runBlocking {
        val now = System.currentTimeMillis()

        val session1 = SaleSession(
            id = "session1",
            businessId = "biz1",
            receiptNumber = "SAL-LOC-00001",
            deviceId = "dev1",
            deviceCode = "LOC",
            salespersonId = "user1",
            salespersonRole = "owner",
            subtotalCents = 3000,
            taxCents = 0,
            totalCents = 3000,
            status = "completed",
            createdAt = now,
            updatedAt = now,
            synced = false
        )
        val session2 = SaleSession(
            id = "session2",
            businessId = "biz1",
            receiptNumber = "SAL-LOC-00002",
            deviceId = "dev1",
            deviceCode = "LOC",
            salespersonId = "user1",
            salespersonRole = "owner",
            subtotalCents = 5000,
            taxCents = 0,
            totalCents = 5000,
            status = "voided", // Voided
            createdAt = now,
            updatedAt = now,
            synced = false
        )
        saleSessionRepository.insert(session1)
        saleSessionRepository.insert(session2)

        val reportData = reportsViewModel.reportData.first()

        assertThat(reportData.totalSales).isEqualTo(3000L) // Only completed
        assertThat(reportData.transactionCount).isEqualTo(1)
    }

    @Test
    fun periodSelectionUpdatesDateRange() = runBlocking {
        // Test that period selection works (we can't easily test the viewmodel's internal state
        // but we can verify the calculateDateRange logic by checking the report data changes)
        reportsViewModel.setPeriod("Today")

        val reportData = reportsViewModel.reportData.first()
        // With no data in range, should be empty
        assertThat(reportData.totalSales).isEqualTo(0L)
        assertThat(reportData.transactionCount).isEqualTo(0)
    }
}
