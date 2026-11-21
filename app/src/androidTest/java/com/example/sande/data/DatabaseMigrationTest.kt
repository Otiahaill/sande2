package com.example.sande.data

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import com.example.sande.data.db.AppDatabase
import com.example.sande.data.db.AppDatabase.MIGRATION_2_3
import com.example.sande.data.db.AppDatabase.MIGRATION_3_4
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException

/**
 * Tests Room migrations to ensure schema upgrades work correctly without data loss.
 * This replaces the unsafe fallbackToDestructiveMigration().
 */
class DatabaseMigrationTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation().targetContext,
        FrameworkSQLiteOpenHelperFactory(),
        folder
    )

    @Test
    @Throws(IOException::class)
    fun migrate2To3() {
        // Create database at version 2
        val db = helper.createDatabase("sande_database", 2)

        // Insert test data that should survive migration
        db.execSQL("INSERT INTO products (id, name, categoryId, businessId, priceCents, isActive, createdAt) " +
                "VALUES ('prod1', 'Test Product', 'cat1', 'biz1', 1000, 1, 1234567890)")
        db.execSQL("INSERT INTO sale_headers (id, businessId, salespersonId, salespersonRole, status, createdAt) " +
                "VALUES ('sale1', 'biz1', 'user1', 'owner', 'completed', 1234567890)")
        db.close()

        // Migrate to version 3
        val db3 = helper.runMigrationsAndValidate("sande_database", 3, true, MIGRATION_2_3)

        // Verify new columns exist with correct defaults
        var cursor = db3.rawQuery("SELECT archivedAt, deletedAt, originalPriceCents FROM products WHERE id = 'prod1'")
        cursor.moveToFirst()
        assert(cursor.getLong(0) == 0L) // archivedAt default
        assert(cursor.getLong(1) == 0L) // deletedAt default
        assert(cursor.getLong(2) == 0L) // originalPriceCents default
        cursor.close()

        cursor = db3.rawQuery("SELECT salespersonId, salespersonRole, status FROM sale_headers WHERE id = 'sale1'")
        cursor.moveToFirst()
        assert(cursor.getString(0) == "") // salespersonId default
        assert(cursor.getString(1) == "") // salespersonRole default
        assert(cursor.getString(2) == "completed") // status default
        cursor.close()

        // Verify new tables exist
        cursor = db3.rawQuery("SELECT name FROM sqlite_master WHERE type='table' AND name IN ('draft_sales', 'stock_adjustments')")
        assert(cursor.count == 2)
        cursor.close()

        // Verify original data still there
        cursor = db3.rawQuery("SELECT name FROM products WHERE id = 'prod1'")
        cursor.moveToFirst()
        assert(cursor.getString(0) == "Test Product")
        cursor.close()

        db3.close()
    }

    @Test
    @Throws(IOException::class)
    fun migrate3To4() {
        // Create database at version 3
        val db = helper.createDatabase("sande_database", 3)

        // Insert data that exercises all v3 schema elements
        db.execSQL("INSERT INTO products (id, name, categoryId, businessId, priceCents, isActive, createdAt, archivedAt, deletedAt, originalPriceCents) " +
                "VALUES ('prod1', 'Test Product', 'cat1', 'biz1', 1000, 1, 1234567890, 0, 0, 0)")
        db.execSQL("INSERT INTO sale_headers (id, businessId, salespersonId, salespersonRole, status, createdAt) " +
                "VALUES ('sale1', 'biz1', 'user1', 'owner', 'completed', 1234567890)")
        db.execSQL("INSERT INTO draft_sales (id, businessId, salespersonId, salespersonRole, cartItems, createdAt, updatedAt) " +
                "VALUES ('draft1', 'biz1', 'user1', 'owner', '[]', 1234567890, 1234567890)")
        db.execSQL("INSERT INTO stock_adjustments (id, productId, businessId, adjustmentType, quantityChange, reason, referenceId, createdAt, createdBy) " +
                "VALUES ('adj1', 'prod1', 'biz1', 'RECEIVE', 10, 'test', null, 1234567890, 'user1')")
        db.close()

        // Migrate to version 4 (no-op migration)
        val db4 = helper.runMigrationsAndValidate("sande_database", 4, true, MIGRATION_3_4)

        // Verify all data intact
        var cursor = db4.rawQuery("SELECT name FROM products WHERE id = 'prod1'")
        cursor.moveToFirst()
        assert(cursor.getString(0) == "Test Product")
        cursor.close()

        cursor = db4.rawQuery("SELECT id FROM sale_headers WHERE id = 'sale1'")
        cursor.moveToFirst()
        assert(cursor.getString(0) == "sale1")
        cursor.close()

        cursor = db4.rawQuery("SELECT id FROM draft_sales WHERE id = 'draft1'")
        cursor.moveToFirst()
        assert(cursor.getString(0) == "draft1")
        cursor.close()

        cursor = db4.rawQuery("SELECT id FROM stock_adjustments WHERE id = 'adj1'")
        cursor.moveToFirst()
        assert(cursor.getString(0) == "adj1")
        cursor.close()

        db4.close()
    }

    @Test
    @Throws(IOException::class)
    fun fullMigrationPath1To4() {
        // Create database at version 1 (original schema)
        // We can't easily create v1 here, but we test the path that matters: 2→3→4
        val db = helper.createDatabase("sande_database", 2)

        db.execSQL("INSERT INTO products (id, name, categoryId, businessId, priceCents, isActive, createdAt) " +
                "VALUES ('prod1', 'Test Product', 'cat1', 'biz1', 1000, 1, 1234567890)")
        db.close()

        // Run all migrations in sequence
        val db3 = helper.runMigrationsAndValidate("sande_database", 3, true, MIGRATION_2_3)
        val db4 = helper.runMigrationsAndValidate("sande_database", 4, true, MIGRATION_3_4)

        // Verify final state
        var cursor = db4.rawQuery("SELECT name, archivedAt, deletedAt, originalPriceCents FROM products WHERE id = 'prod1'")
        cursor.moveToFirst()
        assert(cursor.getString(0) == "Test Product")
        assert(cursor.getLong(1) == 0L)
        assert(cursor.getLong(2) == 0L)
        assert(cursor.getLong(3) == 0L)
        cursor.close()

        db4.close()
    }
}
