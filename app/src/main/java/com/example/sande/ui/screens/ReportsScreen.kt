package com.example.sande.ui.screens

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
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
import androidx.compose.material.icons.filled.Download
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
import com.example.sande.di.SandeApplication
import com.example.sande.ui.ScreenHeader
import com.example.sande.ui.theme.SandeTheme
import com.example.sande.ui.viewModelFactoryOf
import com.example.sande.ui.viewmodel.CashierSalesData
import com.example.sande.ui.viewmodel.HourlySalesData
import com.example.sande.ui.viewmodel.ReportsViewModel
import com.example.sande.ui.viewmodel.TopProductData
import kotlinx.coroutines.launch
import java.io.File
import java.util.Calendar
import java.util.TimeZone
import androidx.compose.material.icons.filled.Menu
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.layout.wrapContentSize

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
    modifier: Modifier = Modifier,
    onOpenDrawer: () -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as SandeApplication
    val vm: ReportsViewModel = viewModel(
        factory = viewModelFactoryOf { ReportsViewModel(context, app.saleSessionRepository, app.saleItemRepository, app.businessProfileRepository) }
    )

    val reportData by vm.reportData.collectAsState()
    val isLoading by vm.isLoading.collectAsState()
    val selectedPeriod by vm.selectedPeriod.collectAsState()
    val startDate by vm.startDate.collectAsState()
    val endDate by vm.endDate.collectAsState()

    val scope = rememberCoroutineScope()

    // One-off message for the export action (success or failure).
    var exportMessage by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Reports") },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(Icons.Filled.Menu, contentDescription = "Open drawer")
                    }
                },
                actions = {
                    androidx.compose.material3.IconButton(
                        onClick = {
                            scope.launch {
                                when (val result = vm.exportCsvToCache()) {
                                    is ReportsViewModel.CsvExportResult.Success -> {
                                        exportMessage = "Exported to ${File(result.filePath).name}"
                                        shareCsv(context, result.filePath)
                                    }
                                    is ReportsViewModel.CsvExportResult.Failure -> {
                                        exportMessage = "Export failed: ${result.message}"
                                    }
                                }
                            }
                        }
                    ) {
                        Icon(Icons.Filled.Download, contentDescription = "Export CSV")
                    }
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
            // Period selector
            PeriodSelector(
                selectedPeriod = selectedPeriod,
                onPeriodChange = { vm.setPeriod(it) },
                startDate = startDate,
                endDate = endDate,
                onDateChange = { start, end -> vm.setCustomDateRange(start, end) }
            )

            androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(16.dp))

            // Result of the last CSV export.
            exportMessage?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(8.dp))
            }

            if (isLoading) {
                androidx.compose.material3.CircularProgressIndicator(
                    modifier = Modifier
                        .fillMaxSize()
                        .wrapContentSize(Alignment.Center)
                )
            } else {
                // Summary cards
                androidx.compose.foundation.layout.Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SummaryCard(
                        title = "Total Sales",
                        value = formatCurrency(reportData.totalSales),
                        color = MaterialTheme.colorScheme.primary
                    )
                    SummaryCard(
                        title = "Transactions",
                        value = reportData.transactionCount.toString(),
                        color = MaterialTheme.colorScheme.secondary
                    )
                    SummaryCard(
                        title = "Avg. Transaction",
                        value = formatCurrency(if (reportData.transactionCount > 0) reportData.totalSales / reportData.transactionCount else 0),
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }

                androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(16.dp))

                // Top products
                if (reportData.topProducts.isNotEmpty()) {
                    Text(
                        text = "Top Products",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(8.dp))
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(reportData.topProducts) { product ->
                            TopProductItem(product = product)
                        }
                    }
                    androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(16.dp))
                }

                // Sales by hour
                if (reportData.salesByHour.isNotEmpty()) {
                    Text(
                        text = "Sales by Hour (${vm.businessTimeZone})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(8.dp))
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(reportData.salesByHour) { hourData ->
                            HourlySalesItem(hourData = hourData)
                        }
                    }
                    androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(16.dp))
                }

                // Sales by cashier
                if (reportData.salesByCashier.isNotEmpty()) {
                    Text(
                        text = "Sales by Cashier",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(8.dp))
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(reportData.salesByCashier) { cashierData ->
                            CashierSalesItem(cashierData = cashierData)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PeriodSelector(
    selectedPeriod: String,
    onPeriodChange: (String) -> Unit,
    startDate: Long,
    endDate: Long,
    onDateChange: (Long, Long) -> Unit
) {
    val periods = listOf("Today", "Yesterday", "This Week", "This Month", "Last Month", "Custom")

    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            periods.forEach { period ->
                androidx.compose.material3.FilterChip(
                    selected = selectedPeriod == period,
                    onClick = { onPeriodChange(period) },
                    label = { Text(period) },
                    modifier = Modifier.padding(4.dp)
                )
            }
        }

        if (selectedPeriod == "Custom") {
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(8.dp))
            androidx.compose.foundation.layout.Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                androidx.compose.material3.Text(
                    text = "From: ${java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.getDefault()).format(java.util.Date(startDate))}",
                    style = MaterialTheme.typography.bodySmall
                )
                androidx.compose.material3.Text(
                    text = "To: ${java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.getDefault()).format(java.util.Date(endDate))}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
fun SummaryCard(
    title: String,
    value: String,
    color: Color
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f)
            .height(100.dp),
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.1f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = title, style = MaterialTheme.typography.bodySmall, color = color)
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(4.dp))
            Text(text = value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = color)
        }
    }
}

@Composable
fun TopProductItem(
    product: TopProductData
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            androidx.compose.foundation.layout.Column {
                Text(text = product.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                Text(text = "Qty sold: ${product.quantitySold} • Revenue: ${formatCurrency(product.revenue)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(text = formatCurrency(product.revenue), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
fun HourlySalesItem(
    hourData: HourlySalesData
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "${String.format("%02d:00", hourData.hour)}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            Text(text = "${hourData.count} sales", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(text = formatCurrency(hourData.total), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
fun CashierSalesItem(
    cashierData: CashierSalesData
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            androidx.compose.foundation.layout.Column {
                Text(text = "${cashierData.cashierId} (${cashierData.role})", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                Text(text = "${cashierData.count} transactions", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(text = formatCurrency(cashierData.total), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        }
    }
}

private fun formatCurrency(cents: Long): String {
    val amount = cents / 100.0
    return java.text.NumberFormat.getCurrencyInstance(java.util.Locale.getDefault()).format(amount)
}

/**
 * Shares an exported CSV file with other apps.
 *
 * The file lives in the app cache, so it has to be exposed through the
 * app's FileProvider (authority `${applicationId}.fileprovider`) rather than
 * handed out as a `file://` URI.
 */
private fun shareCsv(context: Context, filePath: String) {
    try {
        val file = File(filePath)
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "SANDE report")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share report"))
    } catch (e: Exception) {
        android.util.Log.e("Reports", "Could not share the CSV file", e)
    }
}
