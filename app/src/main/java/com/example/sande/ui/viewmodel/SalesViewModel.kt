package com.example.sande.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sande.data.AppSettings
import com.example.sande.data.entities.Product
import com.example.sande.data.repository.MovementRepository
import com.example.sande.data.repository.ProductRepository
import com.example.sande.data.repository.SaleHeaderRepository
import com.example.sande.data.repository.SaleLineItem
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
    private val saleHeaderRepository: SaleHeaderRepository,
    private val settings: AppSettings
) : ViewModel() {

    data class CartItem(
        val productId: String,
        val productName: String,
        val quantity: Long,
        val unitPriceCents: Long
    )

    val products: StateFlow<List<Product>> =
        productRepository.getAllActiveByBusiness("default-business").stateIn(
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

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    /** Set when the policy is WARN and the cart exceeds stock; the UI asks the user to confirm. */
    private val _pendingWarning = MutableStateFlow<String?>(null)
    val pendingWarning: StateFlow<String?> = _pendingWarning.asStateFlow()

    /** One-off message for the UI (success, blocked, or error). */
    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice.asStateFlow()

    fun dismissWarning() { _pendingWarning.value = null }
    fun consumeNotice() { _notice.value = null }

    /**
     * Checks stock against the negative-stock policy, then records the sale atomically.
     * Ignored while another save is running, so a double tap cannot record two sales.
     */
    fun checkout(
        businessId: String,
        salespersonId: String,
        salespersonRole: String,
        confirmedOversell: Boolean = false
    ) {
        if (_isSaving.value || _cart.value.isEmpty()) return
        _isSaving.value = true
        _pendingWarning.value = null
        viewModelScope.launch {
            try {
                val items = _cart.value
                val shortfalls = items.mapNotNull { line ->
                    val balance = movementRepository.getStockBalance(line.productId)
                    if (balance < line.quantity) {
                        "${line.productName}: ${balance.coerceAtLeast(0L)} in stock, ${line.quantity} in cart"
                    } else null
                }
                if (shortfalls.isNotEmpty()) {
                    val detail = shortfalls.joinToString("\n")
                    when (settings.negativeStockPolicy) {
                        AppSettings.POLICY_BLOCK -> {
                            _notice.value = "Not enough stock:\n$detail"
                            return@launch
                        }
                        AppSettings.POLICY_WARN -> if (!confirmedOversell) {
                            _pendingWarning.value = detail
                            return@launch
                        }
                    }
                }
                val receipt = saveSale(items, businessId, salespersonId, salespersonRole)
                _cart.value = emptyList()
                _notice.value = "Sale recorded: $receipt"
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _notice.value = "Could not record sale: ${e.message ?: "unknown error"}"
            } finally {
                _isSaving.value = false
            }
        }
    }

    private suspend fun saveSale(
        items: List<CartItem>,
        businessId: String,
        salespersonId: String,
        salespersonRole: String
    ): String {
        val receiptNumber = saleHeaderRepository.generateReceiptNumber("SAL", businessId)
        val lines = items.map { SaleLineItem(it.productId, it.quantity, it.unitPriceCents) }
        movementRepository.recordSaleAtomic(lines, receiptNumber, businessId, "local", salespersonId, salespersonRole)
        return receiptNumber
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
