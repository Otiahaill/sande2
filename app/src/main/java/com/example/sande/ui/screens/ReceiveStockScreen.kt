package com.example.sande.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.material3.Divider
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBar
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.*
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.sande.data.repository.AuthRepository
import com.example.sande.di.SandeApplication
import com.example.sande.ui.ScreenHeader
import com.example.sande.ui.theme.SandeTheme
import com.example.sande.ui.viewModelFactoryOf
import com.example.sande.ui.viewmodel.StockViewModel
import kotlinx.coroutines.launch
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ArrowBack

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiveStockScreen(
    authRepository: AuthRepository,
    modifier: Modifier = Modifier,
    onOpenDrawer: () -> Unit = {},
    onReceived: () -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    val vm: StockViewModel = viewModel(
        factory = viewModelFactoryOf {
            SandeApplication().let { app ->
                StockViewModel(
                    movementRepository = app.movementRepository,
                    productRepository = app.productRepository,
                    stockAdjustmentRepository = app.stockAdjustmentRepository
                )
            }
        }
    )

    var searchQuery by rememberSaveable { mutableStateOf("") }
    var selectedProductId by rememberSaveable { mutableStateOf<String?>(null) }
    var quantityStr by rememberSaveable { mutableStateOf("") }

    val products by vm.products.collectAsState()
    val filteredProducts = if (searchQuery.isBlank()) {
        products
    } else {
        products.filter {
            it.name.contains(searchQuery, ignoreCase = true) ||
            (it.sku?.contains(searchQuery, ignoreCase = true) ?: false)
        }
    }

    val session by authRepository.session.collectAsState()
    val currentUserId = session?.uid ?: return // Not signed in

    fun receiveStock() {
        val qty = quantityStr.toLongOrNull() ?: return
        val productId = selectedProductId ?: return
        if (qty > 0L) {
            scope.launch {
                vm.receive(productId, qty, currentUserId)
                selectedProductId = null
                quantityStr = ""
                onReceived()
            }
        }
    }

    SandeTheme {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(text = "Receive Stock") },
                    navigationIcon = {
                        IconButton(onClick = { onOpenDrawer() }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        IconButton(onClick = { onOpenDrawer() }) {
                            Icon(Icons.Default.Menu, contentDescription = "Menu")
                        }
                    }
                )
            },
            floatingActionButton = {
                ExtendedFloatingActionButton(
                    text = { Text("Record In") },
                    icon = { Icon(Icons.Default.ArrowBack, contentDescription = "Record") },
                    onClick = { receiveStock() },
                    enabled = selectedProductId != null && quantityStr.isNotBlank(),
                    modifier = Modifier.padding(16.dp)
                )
            }
        ) { paddingValues ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues)
                    ) {
                        // Product search/picker
                        Text(
                            text = "Select Product",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                        
                        SearchBar(
                            query = searchQuery,
                            onQueryChange = { searchQuery = it },
                            onSearch = {},
                            placeholder = { Text("Search products...") },
                            enabled = true,
                            content = {
                                if (filteredProducts.isEmpty()) {
                                    Text(
                                        text = "No products found",
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.padding(16.dp)
                                    )
                                } else {
                                    LazyColumn {
                                        items(filteredProducts) { product ->
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable { selectedProductId = product.id }
                                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(product.name, style = MaterialTheme.typography.bodyMedium)
                                                        if (!product.sku.isNullOrBlank()) {
                                                            Text("SKU: ${product.sku}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                        }
                                                    }
                                                    RadioButton(
                                                        selected = selectedProductId == product.id,
                                                        onClick = { selectedProductId = product.id }
                                                    )
                                                }
                                                Divider()
                                            }
                                        }
                                    }
                                }
                            }
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        // Quantity input
                        Text(
                            text = "Quantity",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                        OutlinedTextField(
                            value = quantityStr,
                            onValueChange = { quantityStr = it.filter { c -> c.isDigit() } },
                            label = { Text("Quantity to receive") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            shape = RoundedCornerShape(8.dp)
                        )
                        
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        )
    }
}
