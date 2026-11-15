package com.example.sande.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sande.data.entities.Product
import com.example.sande.data.repository.MovementRepository
import com.example.sande.data.repository.ProductRepository
import com.example.sande.data.repository.SaleHeaderRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

class SalesViewModel(
    private val movementRepository: MovementRepository,
    productRepository: ProductRepository,
    private val saleHeaderRepository: SaleHeaderRepository
) : ViewModel() {

    data class CartItem(
        val productId: String,
        val productName: String,
        val quantity: Long,
        val unitPriceCents: Long
    )

    val products: StateFlow<List<Product>> =
        productRepository.getAllActive.stateIn(
            viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList()
        )

    private val _cart = MutableStateFlow<List<CartItem>>(emptyList())
    val cart: StateFlow<List<CartItem>> = _cart.asStateFlow()

    fun addToCart(product: Product) {
        _cart.update { items ->
            val existing = items.find { it.productId == product.id }
            if (existing == null) {
                items + CartItem(product.id, product.name, 1L, product.priceCents)
            } else {
                items.map { if (it.productId == product.id) it.copy(quantity = it.quantity + 1) else it }
            }
        }
    }

    fun removeFromCart(productId: String) {
        _cart.update { items -> items.filterNot { it.productId == productId } }
    }

    /**
     * Override the unit price for a cart item line (price override control).
     */
    fun overridePrice(productId: String, newPriceCents: Long) {
        _cart.update { items ->
            items.map { item ->
                if (item.productId == productId) item.copy(unitPriceCents = newPriceCents) else item
            }
        }
    }

    fun subtotalCents(items: List<CartItem>): Long = items.sumOf { it.quantity * it.unitPriceCents }

    /**
     * Atomically records a complete sale: creates SaleHeader + all SALE movements + Outbox entry
     * in one Room transaction. Clears the cart on success.
     */
    suspend fun recordSale(businessId: String) {
        val items = _cart.value
        if (items.isEmpty()) return

        val receiptNumber = saleHeaderRepository.generateReceiptNumber("SAL", businessId)
        val saleLineItems = items.map { line ->
            com.example.sande.data.repository.SaleLineItem(
                productId = line.productId,
                quantity = line.quantity,
                unitPriceCents = line.unitPriceCents
            )
        }
        movementRepository.recordSaleAtomic(saleLineItems, receiptNumber, businessId, "local")

        _cart.value = emptyList()
    }

    /**
     * Voids a previously recorded sale by creating VOID movements for each line item.
     * Clears the cart on success.
     */
    suspend fun voidSale(saleItems: List<CartItem>, referenceId: String, deviceId: String = "local") {
        movementRepository.recordVoid(
            originalMovementId = referenceId,
            productId = saleItems.first().productId,
            quantity = saleItems.sumOf { it.quantity },
            referenceId = referenceId,
            deviceId = deviceId
        )
        _cart.value = emptyList()
    }
}
