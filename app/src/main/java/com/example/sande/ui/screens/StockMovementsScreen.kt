package com.example.sande.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardOptions
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.sande.data.entities.StockMovement
import com.example.sande.di.SandeApplication
import com.example.sande.ui.ScreenHeader
import com.example.sande.ui.theme.SandeTheme
import com.example.sande.ui.viewModelFactoryOf
import com.example.sande.ui.viewmodel.StockMovementsViewModel
import kotlinx.coroutines.launch
import androidx.compose.material.icons.filled.Menu
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StockMovementsScreen(
    modifier: Modifier = Modifier,
    onOpenDrawer: () -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as SandeApplication
    val vm: StockMovementsViewModel = viewModel(
        factory = viewModelFactoryOf { StockMovementsViewModel(app.stockMovementRepository) }
    )

    val movements by vm.movements.collectAsState()
    val isLoading by vm.isLoading.collectAsState()
    val filterType by vm.filterType.collectAsState()
    val searchQuery by vm.searchQuery.collectAsState()
    var searchText by remember { mutableStateOf("") }

    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Stock Movements") },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(Icons.Filled.Menu, contentDescription = "Open drawer")
                    }
                },
                actions = {
                    OutlinedTextField(
                        value = searchText,
                        onValueChange = { searchText = it },
                        label = { Text("Search...") },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = "Search") },
                        keyboardOptions = KeyboardOptions.Default,
                        visualTransformation = VisualTransformation.None,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                            .width(300.dp),
                        singleLine = true,
                        onKeyboardAction = { _ ->
                            vm.search(searchText)
                        }
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            // Filter chips
            androidx.compose.foundation.layout.Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(label = "All", selected = filterType == "ALL", onClick = { vm.setFilter("ALL") })
                FilterChip(label = "RECEIVE", selected = filterType == "RECEIVE", onClick = { vm.setFilter("RECEIVE") })
                FilterChip(label = "SALE", selected = filterType == "SALE", onClick = { vm.setFilter("SALE") })
                FilterChip(label = "ADJUST", selected = filterType == "ADJUST", onClick = { vm.setFilter("ADJUST") })
                FilterChip(label = "VOID", selected = filterType == "VOID", onClick = { vm.setFilter("VOID") })
            }

            androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(16.dp))

            if (isLoading) {
                androidx.compose.material3.CircularProgressIndicator(
                    modifier = Modifier
                        .fillMaxSize()
                        .wrapContentSize(Alignment.Center)
                )
            } else if (movements.isEmpty()) {
                androidx.compose.material3.Text(
                    text = "No movements found",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxSize()
                        .wrapContentSize(Alignment.Center)
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(movements) { movement ->
                        StockMovementItem(movement = movement)
                    }
                }
            }
        }
    }
}

@Composable
fun FilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    androidx.compose.material3.FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        modifier = Modifier.padding(4.dp)
    )
}

@Composable
fun StockMovementItem(movement: StockMovement) {
    val isPositive = movement.quantityDelta > 0
    val color = if (isPositive) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        androidx.compose.foundation.layout.Column(
            modifier = Modifier.padding(12.dp)
        ) {
            // Header row
            androidx.compose.foundation.layout.Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween
            ) {
                Text(
                    text = movement.movementType,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = color
                )
                Text(
                    text = if (isPositive) "+${movement.quantityDelta}" else movement.quantityDelta.toString(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = color
                )
            }

            androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(4.dp))

            // Product and reference
            androidx.compose.foundation.layout.Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Product: ${movement.productId}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (movement.referenceId != null && movement.referenceId.isNotBlank()) {
                    Text(
                        text = "Ref: ${movement.referenceId}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(4.dp))

            // Actor and device
            androidx.compose.foundation.layout.Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${movement.actorId} (${movement.actorRole}) • ${movement.deviceCode}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = java.text.SimpleDateFormat("MMM dd, HH:mm", java.util.Locale.getDefault())
                        .format(java.util.Date(movement.createdAt)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Reason
            if (movement.reason != null && movement.reason.isNotBlank()) {
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = movement.reason!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
