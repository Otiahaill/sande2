package com.example.sande.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sande.data.receipt.ReceiptData
import com.example.sande.data.receipt.ReceiptRepository
import com.example.sande.data.receipt.ThermalPrinterFormatter
import com.example.sande.data.receipt.PdfReceiptRenderer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

class ReceiptHistoryViewModel(
    private val receiptRepository: ReceiptRepository
) : ViewModel() {

    private val _receipts = MutableStateFlow<List<ReceiptData>>(emptyList())
    val receipts: StateFlow<List<ReceiptData>> = _receipts.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), "")

    fun loadAll(businessId: String) {
        _isLoading.value = true
        viewModelScope.launch {
            try {
                val data = receiptRepository.getAllReceiptsData(businessId).first()
                _receipts.value = data
            } catch (e: Exception) {
                _receipts.value = emptyList()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun search(query: String) {
        _searchQuery.value = query
        if (query.isBlank()) {
            loadAll("default-business")
        } else {
            _isLoading.value = true
            viewModelScope.launch {
                try {
                    val data = receiptRepository.searchReceipts("default-business", query).first()
                    _receipts.value = data
                } catch (e: Exception) {
                    _receipts.value = emptyList()
                } finally {
                    _isLoading.value = false
                }
            }
        }
    }

    fun loadByDateRange(businessId: String, startDate: Long, endDate: Long) {
        _isLoading.value = true
        viewModelScope.launch {
            try {
                val data = receiptRepository.getReceiptsByDateRange(businessId, startDate, endDate).first()
                _receipts.value = data
            } catch (e: Exception) {
                _receipts.value = emptyList()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun reprint(receiptData: ReceiptData) {
        viewModelScope.launch {
            try {
                // Print using thermal formatter
                val formatter = ThermalPrinterFormatter()
                val bytes = formatter.formatReceipt(
                    receiptData.session,
                    receiptData.items,
                    receiptData.business,
                    receiptData.receipt
                )
                // TODO: Send to Bluetooth printer via BluetoothPrinterHelper
                // For now, just log
                android.util.Log.d("ReceiptHistory", "Reprint: ${bytes.size} bytes")
            } catch (e: Exception) {
                android.util.Log.e("ReceiptHistory", "Reprint failed", e)
            }
        }
    }

    fun share(receiptData: ReceiptData, context: android.content.Context) {
        viewModelScope.launch {
            try {
                val renderer = PdfReceiptRenderer(context)
                val result = renderer.renderToFile(
                    receiptData.session,
                    receiptData.items,
                    receiptData.business,
                    receiptData.receipt,
                    PdfReceiptRenderer.PaperSize.A4,
                    "receipt_${receiptData.receipt.receiptNumber}.pdf"
                )

                val file = File(result.filePath)
                val uri = androidx.core.content.FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )

                val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                    type = "application/pdf"
                    putExtra(android.content.Intent.EXTRA_STREAM, uri)
                    putExtra(android.content.Intent.EXTRA_SUBJECT, "Receipt ${receiptData.receipt.receiptNumber}")
                    putExtra(android.content.Intent.EXTRA_TEXT, "Receipt from ${receiptData.business.name}")
                    flags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                }

                val chooser = android.content.Intent.createChooser(intent, "Share receipt via")
                context.startActivity(chooser)
            } catch (e: Exception) {
                android.util.Log.e("ReceiptHistory", "Share failed", e)
            }
        }
    }

    fun shareWhatsApp(receiptData: ReceiptData, context: android.content.Context) {
        viewModelScope.launch {
            try {
                val text = buildWhatsAppText(receiptData)
                val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(android.content.Intent.EXTRA_TEXT, text)
                    setPackage("com.whatsapp")
                }
                if (intent.resolveActivity(context.packageManager) != null) {
                    context.startActivity(intent)
                } else {
                    // Fallback to generic share
                    val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(android.content.Intent.EXTRA_TEXT, text)
                    }
                    context.startActivity(android.content.Intent.createChooser(shareIntent, "Share via"))
                }
            } catch (e: Exception) {
                android.util.Log.e("ReceiptHistory", "WhatsApp share failed", e)
            }
        }
    }

    fun delete(receiptData: ReceiptData) {
        // TODO: Implement receipt deletion (soft delete or mark voided)
        // For now, just remove from local list
        _receipts.value = _receipts.value.filterNot { it.receipt.id == receiptData.receipt.id }
    }

    private fun buildWhatsAppText(data: ReceiptData): String {
        val sb = StringBuilder()
        sb.append("*${data.business.name}*\n")
        if (data.business.address != null && data.business.address.isNotBlank()) {
            sb.append(data.business.address).append("\n")
        }
        sb.append("Receipt: ${data.receipt.receiptNumber}\n")
        sb.append("Date: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm").format(java.util.Date(data.session.createdAt))}\n")
        sb.append("Cashier: ${data.session.salespersonId}\n")
        sb.append("-------------------\n")
        data.items.forEach { item ->
            sb.append("${item.productName}\n")
            sb.append("  ${item.quantity} x ${formatCurrency(item.unitPriceCents)} = ${formatCurrency(item.totalCents)}\n")
        }
        sb.append("-------------------\n")
        sb.append("Total: ${formatCurrency(data.session.totalCents)}\n")
        sb.append("\nThank you for shopping!\nPowered by SANDE")
        return sb.toString()
    }

    private fun formatCurrency(cents: Long): String {
        val amount = cents / 100.0
        return java.text.NumberFormat.getCurrencyInstance(java.util.Locale.getDefault()).format(amount)
    }
}
