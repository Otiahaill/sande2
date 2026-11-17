package com.example.sande.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sande.data.entities.Product
import com.example.sande.data.repository.ProductRepository
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

    private val _priceCentsStr = MutableStateFlow("")
    val priceCentsStr = _priceCentsStr.asStateFlow()

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
                _priceCentsStr.value = product.priceCents.toString()
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

    fun setPriceCentsStr(value: String) {
        _priceCentsStr.value = value
    }

    val isFormValid: Boolean
        get() = _name.value.isNotBlank() && _priceCentsStr.value.isNotBlank() && _priceCentsStr.value.toLongOrNull() != null

    /**
     * Writes the product. The caller watches [saved] and only navigates away
     * once the write has actually completed, so the insert is never cancelled
     * by the screen being popped.
     */
    fun saveProduct(businessId: String) {
        if (!isFormValid) return

        viewModelScope.launch {
            val priceCents = _priceCentsStr.value.toLongOrNull() ?: 0L
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
