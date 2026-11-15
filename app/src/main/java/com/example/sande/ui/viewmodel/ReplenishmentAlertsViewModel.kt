package com.example.sande.ui.viewmodel

import androidx.lifecycle.ViewModel
import com.example.sande.data.entities.Movement
import com.example.sande.data.entities.Product
import com.example.sande.data.repository.MovementRepository
import com.example.sande.data.repository.ProductRepository
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import androidx.lifecycle.viewModelScope

class ReplenishmentAlertsViewModel(
    private val movementRepository: MovementRepository,
    private val productRepository: ProductRepository
) : ViewModel() {

    val lowStockItems: StateFlow<List<StockAlert>> =
        combine(
            productRepository.getAllActive,
            movementRepository.getAll
        ) { products, movements ->
            val balances = movements.groupBy { it.productId }.mapValues { entry ->
                entry.value.sumOf { m ->
                    when (m.type) {
                        com.example.sande.data.entities.MovementType.RECEIVE -> m.quantity
                        com.example.sande.data.entities.MovementType.SALE -> -m.quantity
                        com.example.sande.data.entities.MovementType.ADJUST -> m.quantity
                        com.example.sande.data.entities.MovementType.VOID -> m.quantity
                    }
                }
            }
            products.filter { product ->
                val stock = balances[product.id] ?: 0L
                stock <= 5L // Low stock threshold
            }.map { product ->
                StockAlert(product, balances[product.id] ?: 0L)
            }
        }.stateIn(
            viewModelScope,
            kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5_000),
            emptyList()
        )
}

data class StockAlert(
    val product: Product,
    val balance: Long
)
