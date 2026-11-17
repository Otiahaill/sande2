package com.example.sande.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.sande.data.daos.MemberDao
import com.example.sande.data.daos.MovementDao
import com.example.sande.data.daos.OutboxDao
import com.example.sande.data.daos.ProductCategoryDao
import com.example.sande.data.daos.ProductDao
import com.example.sande.data.daos.SaleHeaderDao
import com.example.sande.data.daos.DraftSaleDao
import com.example.sande.data.daos.StockAdjustmentDao
import com.example.sande.data.entities.Business
import com.example.sande.data.entities.DraftSale
import com.example.sande.data.entities.Member
import com.example.sande.data.entities.Movement
import com.example.sande.data.entities.Outbox
import com.example.sande.data.entities.Product
import com.example.sande.data.entities.ProductCategory
import com.example.sande.data.entities.SaleHeader
import com.example.sande.data.entities.StockAdjustment

@Database(
    entities = [
        Business::class,
        Member::class,
        Product::class,
        ProductCategory::class,
        Movement::class,
        Outbox::class,
        SaleHeader::class,
        DraftSale::class,
        StockAdjustment::class
    ],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun productDao(): ProductDao
    abstract fun movementDao(): MovementDao
    abstract fun outboxDao(): OutboxDao
    abstract fun memberDao(): MemberDao
    abstract fun saleHeaderDao(): SaleHeaderDao
    abstract fun productCategoryDao(): ProductCategoryDao
    abstract fun draftSaleDao(): DraftSaleDao
    abstract fun stockAdjustmentDao(): StockAdjustmentDao

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

                // Create stock_adjustments table
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

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "sande_database"
                )
                    .addMigrations(MIGRATION_2_3)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
