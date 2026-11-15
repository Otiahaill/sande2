package com.example.sande.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sande.data.entities.MovementType
import com.example.sande.data.entities.Product
import com.example.sande.data.repository.MovementRepository
import com.example.sande.data.repository.ProductRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class StockViewModel(
    private val movementRepository: MovementRepository,
    productRepository: ProductRepository
) : ViewModel() {

    data class StockRow(val product: Product, val balance: Long)

    val rows: StateFlow<List<StockRow>> =
        combine(productRepository.getAllActive, movementRepository.getAll) { products, movements ->
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

    fun receive(productId: String, quantity: Long) {
        if (quantity <= 0L) return
        viewModelScope.launch {
            movementRepository.recordReceive(productId, quantity, null, null, "local")
        }
    }
}
