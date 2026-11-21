package com.example.sande.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.sande.data.daos.AuditEventDao
import com.example.sande.data.daos.BusinessDao
import com.example.sande.data.daos.BusinessProfileDao
import com.example.sande.data.daos.CounterDao
import com.example.sande.data.daos.DeviceRegistrationDao
import com.example.sande.data.daos.DraftSaleDao
import com.example.sande.data.daos.MemberDao
import com.example.sande.data.daos.MembershipMirrorDao
import com.example.sande.data.daos.MovementDao
import com.example.sande.data.daos.OutboxDao
import com.example.sande.data.daos.OutboxEntryDao
import com.example.sande.data.daos.ProductCategoryDao
import com.example.sande.data.daos.ProductDao
import com.example.sande.data.daos.ProductPriceHistoryDao
import com.example.sande.data.daos.ReceiptDao
import com.example.sande.data.daos.SaleHeaderDao
import com.example.sande.data.daos.SaleItemDao
import com.example.sande.data.daos.SaleSessionDao
import com.example.sande.data.daos.StockMovementDao
import com.example.sande.data.daos.SyncCursorDao
import com.example.sande.data.daos.SyncExceptionDao
import com.example.sande.data.entities.AuditEvent
import com.example.sande.data.entities.Business
import com.example.sande.data.entities.BusinessProfile
import com.example.sande.data.entities.Counter
import com.example.sande.data.entities.DeviceRegistration
import com.example.sande.data.entities.DraftSale
import com.example.sande.data.entities.Member
import com.example.sande.data.entities.MembershipMirror
import com.example.sande.data.entities.Movement
import com.example.sande.data.entities.Outbox
import com.example.sande.data.entities.OutboxEntry
import com.example.sande.data.entities.Product
import com.example.sande.data.entities.ProductCategory
import com.example.sande.data.entities.ProductPriceHistory
import com.example.sande.data.entities.Receipt
import com.example.sande.data.entities.SaleHeader
import com.example.sande.data.entities.SaleItem
import com.example.sande.data.entities.SaleSession
import com.example.sande.data.entities.StockMovement
import com.example.sande.data.entities.SyncCursor
import com.example.sande.data.entities.SyncException

@Database(
    entities = [
        Business::class,
        BusinessProfile::class,
        Member::class,
        MembershipMirror::class,
        DeviceRegistration::class,
        Product::class,
        ProductCategory::class,
        ProductPriceHistory::class,
        Movement::class, // Legacy - kept for migration
        StockMovement::class,
        Outbox::class, // Legacy - kept for migration
        OutboxEntry::class,
        SaleHeader::class, // Legacy - kept for migration
        SaleSession::class,
        SaleItem::class,
        DraftSale::class,
        Receipt::class,
        Counter::class,
        AuditEvent::class,
        SyncCursor::class,
        SyncException::class
    ],
    version = 6,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun productDao(): ProductDao
    abstract fun productCategoryDao(): ProductCategoryDao
    abstract fun productPriceHistoryDao(): ProductPriceHistoryDao
    abstract fun businessDao(): BusinessDao
    abstract fun movementDao(): MovementDao // Legacy
    abstract fun stockMovementDao(): StockMovementDao
    abstract fun outboxDao(): OutboxDao // Legacy
    abstract fun outboxEntryDao(): OutboxEntryDao
    abstract fun memberDao(): MemberDao
    abstract fun membershipMirrorDao(): MembershipMirrorDao
    abstract fun deviceRegistrationDao(): DeviceRegistrationDao
    abstract fun saleHeaderDao(): SaleHeaderDao // Legacy
    abstract fun saleSessionDao(): SaleSessionDao
    abstract fun saleItemDao(): SaleItemDao
    abstract fun draftSaleDao(): DraftSaleDao
    abstract fun receiptDao(): ReceiptDao
    abstract fun counterDao(): CounterDao
    abstract fun businessProfileDao(): BusinessProfileDao
    abstract fun auditEventDao(): AuditEventDao
    abstract fun syncCursorDao(): SyncCursorDao
    abstract fun syncExceptionDao(): SyncExceptionDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // New columns must be NOT NULL to match the entity definitions
                database.execSQL("ALTER TABLE products ADD COLUMN archivedAt INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE products ADD COLUMN deletedAt INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE products ADD COLUMN originalPriceCents INTEGER NOT NULL DEFAULT 0")

                database.execSQL("ALTER TABLE sale_headers ADD COLUMN salespersonId TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE sale_headers ADD COLUMN salespersonRole TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE sale_headers ADD COLUMN status TEXT NOT NULL DEFAULT 'completed'")

                // Index names must be exactly what Room generates: index_<table>_<column>
                listOf("businessId", "categoryId", "isActive", "archivedAt", "deletedAt").forEach { col ->
                    database.execSQL("CREATE INDEX IF NOT EXISTS index_products_$col ON products($col)")
                }
                listOf("salespersonId", "status").forEach { col ->
                    database.execSQL("CREATE INDEX IF NOT EXISTS index_sale_headers_$col ON sale_headers($col)")
                }

                // Create draft_sales table
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS draft_sales (
                        id TEXT PRIMARY KEY NOT NULL,
                        businessId TEXT NOT NULL,
                        salespersonId TEXT NOT NULL,
                        salespersonRole TEXT NOT NULL,
                        cartItems TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                """)

                // Create stock_adjustments table (legacy, will be retired in v5)
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS stock_adjustments (
                        id TEXT PRIMARY KEY NOT NULL,
                        productId TEXT NOT NULL,
                        businessId TEXT NOT NULL,
                        adjustmentType TEXT NOT NULL,
                        quantityChange INTEGER NOT NULL,
                        reason TEXT,
                        referenceId TEXT,
                        createdAt INTEGER NOT NULL,
                        createdBy TEXT NOT NULL
                    )
                """)
                listOf("productId", "businessId", "createdAt").forEach { col ->
                    database.execSQL("CREATE INDEX IF NOT EXISTS index_stock_adjustments_$col ON stock_adjustments($col)")
                }
            }
        }

        // Migration 3→4: no schema changes, exists to test migration path and remove destructive fallback
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // No-op: schema unchanged. This migration exists solely to exercise the migration path
                // and to allow removing fallbackToDestructiveMigration().
            }
        }

        // Migration 4→5: Create new ledger tables (stock_movements, sale_sessions, sale_items, receipts, counters, audit_events, etc.)
        // and migrate data from legacy tables
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Create business_profiles table
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS business_profiles (
                        id TEXT PRIMARY KEY NOT NULL,
                        name TEXT NOT NULL,
                        category TEXT NOT NULL,
                        phone TEXT,
                        countryCode TEXT NOT NULL DEFAULT 'KE',
                        currencyCode TEXT NOT NULL DEFAULT 'KES',
                        timezone TEXT NOT NULL DEFAULT 'Africa/Nairobi',
                        address TEXT,
                        logoUri TEXT,
                        negativeStockPolicy TEXT NOT NULL DEFAULT 'warn',
                        receiptFooter TEXT,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        deletedAt INTEGER NOT NULL DEFAULT 0,
                        schemaVersion INTEGER NOT NULL DEFAULT 1
                    )
                """)

                // Create stock_movements table
                // The FOREIGN KEY clause must mirror StockMovement's @Entity foreignKeys,
                // otherwise Room's schema validation rejects the migrated database.
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS stock_movements (
                        id TEXT PRIMARY KEY NOT NULL,
                        productId TEXT NOT NULL,
                        businessId TEXT NOT NULL,
                        movementType TEXT NOT NULL,
                        quantityDelta INTEGER NOT NULL,
                        unitPriceCents INTEGER,
                        actorId TEXT NOT NULL,
                        actorRole TEXT NOT NULL,
                        deviceCode TEXT NOT NULL,
                        reason TEXT,
                        referenceId TEXT,
                        reversalOf TEXT,
                        metadataJson TEXT,
                        createdAt INTEGER NOT NULL,
                        synced INTEGER NOT NULL DEFAULT 0,
                        FOREIGN KEY(productId) REFERENCES products(id) ON DELETE RESTRICT
                    )
                """)
                listOf("productId", "businessId", "actorId", "createdAt", "synced", "referenceId").forEach { col ->
                    database.execSQL("CREATE INDEX IF NOT EXISTS index_stock_movements_$col ON stock_movements($col)")
                }

                // Create sale_sessions table
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS sale_sessions (
                        id TEXT PRIMARY KEY NOT NULL,
                        businessId TEXT NOT NULL,
                        receiptNumber TEXT NOT NULL,
                        deviceId TEXT NOT NULL,
                        deviceCode TEXT NOT NULL,
                        salespersonId TEXT NOT NULL,
                        salespersonRole TEXT NOT NULL,
                        subtotalCents INTEGER NOT NULL,
                        taxCents INTEGER NOT NULL,
                        totalCents INTEGER NOT NULL,
                        status TEXT NOT NULL DEFAULT 'completed',
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        synced INTEGER NOT NULL DEFAULT 0
                    )
                """)
                listOf("businessId", "salespersonId", "deviceId", "createdAt", "status").forEach { col ->
                    database.execSQL("CREATE INDEX IF NOT EXISTS index_sale_sessions_$col ON sale_sessions($col)")
                }

                // Create sale_items table
                // The FOREIGN KEY clauses must mirror SaleItem's @Entity foreignKeys,
                // otherwise Room's schema validation rejects the migrated database.
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS sale_items (
                        id TEXT PRIMARY KEY NOT NULL,
                        sessionId TEXT NOT NULL,
                        productId TEXT NOT NULL,
                        productName TEXT NOT NULL,
                        categoryName TEXT NOT NULL,
                        quantity INTEGER NOT NULL,
                        unitPriceCents INTEGER NOT NULL,
                        totalCents INTEGER NOT NULL,
                        businessId TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        FOREIGN KEY(productId) REFERENCES products(id) ON DELETE RESTRICT,
                        FOREIGN KEY(sessionId) REFERENCES sale_sessions(id) ON DELETE CASCADE
                    )
                """)
                listOf("sessionId", "productId", "businessId").forEach { col ->
                    database.execSQL("CREATE INDEX IF NOT EXISTS index_sale_items_$col ON sale_items($col)")
                }

                // Create receipts table
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS receipts (
                        id TEXT PRIMARY KEY NOT NULL,
                        sessionId TEXT NOT NULL,
                        businessId TEXT NOT NULL,
                        deviceCode TEXT NOT NULL,
                        counter INTEGER NOT NULL,
                        receiptNumber TEXT NOT NULL,
                        format TEXT NOT NULL DEFAULT 'thermal_58mm',
                        contentHash TEXT NOT NULL,
                        createdAt INTEGER NOT NULL
                    )
                """)
                listOf("sessionId", "businessId", "deviceCode", "counter", "createdAt").forEach { col ->
                    database.execSQL("CREATE INDEX IF NOT EXISTS index_receipts_$col ON receipts($col)")
                }

                // Create counters table
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS counters (
                        id TEXT PRIMARY KEY NOT NULL,
                        businessId TEXT NOT NULL,
                        deviceCode TEXT NOT NULL,
                        counterType TEXT NOT NULL,
                        currentValue INTEGER NOT NULL DEFAULT 0,
                        updatedAt INTEGER NOT NULL,
                        UNIQUE(businessId, deviceCode, counterType)
                    )
                """)

                // Create audit_events table
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS audit_events (
                        id TEXT PRIMARY KEY NOT NULL,
                        businessId TEXT NOT NULL,
                        entityType TEXT NOT NULL,
                        entityId TEXT NOT NULL,
                        action TEXT NOT NULL,
                        actorId TEXT NOT NULL,
                        actorRole TEXT NOT NULL,
                        deviceCode TEXT NOT NULL,
                        severity TEXT NOT NULL DEFAULT 'info',
                        beforeJson TEXT,
                        afterJson TEXT,
                        metadataJson TEXT,
                        createdAt INTEGER NOT NULL,
                        synced INTEGER NOT NULL DEFAULT 0
                    )
                """)
                listOf("businessId", "entityType", "entityId", "actorId", "createdAt", "severity").forEach { col ->
                    database.execSQL("CREATE INDEX IF NOT EXISTS index_audit_events_$col ON audit_events($col)")
                }

                // Create product_price_history table
                // The FOREIGN KEY clause must mirror ProductPriceHistory's @Entity foreignKeys,
                // otherwise Room's schema validation rejects the migrated database.
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS product_price_history (
                        id TEXT PRIMARY KEY NOT NULL,
                        productId TEXT NOT NULL,
                        oldPriceCents INTEGER NOT NULL,
                        newPriceCents INTEGER NOT NULL,
                        changedBy TEXT NOT NULL,
                        reason TEXT,
                        createdAt INTEGER NOT NULL,
                        FOREIGN KEY(productId) REFERENCES products(id) ON DELETE CASCADE
                    )
                """)
                listOf("productId", "createdAt").forEach { col ->
                    database.execSQL("CREATE INDEX IF NOT EXISTS index_product_price_history_$col ON product_price_history($col)")
                }

                // Create membership_mirrors table
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS membership_mirrors (
                        id TEXT PRIMARY KEY NOT NULL,
                        businessId TEXT NOT NULL,
                        accountId TEXT NOT NULL,
                        email TEXT NOT NULL,
                        role TEXT NOT NULL,
                        status TEXT NOT NULL,
                        invitedBy TEXT,
                        invitedAt INTEGER,
                        acceptedAt INTEGER,
                        deviceCode TEXT,
                        lastSyncedAt INTEGER NOT NULL,
                        serverVersion INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                """)
                listOf("businessId", "accountId", "deviceCode", "lastSyncedAt").forEach { col ->
                    database.execSQL("CREATE INDEX IF NOT EXISTS index_membership_mirrors_$col ON membership_mirrors($col)")
                }

                // Create device_registrations table
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS device_registrations (
                        id TEXT PRIMARY KEY NOT NULL,
                        businessId TEXT NOT NULL,
                        accountId TEXT NOT NULL,
                        deviceCode TEXT NOT NULL,
                        deviceName TEXT NOT NULL,
                        deviceType TEXT NOT NULL,
                        status TEXT NOT NULL,
                        approvedBy TEXT,
                        approvedAt INTEGER,
                        revokedAt INTEGER,
                        revokedBy TEXT,
                        lastSeenAt INTEGER NOT NULL,
                        lastSyncAt INTEGER,
                        pushToken TEXT,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                """)
                listOf("businessId", "accountId", "deviceCode", "status").forEach { col ->
                    database.execSQL("CREATE INDEX IF NOT EXISTS index_device_registrations_$col ON device_registrations($col)")
                }

                // Create outbox_entries table
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS outbox_entries (
                        id TEXT PRIMARY KEY NOT NULL,
                        businessId TEXT NOT NULL,
                        entityType TEXT NOT NULL,
                        entityId TEXT NOT NULL,
                        operation TEXT NOT NULL,
                        payloadJson TEXT NOT NULL,
                        batchId TEXT,
                        attemptCount INTEGER NOT NULL DEFAULT 0,
                        lastError TEXT,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        synced INTEGER NOT NULL DEFAULT 0,
                        syncedAt INTEGER NOT NULL DEFAULT 0
                    )
                """)
                listOf("businessId", "entityType", "entityId", "operation", "createdAt", "synced", "batchId").forEach { col ->
                    database.execSQL("CREATE INDEX IF NOT EXISTS index_outbox_entries_$col ON outbox_entries($col)")
                }

                // Create sync_cursors table
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS sync_cursors (
                        id TEXT PRIMARY KEY NOT NULL,
                        businessId TEXT NOT NULL,
                        deviceCode TEXT NOT NULL,
                        cursor INTEGER NOT NULL,
                        lastPullAt INTEGER NOT NULL,
                        lastPushAt INTEGER NOT NULL,
                        pendingPushCount INTEGER NOT NULL DEFAULT 0,
                        updatedAt INTEGER NOT NULL
                    )
                """)

                // Create sync_exceptions table
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS sync_exceptions (
                        id TEXT PRIMARY KEY NOT NULL,
                        businessId TEXT NOT NULL,
                        deviceCode TEXT NOT NULL,
                        exceptionType TEXT NOT NULL,
                        entityType TEXT NOT NULL,
                        entityId TEXT NOT NULL,
                        localVersion INTEGER NOT NULL,
                        serverVersion INTEGER NOT NULL,
                        description TEXT NOT NULL,
                        localDataJson TEXT NOT NULL,
                        serverDataJson TEXT,
                        resolution TEXT,
                        resolvedBy TEXT,
                        resolvedAt INTEGER NOT NULL DEFAULT 0,
                        status TEXT NOT NULL DEFAULT 'open',
                        createdAt INTEGER NOT NULL
                    )
                """)
                listOf("businessId", "deviceCode", "status", "createdAt").forEach { col ->
                    database.execSQL("CREATE INDEX IF NOT EXISTS index_sync_exceptions_$col ON sync_exceptions($col)")
                }

                // MIGRATE DATA FROM LEGACY TABLES
                // 1. Migrate movements -> stock_movements
                // movements has: id, productId, type, quantity, unitPriceCents, referenceId, createdAt, deviceId, synced, businessId
                // stock_movements needs: id, productId, businessId, movementType, quantityDelta, unitPriceCents, actorId, actorRole, deviceCode, reason, referenceId, reversalOf, metadataJson, createdAt, synced
                database.execSQL("""
                    INSERT INTO stock_movements (id, productId, businessId, movementType, quantityDelta, unitPriceCents, actorId, actorRole, deviceCode, reason, referenceId, reversalOf, metadataJson, createdAt, synced)
                    SELECT
                        m.id,
                        m.productId,
                        m.businessId,
                        m.type as movementType,
                        CASE m.type
                            WHEN 'RECEIVE' THEN m.quantity
                            WHEN 'SALE' THEN -m.quantity
                            WHEN 'ADJUST' THEN m.quantity
                            WHEN 'VOID' THEN m.quantity
                            ELSE m.quantity
                        END as quantityDelta,
                        m.unitPriceCents,
                        COALESCE(m.deviceId, 'legacy') as actorId,
                        'unknown' as actorRole,
                        COALESCE(m.deviceId, 'legacy') as deviceCode,
                        CASE m.type
                            WHEN 'RECEIVE' THEN 'Stock received'
                            WHEN 'SALE' THEN 'Sale #' || COALESCE(m.referenceId, '')
                            WHEN 'ADJUST' THEN 'Stock adjustment'
                            WHEN 'VOID' THEN 'Void of #' || COALESCE(m.referenceId, '')
                            ELSE 'Legacy movement'
                        END as reason,
                        m.referenceId,
                        NULL as reversalOf,
                        NULL as metadataJson,
                        m.createdAt,
                        m.synced
                    FROM movements m
                    JOIN products p ON m.productId = p.id
                """)

                // 2. Migrate sale_headers -> sale_sessions
                database.execSQL("""
                    INSERT INTO sale_sessions (id, businessId, receiptNumber, deviceId, deviceCode, salespersonId, salespersonRole, subtotalCents, taxCents, totalCents, status, createdAt, updatedAt, synced)
                    SELECT
                        id,
                        businessId,
                        receiptNumber,
                        deviceId,
                        COALESCE(deviceId, 'legacy') as deviceCode,
                        COALESCE(salespersonId, 'legacy') as salespersonId,
                        COALESCE(salespersonRole, 'owner') as salespersonRole,
                        totalCents as subtotalCents,
                        0 as taxCents,
                        totalCents,
                        COALESCE(status, 'completed') as status,
                        createdAt,
                        createdAt as updatedAt,
                        synced
                    FROM sale_headers
                    WHERE receiptNumber IS NOT NULL AND receiptNumber != ''
                """)

                // 3. Create sale_items from sale_headers + movements
                // For each sale_header, find SALE movements with matching referenceId and create sale_items.
                // sale_items.unitPriceCents / totalCents / categoryName are NOT NULL, but the
                // legacy movements.unitPriceCents is nullable and product_categories is LEFT
                // JOINed — so coalesce them or the INSERT aborts the whole migration.
                database.execSQL("""
                    INSERT INTO sale_items (id, sessionId, productId, productName, categoryName, quantity, unitPriceCents, totalCents, businessId, createdAt)
                    SELECT
                        'si_' || m.id,
                        sh.id as sessionId,
                        m.productId,
                        COALESCE(p.name, 'Unknown product') as productName,
                        COALESCE(pc.name, '') as categoryName,
                        m.quantity,
                        COALESCE(m.unitPriceCents, 0) as unitPriceCents,
                        m.quantity * COALESCE(m.unitPriceCents, 0) as totalCents,
                        m.businessId,
                        m.createdAt
                    FROM movements m
                    JOIN sale_headers sh ON m.referenceId = sh.id
                    JOIN products p ON m.productId = p.id
                    LEFT JOIN product_categories pc ON p.categoryId = pc.id
                    WHERE m.type = 'SALE' AND sh.receiptNumber IS NOT NULL AND sh.receiptNumber != ''
                """)

                // 4. Migrate outbox -> outbox_entries
                // The legacy `outbox` table has columns (id, entityType, entityId, operation,
                // payload, createdAt, attempts, synced) and NO businessId / payloadJson /
                // attemptCount / lastError columns. Selecting the new names here would abort
                // the migration with "no such column". Map old -> new explicitly:
                //   payload    -> payloadJson
                //   attempts   -> attemptCount
                //   businessId -> derived from the legacy business (single-tenant offline app)
                database.execSQL("""
                    INSERT INTO outbox_entries (id, businessId, entityType, entityId, operation, payloadJson, batchId, attemptCount, lastError, createdAt, updatedAt, synced, syncedAt)
                    SELECT
                        id,
                        COALESCE((SELECT b.id FROM businesses b ORDER BY b.createdAt LIMIT 1), 'default-business') as businessId,
                        entityType,
                        entityId,
                        operation,
                        payload as payloadJson,
                        NULL as batchId,
                        attempts as attemptCount,
                        NULL as lastError,
                        createdAt,
                        createdAt as updatedAt,
                        synced,
                        CASE WHEN synced = 1 THEN createdAt ELSE 0 END as syncedAt
                    FROM outbox
                """)

                // 5. Initialize counters for each business/device from existing data
                // Receipt counters
                database.execSQL("""
                    INSERT OR IGNORE INTO counters (id, businessId, deviceCode, counterType, currentValue, updatedAt)
                    SELECT
                        businessId || ':' || COALESCE(deviceId, 'legacy') || ':receipt',
                        businessId,
                        COALESCE(deviceId, 'legacy'),
                        'receipt',
                        COUNT(*) as currentValue,
                        MAX(createdAt) as updatedAt
                    FROM sale_headers
                    WHERE receiptNumber IS NOT NULL AND receiptNumber != ''
                    GROUP BY businessId, deviceId
                """)

                // 6. Create default business_profile from existing business
                database.execSQL("""
                    INSERT OR IGNORE INTO business_profiles (id, name, category, countryCode, currencyCode, timezone, createdAt, updatedAt)
                    SELECT
                        id,
                        name,
                        'retail' as category,
                        'KE' as countryCode,
                        'KES' as currencyCode,
                        'Africa/Nairobi' as timezone,
                        createdAt,
                        createdAt as updatedAt
                    FROM businesses
                """)
            }
        }

        // Migration 5→6: Clean up legacy tables (optional - we keep them for safety but mark deprecated)
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // No destructive changes - legacy tables (movements, sale_headers, stock_adjustments, outbox)
                // are kept for historical reference but no longer written to.
                // New code uses stock_movements, sale_sessions, sale_items, outbox_entries.
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "sande_database"
                )
                    .addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
