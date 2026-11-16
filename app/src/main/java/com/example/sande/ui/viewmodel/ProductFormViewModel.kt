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
    private val productToEdit: Product? = null
) : ViewModel() {

    private val _name = MutableStateFlow(productToEdit?.name ?: "")
    val name = _name.asStateFlow()

    private val _sku = MutableStateFlow(productToEdit?.sku ?: "")
    val sku = _sku.asStateFlow()

    private val _category = MutableStateFlow(productToEdit?.category ?: "")
    val category = _category.asStateFlow()

    private val _priceCentsStr = MutableStateFlow(
        if (productToEdit != null) productToEdit.priceCents.toString() else ""
    )
    val priceCentsStr = _priceCentsStr.asStateFlow()

    val isEditMode: Boolean = productToEdit != null

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

    fun saveProduct(businessId: String) {
        if (!isFormValid) return

        viewModelScope.launch {
            val priceCents = _priceCentsStr.value.toLongOrNull() ?: 0L
            val product = Product(
                id = productToEdit?.id ?: UUID.randomUUID().toString(),
                businessId = businessId,
                name = _name.value,
                sku = _sku.value.takeIf { it.isNotBlank() },
                categoryId = null,
                category = _category.value.takeIf { it.isNotBlank() },
                priceCents = priceCents,
                unitOfMeasure = null,
                conversionRatio = null,
                isActive = true,
                createdAt = productToEdit?.createdAt ?: System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
            productRepository.insert(product)
        }
    }
}
