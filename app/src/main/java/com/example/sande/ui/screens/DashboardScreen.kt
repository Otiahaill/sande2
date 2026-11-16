package com.example.sande.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.sande.di.SandeApplication
import com.example.sande.ui.ScreenHeader
import com.example.sande.ui.formatKes
import com.example.sande.ui.theme.SandeShape
import com.example.sande.ui.theme.SandeTheme
import com.example.sande.ui.viewModelFactoryOf
import com.example.sande.ui.viewmodel.CatalogueViewModel
import com.example.sande.ui.viewmodel.StockViewModel

@Composable
fun DashboardScreen(
    modifier: Modifier = Modifier,
    onOpenDrawer: () -> Unit = {}
) {
    val context = LocalContext.current
    val app = context.applicationContext as SandeApplication
    val catalogueViewModel: CatalogueViewModel = viewModel(
        factory = viewModelFactoryOf { CatalogueViewModel(app.productRepository) }
    )
    val stockViewModel: StockViewModel = viewModel(
        factory = viewModelFactoryOf { StockViewModel(app.movementRepository, app.productRepository) }
    )
    val products by catalogueViewModel.products.collectAsState()
    val stockRows by stockViewModel.rows.collectAsState()
    val stockByProduct = stockRows.associate { it.product.id to it.balance }

    val totalValueCents = products.sumOf { p -> (stockByProduct[p.id] ?: 0L) * p.priceCents }
    val lowStockCount = products.count { p -> (stockByProduct[p.id] ?: 0L) <= 5L }
    val totalStock = products.sumOf { p -> stockByProduct[p.id] ?: 0L }

    SandeTheme {
        Column(
            modifier = modifier.fillMaxSize().padding(16.dp)
        ) {
            ScreenHeader(title = "Dashboard", onOpenDrawer = onOpenDrawer)
            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                KpiCard(
                    title = "Inv. Value",
                    value = formatKes(totalValueCents),
                    subtitle = "All products",
                    icon = Icons.Default.Inventory,
                    iconColor = MaterialTheme.colorScheme.primary,
                    cardColor = Color(0xFFE3F2FD),
                    modifier = Modifier.weight(1f)
                )
                KpiCard(
                    title = "Low Stock",
                    value = lowStockCount.toString(),
                    subtitle = "Needs attention",
                    icon = Icons.Default.Storage,
                    iconColor = MaterialTheme.colorScheme.error,
                    cardColor = Color(0xFFFFEEEE),
                    modifier = Modifier.weight(1f)
                )
                KpiCard(
                    title = "Units",
                    value = totalStock.toString(),
                    subtitle = "In stock",
                    icon = Icons.Default.ShoppingCart,
                    iconColor = MaterialTheme.colorScheme.secondary,
                    cardColor = Color(0xFFE4E4EC),
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Stock by Product",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(top = 8.dp)
            ) {
                items(stockRows, key = { it.product.id }) { row ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = SandeShape.card,
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        border = BorderStroke(
                            0.5.dp,
                            MaterialTheme.colorScheme.outlineVariant
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Inventory,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = row.product.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1
                                )
                                Text(
                                    text = row.product.category ?: "General",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(
                                text = row.balance.toString(),
                                style = MaterialTheme.typography.titleMedium,
                                color = if (row.balance <= 0L) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun KpiCard(
    title: String,
    value: String,
    subtitle: String,
    icon: ImageVector,
    iconColor: Color,
    cardColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = SandeShape.card,
        colors = CardDefaults.cardColors(containerColor = cardColor)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
