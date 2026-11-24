package com.example.sande.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sande.data.AppSettings
import com.example.sande.data.entities.DraftSale
import com.example.sande.data.entities.Product
import com.example.sande.data.repository.CheckoutRepository
import com.example.sande.data.repository.DraftSaleRepository
import com.example.sande.data.repository.ProductRepository
import com.example.sande.data.repository.SaleHeaderRepository
import com.example.sande.data.repository.SaleLineItem
import com.example.sande.data.repository.StockMovementRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import java.util.UUID

class SalesViewModel(
    private val checkoutRepository: CheckoutRepository,
    productRepository: ProductRepository,
    private val draftSaleRepository: DraftSaleRepository,
    private val saleHeaderRepository: SaleHeaderRepository,
    private val stockMovementRepository: StockMovementRepository,
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

    private val draftSaleKey = "default-business:local-owner"

    init {
        loadDraftSale()
    }

    private fun loadDraftSale() {
        viewModelScope.launch {
            // The repository function is already a suspend function, so the
            // draft is read directly — there is no Task to await.
            val draft = draftSaleRepository.getById(draftSaleKey)
            if (draft != null) {
                try {
                    val items = kotlinx.serialization.json.Json.decodeFromString<List<CartItem>>(draft.cartItems)
                    _cart.value = items
                } catch (e: Exception) {
                    // Corrupted draft, ignore
                }
            }
        }
    }

    private fun saveDraftSale(cartItems: List<CartItem>) {
        viewModelScope.launch {
            val json = kotlinx.serialization.json.Json.encodeToString(cartItems)
            val draft = DraftSale(
                id = draftSaleKey,
                businessId = "default-business",
                salespersonId = "local-owner",
                salespersonRole = "owner",
                cartItems = json,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
            draftSaleRepository.insert(draft)
        }
    }

    fun addToCart(product: Product) {
        _cart.update { items ->
            val existing = items.find { it.productId == product.id }
            val newItems = if (existing == null) {
                items + CartItem(product.id, product.name, 1L, product.priceCents)
            } else {
                items.map { if (it.productId == product.id) it.copy(quantity = it.quantity + 1) else it }
            }
            saveDraftSale(newItems)
            newItems
        }
    }

    fun removeFromCart(productId: String) {
        _cart.update { items ->
            val newItems = items.filterNot { it.productId == productId }
            saveDraftSale(newItems)
            newItems
        }
    }

    /**
     * Override the unit price for a cart item line (price override control).
     */
    fun overridePrice(productId: String, newPriceCents: Long) {
        _cart.update { items ->
            val newItems = items.map { item ->
                if (item.productId == productId) item.copy(unitPriceCents = newPriceCents) else item
            }
            saveDraftSale(newItems)
            newItems
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
        deviceCode: String = "LOC",
        actorId: String = "local-owner",
        actorRole: String = "owner",
        confirmedOversell: Boolean = false
    ) {
        if (_isSaving.value || _cart.value.isEmpty()) return
        _isSaving.value = true
        _pendingWarning.value = null
        viewModelScope.launch {
            try {
                val items = _cart.value
                val shortfalls = items.mapNotNull { line ->
                    val balance = stockMovementRepository.getStockBalance(line.productId)
                    if (balance < line.quantity) {
                        "${line.productName}: need ${line.quantity}, available $balance"
                    } else {
                        null
                    }
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
                val receipt = saveSale(items, businessId, salespersonId, salespersonRole, deviceCode, actorId, actorRole)
                _cart.value = emptyList()
                saveDraftSale(emptyList())
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
        salespersonRole: String,
        deviceCode: String,
        actorId: String,
        actorRole: String
    ): String {
        val deviceShort = settings.deviceCode.takeIf { it.isNotBlank() } ?: deviceCode
        val receiptNumber = saleHeaderRepository.generateReceiptNumber("SAL", deviceShort, businessId)
        val lines = items.map { SaleLineItem(it.productId, it.quantity, it.unitPriceCents) }
        val result = checkoutRepository.recordSaleAtomic(
            lines,
            receiptNumber,
            businessId,
            "local-device-id",
            deviceCode,
            salespersonId,
            salespersonRole,
            actorId,
            actorRole
        )
        return result.receiptNumber
    }

    /**
     * Voids a previously recorded sale.
     *
     * The work happens inside [CheckoutRepository.voidSaleAtomic]: the sale
     * session is marked `voided`, a reversing stock movement is written for
     * every line item, and an audit event plus an outbox entry are created so
     * the void reaches the other devices.
     *
     * [referenceId] is the id of the `sale_sessions` row to void. The cart is
     * only cleared when the void actually succeeded.
     *
     * @return true when the sale was voided, false when it was already voided
     *         or could not be found.
     */
    suspend fun voidSale(
        saleItems: List<CartItem>,
        referenceId: String,
        businessId: String = "default-business",
        deviceCode: String = "LOC",
        actorId: String = "local-owner",
        actorRole: String = "owner",
        reason: String = "Voided by user"
    ): Boolean {
        return try {
            val voided = checkoutRepository.voidSaleAtomic(
                sessionId = referenceId,
                businessId = businessId,
                deviceCode = deviceCode,
                actorId = actorId,
                actorRole = actorRole,
                reason = reason
            )
            if (voided) {
                _cart.value = emptyList()
                saveDraftSale(emptyList())
                _notice.value = "Sale voided"
            } else {
                _notice.value = "Sale could not be voided"
            }
            voided
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            _notice.value = "Could not void the sale: ${e.message ?: "unknown error"}"
            false
        }
    }
}
