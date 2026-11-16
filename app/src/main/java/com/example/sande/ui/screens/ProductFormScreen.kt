package com.example.sande.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.sande.di.SandeApplication
import com.example.sande.ui.ScreenHeader
import com.example.sande.ui.theme.SandeTheme
import com.example.sande.ui.viewModelFactoryOf
import com.example.sande.ui.viewmodel.ProductFormViewModel

@Composable
fun ProductFormScreen(
    modifier: Modifier = Modifier,
    productId: String? = null,
    onOpenDrawer: () -> Unit = {},
    onProductSaved: (() -> Unit)? = null,
    onCancel: (() -> Unit)? = null
) {
    if (productId == null && onProductSaved == null) return // Nothing to see here

    val context = LocalContext.current
    val app = context.applicationContext as SandeApplication
    val vm: ProductFormViewModel = viewModel(
        factory = viewModelFactoryOf { ProductFormViewModel(app.productRepository, productId) }
    )

    val name by vm.name.collectAsState()
    val sku by vm.sku.collectAsState()
    val category by vm.category.collectAsState()
    val priceCentsStr by vm.priceCentsStr.collectAsState()
    val isEditMode by derivedStateOf { productId != null }
    val saved by vm.saved.collectAsState()

    LaunchedEffect(saved, onProductSaved) {
        if (saved) {
            onProductSaved?.invoke()
        }
    }

    SandeTheme {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(text = if (isEditMode) "Edit Product" else "Add Product") },
                    navigationIcon = {
                        IconButton(onClick = { onCancel?.invoke() }) {
                            Icon(
                                imageVector = Icons.Default.Menu,
                                contentDescription = "Close"
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface
                    )
                )
            }
        ) { innerPadding ->
            Column(
                modifier = modifier.fillMaxSize().padding(innerPadding).padding(16.dp)
            ) {
                ScreenHeader(title = if (isEditMode) "Edit Product" else "Add Product", onOpenDrawer = onOpenDrawer)
                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = name,
                    onValueChange = vm::setName,
                    label = { Text("Product Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = sku,
                    onValueChange = vm::setSku,
                    label = { Text("SKU (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = category,
                    onValueChange = vm::setCategory,
                    label = { Text("Category (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = priceCentsStr,
                    onValueChange = vm::setPriceCentsStr,
                    label = { Text("Price (cents)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions.Default,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = { vm.saveProduct("default-business") },
                    enabled = vm.isFormValid,
                    modifier = Modifier.fillMaxWidth(),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Text(text = if (isEditMode) "Save Changes" else "Add Product")
                }
            }
        }
    }
}
