package com.example.sande.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.sande.data.db.AppDatabase
import com.example.sande.data.entities.Movement
import com.example.sande.data.entities.MovementType
import com.example.sande.data.entities.Product
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * Tests the core ledger invariant: sum of signed deltas in movements = product balance.
 * This is the fundamental correctness property of the stock ledger.
 */
class LedgerBalanceTest {

    private lateinit var database: AppDatabase

    @Before
    fun setup() {
        val context: Context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun balanceEqualsSumOfSignedDeltas() = runBlocking {
        val productDao = database.productDao()
        val movementDao = database.movementDao()

        // Insert test product
        val product = Product(
            id = "prod1",
            name = "Test Product",
            categoryId = "cat1",
            businessId = "biz1",
            priceCents = 1000,
            isActive = true,
            createdAt = System.currentTimeMillis()
        )
        productDao.insert(product)

        // Record various movements
        val movements = listOf(
            Movement(id = "m1", productId = "prod1", type = MovementType.RECEIVE, quantity = 100, unitPriceCents = null, referenceId = null, deviceId = "dev1", businessId = "biz1"),
            Movement(id = "m2", productId = "prod1", type = MovementType.SALE, quantity = 30, unitPriceCents = 1000, referenceId = "sale1", deviceId = "dev1", businessId = "biz1"),
            Movement(id = "m3", productId = "prod1", type = MovementType.RECEIVE, quantity = 50, unitPriceCents = null, referenceId = null, deviceId = "dev1", businessId = "biz1"),
            Movement(id = "m4", productId = "prod1", type = MovementType.SALE, quantity = 20, unitPriceCents = 1000, referenceId = "sale2", deviceId = "dev1", businessId = "biz1"),
            Movement(id = "m5", productId = "prod1", type = MovementType.ADJUST, quantity = -5, unitPriceCents = null, referenceId = "adj1", deviceId = "dev1", businessId = "biz1"),
        )

        // Insert all movements
        movementDao.insertAll(movements)

        // Calculate balance from movements (the ledger invariant)
        val allMovements = movementDao.getByProduct("prod1")
        val calculatedBalance = allMovements.sumOf { m ->
            when (m.type) {
                MovementType.RECEIVE -> m.quantity
                MovementType.SALE -> -m.quantity
                MovementType.ADJUST -> m.quantity
                MovementType.VOID -> m.quantity
            }
        }

        // Expected: 100 - 30 + 50 - 20 - 5 = 95
        assertThat(calculatedBalance).isEqualTo(95L)

        // Verify DAO balance query matches
        val daoBalance = movementDao.getStockBalance("prod1")
        assertThat(daoBalance).isEqualTo(calculatedBalance)
    }

    @Test
    fun multipleProductsHaveIndependentBalances() = runBlocking {
        val productDao = database.productDao()
        val movementDao = database.movementDao()

        // Insert two products
        productDao.insert(Product(id = "prod1", name = "Product 1", categoryId = "cat1", businessId = "biz1", priceCents = 1000, isActive = true, createdAt = System.currentTimeMillis()))
        productDao.insert(Product(id = "prod2", name = "Product 2", categoryId = "cat1", businessId = "biz1", priceCents = 2000, isActive = true, createdAt = System.currentTimeMillis()))

        // Movements for prod1: receive 10, sell 3 = balance 7
        movementDao.insertAll(listOf(
            Movement(id = "m1", productId = "prod1", type = MovementType.RECEIVE, quantity = 10, unitPriceCents = null, referenceId = null, deviceId = "dev1", businessId = "biz1"),
            Movement(id = "m2", productId = "prod1", type = MovementType.SALE, quantity = 3, unitPriceCents = 1000, referenceId = "sale1", deviceId = "dev1", businessId = "biz1"),
        ))

        // Movements for prod2: receive 50, sell 10 = balance 40
        movementDao.insertAll(listOf(
            Movement(id = "m3", productId = "prod2", type = MovementType.RECEIVE, quantity = 50, unitPriceCents = null, referenceId = null, deviceId = "dev1", businessId = "biz1"),
            Movement(id = "m4", productId = "prod2", type = MovementType.SALE, quantity = 10, unitPriceCents = 2000, referenceId = "sale2", deviceId = "dev1", businessId = "biz1"),
        ))

        val balance1 = movementDao.getStockBalance("prod1")
        val balance2 = movementDao.getStockBalance("prod2")

        assertThat(balance1).isEqualTo(7L)
        assertThat(balance2).isEqualTo(40L)
    }

    @Test
    fun voidMovementCancelsPreviousSale() = runBlocking {
        val productDao = database.productDao()
        val movementDao = database.movementDao()

        productDao.insert(Product(id = "prod1", name = "Product 1", categoryId = "cat1", businessId = "biz1", priceCents = 1000, isActive = true, createdAt = System.currentTimeMillis()))

        // Receive 100
        movementDao.insert(Movement(id = "m1", productId = "prod1", type = MovementType.RECEIVE, quantity = 100, unitPriceCents = null, referenceId = null, deviceId = "dev1", businessId = "biz1"))
        // Sell 30
        movementDao.insert(Movement(id = "m2", productId = "prod1", type = MovementType.SALE, quantity = 30, unitPriceCents = 1000, referenceId = "sale1", deviceId = "dev1", businessId = "biz1"))
        // Void the sale (adds back 30)
        movementDao.insert(Movement(id = "m3", productId = "prod1", type = MovementType.VOID, quantity = 30, unitPriceCents = null, referenceId = "sale1", deviceId = "dev1", businessId = "biz1"))

        val balance = movementDao.getStockBalance("prod1")
        // 100 - 30 + 30 = 100
        assertThat(balance).isEqualTo(100L)
    }
}
