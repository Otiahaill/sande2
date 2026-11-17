package com.example.sande.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.sande.di.SandeApplication
import com.example.sande.ui.ScreenHeader
import com.example.sande.ui.theme.SandeTheme
import com.example.sande.ui.viewModelFactoryOf
import com.example.sande.ui.viewmodel.StockViewModel
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.ExperimentalMaterial3Api

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiveStockScreen(
    modifier: Modifier = Modifier,
    onOpenDrawer: () -> Unit = {},
    onReceived: () -> Unit = {}
) {
    val context = LocalContext.current
    val app = context.applicationContext as SandeApplication
    val vm: StockViewModel = viewModel(
        factory = viewModelFactoryOf { StockViewModel(app.movementRepository, app.productRepository, app.stockAdjustmentRepository) }
    )

    var productId by remember { mutableStateOf("") }
    var quantityStr by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    SandeTheme {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(text = "Receive Stock") },
                    navigationIcon = {
                        IconButton(onClick = { onReceived() }) {
                            Icon(
                                imageVector = Icons.Default.Menu,
                                contentDescription = "Back"
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
                ScreenHeader(
                    title = "Receive Stock",
                    onOpenDrawer = onOpenDrawer,
                    onBack = { onReceived() }
                )
                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = productId,
                    onValueChange = { productId = it },
                    label = { Text("Product ID") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = quantityStr,
                    onValueChange = { quantityStr = it.filter { c -> c.isDigit() } },
                    label = { Text("Quantity") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = {
                        val qty = quantityStr.toLongOrNull() ?: return@Button
                        val id = productId.trim()
                        if (id.isNotBlank() && qty > 0L) {
                            scope.launch {
                                vm.receive(id, qty, "owner-uid")
                                onReceived()
                            }
                        }
                    },
                    enabled = productId.isNotBlank() && quantityStr.isNotBlank(),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = "Record In")
                }
            }
        }
    }
}
