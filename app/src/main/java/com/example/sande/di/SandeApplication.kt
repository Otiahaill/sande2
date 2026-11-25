package com.example.sande.di

import android.app.Application
import com.example.sande.data.AppSettings
import com.example.sande.data.auth.ConnectionResolver
import com.example.sande.data.auth.GoogleAuthManager
import com.example.sande.data.auth.LegacyAdoptionManager
import com.example.sande.data.auth.SessionManager
import com.example.sande.data.backup.BackupManager
import com.example.sande.data.db.AppDatabase
import com.example.sande.data.prefs.ConnectionSettings
import com.example.sande.data.prefs.TokenManager
import com.example.sande.data.remote.ApiClient
import com.example.sande.data.repository.AuditEventRepository
import com.example.sande.data.repository.AuthRepository
import com.example.sande.data.repository.BusinessProfileRepository
import com.example.sande.data.repository.CheckoutRepository
import com.example.sande.data.repository.CounterRepository
import com.example.sande.data.repository.DeviceRegistrationRepository
import com.example.sande.data.repository.DraftSaleRepository
import com.example.sande.data.repository.MemberRepository
import com.example.sande.data.repository.MembershipMirrorRepository
import com.example.sande.data.repository.MovementRepository
import com.example.sande.data.repository.OutboxEntryRepository
import com.example.sande.data.repository.OutboxRepository
import com.example.sande.data.repository.ProductPriceHistoryRepository
import com.example.sande.data.repository.ProductRepository
import com.example.sande.data.receipt.ReceiptRepository
import com.example.sande.data.repository.SaleHeaderRepository
import com.example.sande.data.repository.SaleItemRepository
import com.example.sande.data.repository.SaleSessionRepository
import com.example.sande.data.repository.StockMovementRepository
import com.example.sande.data.repository.SyncCursorRepository
import com.example.sande.data.repository.SyncExceptionRepository

class SandeApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        // Records any unhandled exception so a startup crash is visible
        // instead of the app disappearing without a trace.
        com.example.sande.ui.StartupCrashHandler.install(this)
    }

    val settings: AppSettings by lazy {
        AppSettings(this)
    }

    val database: AppDatabase by lazy {
        AppDatabase.getDatabase(this)
    }

    // Auth
    val connectionSettings: ConnectionSettings by lazy {
        ConnectionSettings(this)
    }
    val tokenManager: TokenManager by lazy {
        TokenManager(this)
    }
    val connectionResolver: ConnectionResolver by lazy {
        ConnectionResolver(this, connectionSettings, tokenManager, settings)
    }
    val googleAuthManager: GoogleAuthManager by lazy {
        // Client ID resolved at construction; if the user changes it in
        // Connection settings, the app recreates this on next launch.
        GoogleAuthManager(this, connectionResolver.googleClientId())
    }
    val apiClient: ApiClient by lazy {
        ApiClient(this, tokenManager, connectionResolver.apiBaseUrl())
    }
    val sessionManager: SessionManager by lazy {
        SessionManager(this, tokenManager, apiClient)
    }
    val legacyAdoptionManager: LegacyAdoptionManager by lazy {
        LegacyAdoptionManager(this, database, tokenManager, apiClient)
    }
    val authRepository: AuthRepository by lazy {
        AuthRepository()
    }

    // Repositories
    val productRepository: ProductRepository by lazy {
        ProductRepository(database)
    }
    val productPriceHistoryRepository: ProductPriceHistoryRepository by lazy {
        ProductPriceHistoryRepository(database)
    }
    val saleHeaderRepository: SaleHeaderRepository by lazy {
        SaleHeaderRepository(database)
    }
    val saleSessionRepository: SaleSessionRepository by lazy {
        SaleSessionRepository(database)
    }
    val saleItemRepository: SaleItemRepository by lazy {
        SaleItemRepository(database)
    }
    val movementRepository: MovementRepository by lazy {
        MovementRepository(database, productRepository, saleHeaderRepository)
    }
    val stockMovementRepository: StockMovementRepository by lazy {
        StockMovementRepository(database)
    }
    val outboxRepository: OutboxRepository by lazy {
        OutboxRepository(database)
    }
    val outboxEntryRepository: OutboxEntryRepository by lazy {
        OutboxEntryRepository(database)
    }
    val memberRepository: MemberRepository by lazy {
        MemberRepository(database)
    }
    val membershipMirrorRepository: MembershipMirrorRepository by lazy {
        MembershipMirrorRepository(database)
    }
    val deviceRegistrationRepository: DeviceRegistrationRepository by lazy {
        DeviceRegistrationRepository(database)
    }
    val draftSaleRepository: DraftSaleRepository by lazy {
        DraftSaleRepository(database)
    }
    val receiptRepository: ReceiptRepository by lazy {
        ReceiptRepository(database)
    }
    val counterRepository: CounterRepository by lazy {
        CounterRepository(database)
    }
    val businessProfileRepository: BusinessProfileRepository by lazy {
        BusinessProfileRepository(database)
    }
    val auditEventRepository: AuditEventRepository by lazy {
        AuditEventRepository(database)
    }
    val syncCursorRepository: SyncCursorRepository by lazy {
        SyncCursorRepository(database)
    }
    val syncExceptionRepository: SyncExceptionRepository by lazy {
        SyncExceptionRepository(database)
    }
    val checkoutRepository: CheckoutRepository by lazy {
        CheckoutRepository(database, productRepository)
    }
    val backupManager: BackupManager by lazy {
        BackupManager(this, database)
    }
}
