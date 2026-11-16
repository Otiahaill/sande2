package com.example.sande.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.sande.data.daos.MemberDao
import com.example.sande.data.daos.MovementDao
import com.example.sande.data.daos.ProductCategoryDao
import com.example.sande.data.daos.ProductDao
import com.example.sande.data.daos.SaleHeaderDao
import com.example.sande.data.entities.Business
import com.example.sande.data.entities.Member
import com.example.sande.data.entities.Movement
import com.example.sande.data.entities.Product
import com.example.sande.data.entities.ProductCategory
import com.example.sande.data.entities.SaleHeader

@Database(
    entities = [
        Business::class,
        Member::class,
        Product::class,
        ProductCategory::class,
        Movement::class,
        SaleHeader::class
    ],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun productDao(): ProductDao
    abstract fun movementDao(): MovementDao
    abstract fun memberDao(): MemberDao
    abstract fun saleHeaderDao(): SaleHeaderDao
    abstract fun productCategoryDao(): ProductCategoryDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "sande_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
