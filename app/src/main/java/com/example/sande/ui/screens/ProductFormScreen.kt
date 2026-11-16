package com.example.sande.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
    val context = LocalContext.current
    val app = context.applicationContext as SandeApplication
    val vm: ProductFormViewModel = viewModel(
        factory = viewModelFactoryOf { ProductFormViewModel(app.productRepository, productId) }
    )

    val name by vm.name.collectAsState()
    val sku by vm.sku.collectAsState()
    val category by vm.category.collectAsState()
    val priceCentsStr by vm.priceCentsStr.collectAsState()
    val saved by vm.saved.collectAsState()

    LaunchedEffect(saved) {
        if (saved) onProductSaved?.invoke()
    }

    SandeTheme {
        Column(
            modifier = modifier.fillMaxSize().padding(16.dp)
        ) {
            ScreenHeader(
                title = if (productId == null) "Add Product" else "Edit Product",
                onOpenDrawer = onOpenDrawer,
                onBack = { onCancel?.invoke() }
            )
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
                Text(text = if (productId == null) "Add Product" else "Save Changes")
            }
        }
    }
}
