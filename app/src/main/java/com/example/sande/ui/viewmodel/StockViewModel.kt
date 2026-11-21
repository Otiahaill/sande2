package com.example.sande.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sande.data.entities.Product
import com.example.sande.data.repository.ProductRepository
import com.example.sande.data.repository.StockMovementRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class StockViewModel(
    private val stockMovementRepository: StockMovementRepository,
    productRepository: ProductRepository
) : ViewModel() {

    data class StockRow(val product: Product, val balance: Long)

    val rows: StateFlow<List<StockRow>> =
        combine(productRepository.getAllActiveByBusiness("default-business"), stockMovementRepository.observeBalances("default-business")) { products, balances ->
            products.map { StockRow(it, balances[it.id] ?: 0L) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Records a stock receipt as a single RECEIVE stock movement. */
    suspend fun receive(productId: String, quantity: Long, deviceCode: String = "local", actorId: String = "local-owner", actorRole: String = "owner") {
        if (quantity <= 0L) return
        stockMovementRepository.insert(
            com.example.sande.data.entities.StockMovement(
                id = java.util.UUID.randomUUID().toString(),
                productId = productId,
                businessId = "default-business",
                movementType = "RECEIVE",
                quantityDelta = quantity,
                unitPriceCents = null,
                actorId = actorId,
                actorRole = actorRole,
                deviceCode = deviceCode,
                reason = "Stock received",
                referenceId = null,
                reversalOf = null,
                metadataJson = null,
                createdAt = System.currentTimeMillis(),
                synced = false
            )
        )
    }
}
