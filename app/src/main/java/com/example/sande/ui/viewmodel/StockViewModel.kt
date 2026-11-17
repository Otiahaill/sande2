package com.example.sande.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sande.data.entities.MovementType
import com.example.sande.data.entities.Product
import com.example.sande.data.repository.MovementRepository
import com.example.sande.data.repository.ProductRepository
import com.example.sande.data.repository.StockAdjustmentRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class StockViewModel(
    private val movementRepository: MovementRepository,
    productRepository: ProductRepository,
    private val stockAdjustmentRepository: StockAdjustmentRepository
) : ViewModel() {

    data class StockRow(val product: Product, val balance: Long)

    val rows: StateFlow<List<StockRow>> =
        combine(productRepository.getAllActiveByBusiness("default-business"), movementRepository.getAll) { products, movements ->
            val balances = movements.groupBy { it.productId }.mapValues { entry ->
                entry.value.sumOf { m ->
                    when (m.type) {
                        MovementType.RECEIVE -> m.quantity
                        MovementType.SALE -> -m.quantity
                        MovementType.ADJUST -> m.quantity
                        MovementType.VOID -> m.quantity
                    }
                }
            }
            products.map { StockRow(it, balances[it.id] ?: 0L) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Suspends until both writes are done, so callers only navigate away once stock is saved. */
    suspend fun receive(productId: String, quantity: Long, userId: String) {
        if (quantity <= 0L) return
        movementRepository.recordReceive(productId, quantity, null, null, "local")
        stockAdjustmentRepository.insert(
            com.example.sande.data.entities.StockAdjustment(
                id = java.util.UUID.randomUUID().toString(),
                productId = productId,
                businessId = "default-business",
                adjustmentType = "RECEIVE",
                quantityChange = quantity,
                reason = "Stock received",
                referenceId = null,
                createdAt = System.currentTimeMillis(),
                createdBy = userId
            )
        )
    }
}
