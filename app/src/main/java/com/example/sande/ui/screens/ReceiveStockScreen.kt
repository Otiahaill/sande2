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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.sande.di.SandeApplication
import com.example.sande.ui.ScreenHeader
import com.example.sande.ui.theme.SandeTheme
import com.example.sande.ui.viewModelFactoryOf
import com.example.sande.ui.viewmodel.StockViewModel

@Composable
fun ReceiveStockScreen(
    modifier: Modifier = Modifier,
    onOpenDrawer: () -> Unit = {},
    onReceived: () -> Unit = {}
) {
    val context = LocalContext.current
    val app = context.applicationContext as SandeApplication
    val vm: StockViewModel = viewModel(
        factory = viewModelFactoryOf { StockViewModel(app.movementRepository, app.productRepository) }
    )

    var productId by remember { mutableStateOf("") }
    var quantityStr by remember { mutableStateOf("") }

    SandeTheme {
        Column(
            modifier = modifier.fillMaxSize().padding(16.dp)
        ) {
            ScreenHeader(
                title = "Receive Stock",
                onOpenDrawer = onOpenDrawer,
                onBack = { onReceived() }
            )
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Record stock coming in",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
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
                    val qty = quantityStr.toLongOrNull() ?: 0L
                    val id = productId.trim()
                    if (id.isNotBlank() && qty > 0L) {
                        vm.receive(id, qty)
                        onReceived()
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
