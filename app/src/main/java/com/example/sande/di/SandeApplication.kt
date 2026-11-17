package com.example.sande.di

import android.app.Application
import com.example.sande.data.AppSettings
import com.example.sande.data.db.AppDatabase
import com.example.sande.data.repository.AuthRepository
import com.example.sande.data.repository.DraftSaleRepository
import com.example.sande.data.repository.MemberRepository
import com.example.sande.data.repository.MovementRepository
import com.example.sande.data.repository.OutboxRepository
import com.example.sande.data.repository.ProductRepository
import com.example.sande.data.repository.SaleHeaderRepository
import com.example.sande.data.repository.StockAdjustmentRepository

class SandeApplication : Application() {
    val settings: AppSettings by lazy {
        AppSettings(this)
    }

    val database: AppDatabase by lazy {
        AppDatabase.getDatabase(this)
    }

    val productRepository: ProductRepository by lazy {
        ProductRepository(database)
    }
    val saleHeaderRepository: SaleHeaderRepository by lazy {
        SaleHeaderRepository(database)
    }
    val movementRepository: MovementRepository by lazy {
        MovementRepository(database, productRepository, saleHeaderRepository)
    }
    val outboxRepository: OutboxRepository by lazy {
        OutboxRepository(database)
    }
    val authRepository: AuthRepository by lazy {
        AuthRepository()
    }
    val memberRepository: MemberRepository by lazy {
        MemberRepository(database)
    }
    val draftSaleRepository: DraftSaleRepository by lazy {
        DraftSaleRepository(database)
    }
    val stockAdjustmentRepository: StockAdjustmentRepository by lazy {
        StockAdjustmentRepository(database)
    }
}
