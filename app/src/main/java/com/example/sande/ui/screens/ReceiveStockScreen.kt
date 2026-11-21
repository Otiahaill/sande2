package com.example.sande.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.sande.di.SandeApplication
import com.example.sande.ui.ScreenHeader
import com.example.sande.ui.theme.SandeTheme
import com.example.sande.ui.viewModelFactoryOf
import com.example.sande.ui.viewmodel.StockViewModel
import kotlinx.coroutines.launch

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
        factory = viewModelFactoryOf { StockViewModel(app.movementRepository, app.productRepository) }
    )
    val rows by vm.rows.collectAsState()
    val session by app.authRepository.session.collectAsState()

    var selectedId by remember { mutableStateOf<String?>(null) }
    var quantityStr by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val selected = rows.firstOrNull { it.product.id == selectedId }
    val qty = quantityStr.toLongOrNull() ?: 0L

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

            if (rows.isEmpty()) {
                Text(
                    text = "Add a product in the Catalogue first, then come back to receive stock.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded }
                ) {
                    OutlinedTextField(
                        value = selected?.product?.name ?: "",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Product") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        rows.forEach { row ->
                            DropdownMenuItem(
                                text = { Text("${row.product.name}  (in stock: ${row.balance})") },
                                onClick = {
                                    selectedId = row.product.id
                                    expanded = false
                                }
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = quantityStr,
                    onValueChange = { quantityStr = it.filter { c -> c.isDigit() }.take(9) },
                    label = { Text("Quantity received") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                if (selected != null && qty > 0L) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "New stock level: ${selected.balance + qty}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                error?.let {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                }

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = {
                        val id = selectedId ?: return@Button
                        if (saving || qty <= 0L) return@Button
                        saving = true
                        error = null
                        scope.launch {
                            try {
                                vm.receive(
                                    productId = id,
                                    quantity = qty,
                                    deviceCode = app.connectionResolver.deviceCode(),
                                    actorId = session?.uid ?: "local-owner",
                                    actorRole = session?.role ?: "owner"
                                )
                                onReceived()
                            } catch (e: kotlinx.coroutines.CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                error = "Could not save: ${e.message ?: "unknown error"}"
                            } finally {
                                saving = false
                            }
                        }
                    },
                    enabled = selectedId != null && qty > 0L && !saving,
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = if (saving) "Saving…" else "Record In")
                }
            }
        }
    }
}
