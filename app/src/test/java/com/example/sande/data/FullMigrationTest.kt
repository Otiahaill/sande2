package com.example.sande.data

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import com.example.sande.data.db.AppDatabase
import com.example.sande.data.db.AppDatabase.MIGRATION_2_3
import com.example.sande.data.db.AppDatabase.MIGRATION_3_4
import com.example.sande.data.db.AppDatabase.MIGRATION_4_5
import com.example.sande.data.db.AppDatabase.MIGRATION_5_6
import com.example.sande.data.entities.Movement
import com.example.sande.data.entities.MovementType
import com.example.sande.data.entities.Product
import com.example.sande.data.entities.SaleHeader
import com.example.sande.data.entities.StockAdjustment
import com.example.sande.data.entities.Outbox
import com.example.sande.data.entities.StockMovement
import com.example.sande.data.entities.SaleSession
import com.example.sande.data.entities.SaleItem
import com.example.sande.data.entities.Receipt
import com.example.sande.data.entities.Counter
import com.example.sande.data.entities.BusinessProfile
import com.example.sande.data.entities.AuditEvent
import com.example.sande.data.entities.OutboxEntry
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException

/**
 * Tests the full migration path from v2 to v6 (current).
 * Verifies data integrity through all migrations.
 */
class FullMigrationTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation().targetContext,
        FrameworkSQLiteOpenHelperFactory(),
        folder
    )

    @Test
    @Throws(IOException::class)
    fun fullMigration2To6() {
        // Create database at version 2 (original state before any migrations)
        val db = helper.createDatabase("sande_database", 2)

        // Insert realistic test data at v2
        val now = System.currentTimeMillis()

        // Products
        db.execSQL("INSERT INTO products (id, name, categoryId, businessId, priceCents, isActive, createdAt) " +
                "VALUES ('prod1', 'Test Product 1', 'cat1', 'biz1', 1000, 1, $now)")
        db.execSQL("INSERT INTO products (id, name, categoryId, businessId, priceCents, isActive, createdAt) " +
                "VALUES ('prod2', 'Test Product 2', 'cat1', 'biz1', 2000, 1, $now)")

        // Sale headers (with receipt numbers)
        db.execSQL("INSERT INTO sale_headers (id, businessId, receiptNumber, deviceId, salespersonId, salespersonRole, status, totalCents, createdAt) " +
                "VALUES ('sale1', 'biz1', 'SAL-LOC-00001', 'dev1', 'user1', 'owner', 'completed', 5000, $now)")
        db.execSQL("INSERT INTO sale_headers (id, businessId, receiptNumber, deviceId, salespersonId, salespersonRole, status, totalCents, createdAt) " +
                "VALUES ('sale2', 'biz1', 'SAL-LOC-00002', 'dev1', 'user1', 'owner', 'completed', 3000, $now)")

        // Movements (mix of types)
        db.execSQL("INSERT INTO movements (id, productId, type, quantity, unitPriceCents, referenceId, deviceId, synced, businessId, createdAt) " +
                "VALUES ('m1', 'prod1', 'RECEIVE', 100, null, null, 'dev1', 0, 'biz1', $now)")
        db.execSQL("INSERT INTO movements (id, productId, type, quantity, unitPriceCents, referenceId, deviceId, synced, businessId, createdAt) " +
                "VALUES ('m2', 'prod1', 'SALE', 30, 1000, 'sale1', 'dev1', 0, 'biz1', $now)")
        db.execSQL("INSERT INTO movements (id, productId, type, quantity, unitPriceCents, referenceId, deviceId, synced, businessId, createdAt) " +
                "VALUES ('m3', 'prod2', 'RECEIVE', 50, null, null, 'dev1', 0, 'biz1', $now)")
        db.execSQL("INSERT INTO movements (id, productId, type, quantity, unitPriceCents, referenceId, deviceId, synced, businessId, createdAt) " +
                "VALUES ('m4', 'prod2', 'SALE', 10, 2000, 'sale2', 'dev1', 0, 'biz1', $now)")
        db.execSQL("INSERT INTO movements (id, productId, type, quantity, unitPriceCents, referenceId, deviceId, synced, businessId, createdAt) " +
                "VALUES ('m5', 'prod1', 'ADJUST', -5, null, 'adj1', 'dev1', 0, 'biz1', $now)")

        // Stock adjustments (legacy)
        db.execSQL("INSERT INTO stock_adjustments (id, productId, businessId, adjustmentType, quantityChange, reason, referenceId, createdAt, createdBy) " +
                "VALUES ('adj1', 'prod1', 'biz1', 'RECEIVE', 20, 'Extra stock', null, $now, 'user1')")

        // Outbox entries
        db.execSQL("INSERT INTO outbox (id, businessId, entityType, entityId, operation, payloadJson, createdAt, synced) " +
                "VALUES ('ob1', 'biz1', 'product', 'prod1', 'create', '{}', $now, 0)")

        // Businesses
        db.execSQL("INSERT INTO businesses (id, name, createdAt) VALUES ('biz1', 'Test Business', $now)")

        db.close()

        // Run all migrations in sequence
        val db3 = helper.runMigrationsAndValidate("sande_database", 3, true, MIGRATION_2_3)
        val db4 = helper.runMigrationsAndValidate("sande_database", 4, true, MIGRATION_3_4)
        val db5 = helper.runMigrationsAndValidate("sande_database", 5, true, MIGRATION_4_5)
        val db6 = helper.runMigrationsAndValidate("sande_database", 6, true, MIGRATION_5_6)

        // ===== VERIFY FINAL STATE (v6) =====

        // 1. Products have new columns with defaults
        var cursor = db6.rawQuery("SELECT name, archivedAt, deletedAt, originalPriceCents FROM products WHERE id = 'prod1'")
        cursor.moveToFirst()
        assertThat(cursor.getString(0)).isEqualTo("Test Product 1")
        assertThat(cursor.getLong(1)).isEqualTo(0L) // archivedAt
        assertThat(cursor.getLong(2)).isEqualTo(0L) // deletedAt
        assertThat(cursor.getLong(3)).isEqualTo(0L) // originalPriceCents
        cursor.close()

        // 2. Legacy tables still exist (movements, sale_headers, stock_adjustments, outbox)
        cursor = db6.rawQuery("SELECT COUNT(*) FROM movements WHERE businessId = 'biz1'")
        cursor.moveToFirst()
        assertThat(cursor.getLong(0)).isEqualTo(5L)
        cursor.close()

        cursor = db6.rawQuery("SELECT COUNT(*) FROM sale_headers WHERE businessId = 'biz1'")
        cursor.moveToFirst()
        assertThat(cursor.getLong(0)).isEqualTo(2L)
        cursor.close()

        // 3. New tables created and populated

        // business_profiles
        cursor = db6.rawQuery("SELECT id, name, category, countryCode, currencyCode, timezone FROM business_profiles WHERE id = 'biz1'")
        cursor.moveToFirst()
        assertThat(cursor.getString(0)).isEqualTo("biz1")
        assertThat(cursor.getString(1)).isEqualTo("Test Business")
        assertThat(cursor.getString(2)).isEqualTo("retail")
        assertThat(cursor.getString(3)).isEqualTo("KE")
        assertThat(cursor.getString(4)).isEqualTo("KES")
        assertThat(cursor.getString(5)).isEqualTo("Africa/Nairobi")
        cursor.close()

        // stock_movements (migrated from movements)
        cursor = db6.rawQuery("SELECT COUNT(*) FROM stock_movements WHERE businessId = 'biz1'")
        cursor.moveToFirst()
        assertThat(cursor.getLong(0)).isEqualTo(5L) // Same count as movements
        cursor.close()

        // Verify signed deltas
        cursor = db6.rawQuery("SELECT movementType, quantityDelta FROM stock_movements WHERE productId = 'prod1' ORDER BY createdAt")
        cursor.moveToFirst()
        // m1: RECEIVE +100
        assertThat(cursor.getString(0)).isEqualTo("RECEIVE")
        assertThat(cursor.getLong(1)).isEqualTo(100L)
        cursor.moveToNext()
        // m2: SALE -30
        assertThat(cursor.getString(0)).isEqualTo("SALE")
        assertThat(cursor.getLong(1)).isEqualTo(-30L)
        cursor.moveToNext()
        // m5: ADJUST -5
        assertThat(cursor.getString(0)).isEqualTo("ADJUST")
        assertThat(cursor.getLong(1)).isEqualTo(-5L)
        cursor.close()

        // sale_sessions (migrated from sale_headers)
        cursor = db6.rawQuery("SELECT COUNT(*) FROM sale_sessions WHERE businessId = 'biz1'")
        cursor.moveToFirst()
        assertThat(cursor.getLong(0)).isEqualTo(2L)
        cursor.close()

        cursor = db6.rawQuery("SELECT receiptNumber, subtotalCents, taxCents, totalCents FROM sale_sessions WHERE id = 'sale1'")
        cursor.moveToFirst()
        assertThat(cursor.getString(0)).isEqualTo("SAL-LOC-00001")
        assertThat(cursor.getLong(1)).isEqualTo(5000L)
        assertThat(cursor.getLong(2)).isEqualTo(0L)
        assertThat(cursor.getLong(3)).isEqualTo(5000L)
        cursor.close()

        // sale_items (created from movements + sale_headers)
        cursor = db6.rawQuery("SELECT COUNT(*) FROM sale_items WHERE sessionId IN ('sale1', 'sale2')")
        cursor.moveToFirst()
        assertThat(cursor.getLong(0)).isEqualTo(2L) // One item per sale in test data
        cursor.close()

        cursor = db6.rawQuery("SELECT productId, quantity, unitPriceCents, totalCents FROM sale_items WHERE sessionId = 'sale1'")
        cursor.moveToFirst()
        assertThat(cursor.getString(0)).isEqualTo("prod1")
        assertThat(cursor.getLong(1)).isEqualTo(30L)
        assertThat(cursor.getLong(2)).isEqualTo(1000L)
        assertThat(cursor.getLong(3)).isEqualTo(3000L)
        cursor.close()

        // receipts (created from sale_sessions)
        cursor = db6.rawQuery("SELECT COUNT(*) FROM receipts WHERE businessId = 'biz1'")
        cursor.moveToFirst()
        assertThat(cursor.getLong(0)).isEqualTo(2L)
        cursor.close()

        cursor = db6.rawQuery("SELECT sessionId, receiptNumber, counter FROM receipts WHERE sessionId = 'sale1'")
        cursor.moveToFirst()
        assertThat(cursor.getString(0)).isEqualTo("sale1")
        assertThat(cursor.getString(1)).isEqualTo("SAL-LOC-00001")
        assertThat(cursor.getLong(2)).isEqualTo(1L)
        cursor.close()

        // counters (initialized from sale_headers)
        cursor = db6.rawQuery("SELECT currentValue FROM counters WHERE businessId = 'biz1' AND deviceCode = 'dev1' AND counterType = 'receipt'")
        cursor.moveToFirst()
        assertThat(cursor.getLong(0)).isEqualTo(2L) // Two receipts for this device
        cursor.close()

        // outbox_entries (migrated from outbox)
        cursor = db6.rawQuery("SELECT COUNT(*) FROM outbox_entries WHERE businessId = 'biz1'")
        cursor.moveToFirst()
        assertThat(cursor.getLong(0)).isEqualTo(1L)
        cursor.close()

        cursor = db6.rawQuery("SELECT entityType, entityId, operation FROM outbox_entries WHERE id = 'ob1'")
        cursor.moveToFirst()
        assertThat(cursor.getString(0)).isEqualTo("product")
        assertThat(cursor.getString(1)).isEqualTo("prod1")
        assertThat(cursor.getString(2)).isEqualTo("create")
        cursor.close()

        // audit_events (empty initially, will be populated by new operations)
        cursor = db6.rawQuery("SELECT COUNT(*) FROM audit_events WHERE businessId = 'biz1'")
        cursor.moveToFirst()
        assertThat(cursor.getLong(0)).isEqualTo(0L)
        cursor.close()

        // product_price_history (empty initially)
        cursor = db6.rawQuery("SELECT COUNT(*) FROM product_price_history")
        cursor.moveToFirst()
        assertThat(cursor.getLong(0)).isEqualTo(0L)
        cursor.close()

        // membership_mirrors, device_registrations, sync_cursors, sync_exceptions (empty initially)
        cursor = db6.rawQuery("SELECT COUNT(*) FROM membership_mirrors")
        cursor.moveToFirst()
        assertThat(cursor.getLong(0)).isEqualTo(0L)
        cursor.close()

        // 4. Balance verification using new stock_movements
        // prod1: +100 (RECEIVE) -30 (SALE) -5 (ADJUST) = +65
        cursor = db6.rawQuery("SELECT COALESCE(SUM(quantityDelta), 0) FROM stock_movements WHERE productId = 'prod1'")
        cursor.moveToFirst()
        assertThat(cursor.getLong(0)).isEqualTo(65L)
        cursor.close()

        // prod2: +50 (RECEIVE) -10 (SALE) = +40
        cursor = db6.rawQuery("SELECT COALESCE(SUM(quantityDelta), 0) FROM stock_movements WHERE productId = 'prod2'")
        cursor.moveToFirst()
        assertThat(cursor.getLong(0)).isEqualTo(40L)
        cursor.close()

        // observeBalances query
        cursor = db6.rawQuery("SELECT productId, COALESCE(SUM(quantityDelta), 0) as balance FROM stock_movements WHERE businessId = 'biz1' GROUP BY productId")
        cursor.moveToFirst()
        var prod1Balance = 0L
        var prod2Balance = 0L
        while (!cursor.isAfterLast) {
            val pid = cursor.getString(0)
            val bal = cursor.getLong(1)
            if (pid == "prod1") prod1Balance = bal
            else if (pid == "prod2") prod2Balance = bal
            cursor.moveToNext()
        }
        cursor.close()
        assertThat(prod1Balance).isEqualTo(65L)
        assertThat(prod2Balance).isEqualTo(40L)

        db6.close()
    }

    @Test
    @Throws(IOException::class)
    fun migrationPreservesReceiptNumbers() {
        // Test that receipt numbers are preserved through migration
        val db = helper.createDatabase("sande_database", 2)

        db.execSQL("INSERT INTO businesses (id, name, createdAt) VALUES ('biz1', 'Test Business', 1234567890)")
        db.execSQL("INSERT INTO sale_headers (id, businessId, receiptNumber, deviceId, salespersonId, salespersonRole, status, totalCents, createdAt) " +
                "VALUES ('sale1', 'biz1', 'SAL-ABC-00001', 'devA', 'user1', 'owner', 'completed', 1000, 1234567890)")
        db.execSQL("INSERT INTO sale_headers (id, businessId, receiptNumber, deviceId, salespersonId, salespersonRole, status, totalCents, createdAt) " +
                "VALUES ('sale2', 'biz1', 'SAL-ABC-00002', 'devA', 'user1', 'owner', 'completed', 2000, 1234567890)")
        db.execSQL("INSERT INTO sale_headers (id, businessId, receiptNumber, deviceId, salespersonId, salespersonRole, status, totalCents, createdAt) " +
                "VALUES ('sale3', 'biz1', 'SAL-XYZ-00001', 'devB', 'user2', 'staff', 'completed', 3000, 1234567890)")
        db.close()

        val db3 = helper.runMigrationsAndValidate("sande_database", 3, true, MIGRATION_2_3)
        val db4 = helper.runMigrationsAndValidate("sande_database", 4, true, MIGRATION_3_4)
        val db5 = helper.runMigrationsAndValidate("sande_database", 5, true, MIGRATION_4_5)
        val db6 = helper.runMigrationsAndValidate("sande_database", 6, true, MIGRATION_5_6)

        // Verify receipts have correct numbers and counters
        var cursor = db6.rawQuery("SELECT receiptNumber, deviceCode, counter FROM receipts ORDER BY counter")
        cursor.moveToFirst()
        // First device (devA) has counter 1, 2
        assertThat(cursor.getString(0)).isEqualTo("SAL-ABC-00001")
        assertThat(cursor.getString(1)).isEqualTo("devA")
        assertThat(cursor.getLong(2)).isEqualTo(1L)
        cursor.moveToNext()
        assertThat(cursor.getString(0)).isEqualTo("SAL-ABC-00002")
        assertThat(cursor.getString(1)).isEqualTo("devA")
        assertThat(cursor.getLong(2)).isEqualTo(2L)
        cursor.moveToNext()
        // Second device (devB) has counter 1
        assertThat(cursor.getString(0)).isEqualTo("SAL-XYZ-00001")
        assertThat(cursor.getString(1)).isEqualTo("devB")
        assertThat(cursor.getLong(2)).isEqualTo(1L)
        cursor.close()

        // Verify counters table
        cursor = db6.rawQuery("SELECT deviceCode, currentValue FROM counters WHERE businessId = 'biz1' AND counterType = 'receipt' ORDER BY deviceCode")
        cursor.moveToFirst()
        assertThat(cursor.getString(0)).isEqualTo("devA")
        assertThat(cursor.getLong(1)).isEqualTo(2L)
        cursor.moveToNext()
        assertThat(cursor.getString(0)).isEqualTo("devB")
        assertThat(cursor.getLong(1)).isEqualTo(1L)
        cursor.close()

        db6.close()
    }
}
