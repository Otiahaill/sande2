package com.example.sande.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.sande.data.db.AppDatabase
import com.example.sande.data.entities.Product
import com.example.sande.data.entities.SaleSession
import com.example.sande.data.entities.SaleItem
import com.example.sande.data.entities.StockMovement
import com.example.sande.data.entities.Receipt
import com.example.sande.data.entities.AuditEvent
import com.example.sande.data.entities.Counter
import com.example.sande.data.entities.OutboxEntry
import com.example.sande.data.repository.CheckoutRepository
import com.example.sande.data.repository.ProductRepository
import com.example.sande.data.repository.SaleHeaderRepository
import com.example.sande.data.repository.SaleLineItem
import com.example.sande.data.repository.SaleSessionRepository
import com.example.sande.data.repository.SaleItemRepository
import com.example.sande.data.repository.StockMovementRepository
import com.example.sande.data.receipt.ReceiptRepository
import com.example.sande.data.repository.CounterRepository
import com.example.sande.data.repository.AuditEventRepository
import com.example.sande.data.repository.OutboxEntryRepository
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * Tests the CheckoutRepository atomic transaction.
 * Verifies all tables are written correctly in a single transaction.
 */
class CheckoutRepositoryTest {

    private lateinit var database: AppDatabase
    private lateinit var checkoutRepository: CheckoutRepository
    private lateinit var productRepository: ProductRepository
    private lateinit var saleHeaderRepository: SaleHeaderRepository
    private lateinit var saleSessionRepository: SaleSessionRepository
    private lateinit var saleItemRepository: SaleItemRepository
    private lateinit var stockMovementRepository: StockMovementRepository
    private lateinit var receiptRepository: ReceiptRepository
    private lateinit var counterRepository: CounterRepository
    private lateinit var auditEventRepository: AuditEventRepository
    private lateinit var outboxEntryRepository: OutboxEntryRepository

    @Before
    fun setup() {
        val context: Context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        productRepository = ProductRepository(database)
        saleHeaderRepository = SaleHeaderRepository(database)
        saleSessionRepository = SaleSessionRepository(database)
        saleItemRepository = SaleItemRepository(database)
        stockMovementRepository = StockMovementRepository(database)
        receiptRepository = ReceiptRepository(database)
        counterRepository = CounterRepository(database)
        auditEventRepository = AuditEventRepository(database)
        outboxEntryRepository = OutboxEntryRepository(database)
        checkoutRepository = CheckoutRepository(database, productRepository)

        // Insert test products
        runBlocking {
            productRepository.insert(Product(
                id = "prod1",
                name = "Test Product 1",
                categoryId = "cat1",
                businessId = "biz1",
                priceCents = 1000,
                isActive = true,
                createdAt = System.currentTimeMillis()
            ))
            productRepository.insert(Product(
                id = "prod2",
                name = "Test Product 2",
                categoryId = "cat1",
                businessId = "biz1",
                priceCents = 2000,
                isActive = true,
                createdAt = System.currentTimeMillis()
            ))
        }
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun successfulSaleCreatesAllRecords() = runBlocking {
        val items = listOf(
            SaleLineItem("prod1", 3, 1000),
            SaleLineItem("prod2", 2, 2000)
        )
        val receiptNumber = "SAL-LOC-00001"

        val result = checkoutRepository.recordSaleAtomic(
            items,
            receiptNumber,
            "biz1",
            "dev1",
            "LOC",
            "user1",
            "owner",
            "user1",
            "owner"
        )

        // Verify result
        assertThat(result.sessionId).isNotEmpty()
        assertThat(result.receiptNumber).isEqualTo(receiptNumber)
        assertThat(result.receiptId).isNotEmpty()
        assertThat(result.totalCents).isEqualTo(7000L) // 3*1000 + 2*2000

        // Verify SaleSession
        val session = saleSessionRepository.getById(result.sessionId).first()
        assertThat(session).isNotNull()
        assertThat(session!!.receiptNumber).isEqualTo(receiptNumber)
        assertThat(session.businessId).isEqualTo("biz1")
        assertThat(session.totalCents).isEqualTo(7000L)
        assertThat(session.status).isEqualTo("completed")

        // Verify SaleItems
        val saleItems = saleItemRepository.getBySession(result.sessionId).first()
        assertThat(saleItems).hasSize(2)
        val item1 = saleItems.find { it.productId == "prod1" }!!
        val item2 = saleItems.find { it.productId == "prod2" }!!
        assertThat(item1.quantity).isEqualTo(3L)
        assertThat(item1.unitPriceCents).isEqualTo(1000L)
        assertThat(item1.totalCents).isEqualTo(3000L)
        assertThat(item2.quantity).isEqualTo(2L)
        assertThat(item2.unitPriceCents).isEqualTo(2000L)
        assertThat(item2.totalCents).isEqualTo(4000L)

        // Verify StockMovements (SALE = negative deltas)
        val movements = stockMovementRepository.getByReference("biz1", result.sessionId).first()
        assertThat(movements).hasSize(2)
        movements.forEach { m ->
            assertThat(m.movementType).isEqualTo("SALE")
            assertThat(m.quantityDelta).isLessThan(0L) // Negative for sale
            assertThat(m.referenceId).isEqualTo(result.sessionId)
        }
        val move1 = movements.find { it.productId == "prod1" }!!
        val move2 = movements.find { it.productId == "prod2" }!!
        assertThat(move1.quantityDelta).isEqualTo(-3L)
        assertThat(move2.quantityDelta).isEqualTo(-2L)

        // Verify Receipt
        val receiptData = receiptRepository.getReceiptDataBySession(result.sessionId)
        assertThat(receiptData).isNotNull()
        val receipt = receiptData!!.receipt
        assertThat(receipt.receiptNumber).isEqualTo(receiptNumber)
        assertThat(receipt.counter).isEqualTo(1L)
        assertThat(receipt.contentHash).isNotEmpty()

        // Verify Counter incremented
        val counterValue = counterRepository.getCurrentValue("biz1", "LOC", "receipt")
        assertThat(counterValue).isEqualTo(1L)

        // Verify AuditEvent
        val auditEvents = auditEventRepository.getByEntity("biz1", result.sessionId).first()
        assertThat(auditEvents).hasSize(1)
        assertThat(auditEvents[0].action).isEqualTo("created")
        assertThat(auditEvents[0].entityType).isEqualTo("sale_session")

        // Verify OutboxEntry
        val outboxEntries = outboxEntryRepository.getByBusiness("biz1").first()
        assertThat(outboxEntries).hasSize(1)
        assertThat(outboxEntries[0].entityType).isEqualTo("sale_session")
        assertThat(outboxEntries[0].entityId).isEqualTo(result.sessionId)
        assertThat(outboxEntries[0].operation).isEqualTo("create")
    }

    @Test
    fun recordReceiveAtomicCreatesRecords() = runBlocking {
        val movementId = checkoutRepository.recordReceiveAtomic(
            "prod1",
            50,
            "biz1",
            "LOC",
            "user1",
            "owner",
            null,
            "Stock received"
        )

        assertThat(movementId).isNotEmpty()

        // Verify StockMovement
        val movements = stockMovementRepository.getByBusiness("biz1").first()
        assertThat(movements).hasSize(1)
        val m = movements[0]
        assertThat(m.id).isEqualTo(movementId)
        assertThat(m.movementType).isEqualTo("RECEIVE")
        assertThat(m.quantityDelta).isEqualTo(50L)
        assertThat(m.productId).isEqualTo("prod1")

        // Verify balance
        val balance = stockMovementRepository.getStockBalance("prod1")
        assertThat(balance).isEqualTo(50L)

        // Verify AuditEvent
        val auditEvents = auditEventRepository.getByEntity("biz1", movementId).first()
        assertThat(auditEvents).hasSize(1)
        assertThat(auditEvents[0].action).isEqualTo("created")
        assertThat(auditEvents[0].entityType).isEqualTo("stock_movement")

        // Verify OutboxEntry
        val outboxEntries = outboxEntryRepository.getByBusiness("biz1").first()
        assertThat(outboxEntries).hasSize(1)
        assertThat(outboxEntries[0].entityType).isEqualTo("stock_movement")
    }

    @Test
    fun recordAdjustmentAtomicCreatesRecords() = runBlocking {
        // First receive some stock
        checkoutRepository.recordReceiveAtomic("prod1", 100, "biz1", "LOC", "user1", "owner")

        // Then adjust down by 5
        val movementId = checkoutRepository.recordAdjustmentAtomic(
            "prod1",
            -5,
            "biz1",
            "LOC",
            "user1",
            "owner",
            "Damaged goods"
        )

        // Verify balance
        val balance = stockMovementRepository.getStockBalance("prod1")
        assertThat(balance).isEqualTo(95L)

        // Verify movement
        val movements = stockMovementRepository.getByBusiness("biz1").first()
        assertThat(movements).hasSize(2)
        val adj = movements.find { it.id == movementId }!!
        assertThat(adj.movementType).isEqualTo("ADJUST")
        assertThat(adj.quantityDelta).isEqualTo(-5L)
    }

    @Test
    fun voidSaleAtomicReversesStockAndUpdatesSession() = runBlocking {
        // First create a sale
        val items = listOf(SaleLineItem("prod1", 10, 1000))
        val result = checkoutRepository.recordSaleAtomic(
            items,
            "SAL-LOC-00002",
            "biz1",
            "dev1",
            "LOC",
            "user1",
            "owner",
            "user1",
            "owner"
        )

        // Verify initial balance
        var balance = stockMovementRepository.getStockBalance("prod1")
        assertThat(balance).isEqualTo(-10L) // Negative because only sale, no receive

        // Now void it
        val voided = checkoutRepository.voidSaleAtomic(
            result.sessionId,
            "biz1",
            "LOC",
            "user1",
            "owner",
            "Customer cancelled"
        )

        assertThat(voided).isTrue()

        // Verify session status
        val session = saleSessionRepository.getById(result.sessionId).first()
        assertThat(session!!).isNotNull()
        assertThat(session.status).isEqualTo("voided")

        // Verify reversal movements created (VOID = positive delta)
        val movements = stockMovementRepository.getByBusiness("biz1").first()
        val voidMovements = movements.filter { it.movementType == "VOID" }
        assertThat(voidMovements).hasSize(1)
        assertThat(voidMovements[0].quantityDelta).isEqualTo(10L) // Positive to reverse
        assertThat(voidMovements[0].referenceId).isEqualTo(result.sessionId)

        // Verify new balance (net zero)
        balance = stockMovementRepository.getStockBalance("prod1")
        assertThat(balance).isEqualTo(0L)

        // Verify audit event for void
        val auditEvents = auditEventRepository.getByEntity("biz1", result.sessionId).first()
        val voidAudit = auditEvents.find { it.action == "voided" }
        assertThat(voidAudit).isNotNull()
        assertThat(voidAudit!!.severity).isEqualTo("warning")

        // Verify outbox entry for void
        val outboxEntries = outboxEntryRepository.getByBusiness("biz1").first()
        val voidOutbox = outboxEntries.find { it.entityType == "sale_session" && it.operation == "update" }
        assertThat(voidOutbox).isNotNull()
    }

    @Test
    voidSaleAtomicFailsForAlreadyVoided() = runBlocking {
        val items = listOf(SaleLineItem("prod1", 5, 1000))
        val result = checkoutRepository.recordSaleAtomic(
            items,
            "SAL-LOC-00003",
            "biz1",
            "dev1",
            "LOC",
            "user1",
            "owner",
            "user1",
            "owner"
        )

        // Void once
        checkoutRepository.voidSaleAtomic(result.sessionId, "biz1", "LOC", "user1", "owner", "Reason 1")

        // Try to void again
        val voided = checkoutRepository.voidSaleAtomic(result.sessionId, "biz1", "LOC", "user1", "owner", "Reason 2")

        assertThat(voided).isFalse()
    }

    @Test
    fun multipleSalesIncrementCounter() = runBlocking {
        // First sale
        checkoutRepository.recordSaleAtomic(
            listOf(SaleLineItem("prod1", 1, 1000)),
            "SAL-LOC-00004",
            "biz1", "dev1", "LOC", "user1", "owner", "user1", "owner"
        )

        var counter = counterRepository.getCurrentValue("biz1", "LOC", "receipt")
        assertThat(counter).isEqualTo(1L)

        // Second sale
        checkoutRepository.recordSaleAtomic(
            listOf(SaleLineItem("prod1", 1, 1000)),
            "SAL-LOC-00005",
            "biz1", "dev1", "LOC", "user1", "owner", "user1", "owner"
        )

        counter = counterRepository.getCurrentValue("biz1", "LOC", "receipt")
        assertThat(counter).isEqualTo(2L)
    }
}
