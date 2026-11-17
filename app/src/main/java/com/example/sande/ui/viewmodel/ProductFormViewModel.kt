package com.example.sande.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sande.data.entities.Product
import com.example.sande.data.repository.ProductRepository
import com.example.sande.ui.formatKes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

class ProductFormViewModel(
    private val productRepository: ProductRepository,
    private val productId: String? = null
) : ViewModel() {

    private val _name = MutableStateFlow("")
    val name = _name.asStateFlow()

    private val _sku = MutableStateFlow("")
    val sku = _sku.asStateFlow()

    private val _category = MutableStateFlow("")
    val category = _category.asStateFlow()

    private val _priceStr = MutableStateFlow("")
    val priceStr = _priceStr.asStateFlow()

    private val _saved = MutableStateFlow(false)
    val saved = _saved.asStateFlow()

    private var loaded: Product? = null

    val isEditMode: Boolean get() = productId != null

    init {
        productId?.let { id ->
            viewModelScope.launch {
                val product = productRepository.getById(id) ?: return@launch
                loaded = product
                _name.value = product.name
                _sku.value = product.sku ?: ""
                _category.value = product.category ?: ""
                _priceStr.value = formatKes(product.priceCents)
            }
        }
    }

    fun setName(value: String) {
        _name.value = value
    }

    fun setSku(value: String) {
        _sku.value = value
    }

    fun setCategory(value: String) {
        _category.value = value
    }

    fun setPriceStr(value: String) {
        _priceStr.value = value
    }

    val isFormValid: Boolean
        get() = _name.value.isNotBlank() && parseCents(_priceStr.value) != null

    /** "220" or "220.50" (shillings) -> cents. Null if invalid or more than 2 decimals. */
    private fun parseCents(input: String): Long? {
        val text = input.trim().replace(",", "")
        if (text.isEmpty()) return null
        return try {
            val value = java.math.BigDecimal(text)
            if (value.signum() < 0 || value.scale() > 2) null else value.movePointRight(2).longValueExact()
        } catch (e: ArithmeticException) {
            null
        } catch (e: NumberFormatException) {
            null
        }
    }

    /**
     * Writes the product. The caller watches [saved] and only navigates away
     * once the write has actually completed, so the insert is never cancelled
     * by the screen being popped.
     */
    fun saveProduct(businessId: String) {
        if (!isFormValid) return

        viewModelScope.launch {
            val priceCents = parseCents(_priceStr.value) ?: return@launch
            val existing = loaded
            val product = Product(
                id = existing?.id ?: UUID.randomUUID().toString(),
                businessId = existing?.businessId ?: businessId,
                name = _name.value.trim(),
                sku = _sku.value.trim().takeIf { it.isNotBlank() },
                categoryId = existing?.categoryId,
                category = _category.value.trim().takeIf { it.isNotBlank() },
                priceCents = priceCents,
                unitOfMeasure = existing?.unitOfMeasure,
                conversionRatio = existing?.conversionRatio,
                isActive = existing?.isActive ?: true,
                originalPriceCents = existing?.originalPriceCents ?: priceCents,
                createdAt = existing?.createdAt ?: System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
            productRepository.insert(product)
            _saved.value = true
        }
    }
}
