package com.example.sande.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sande.data.entities.BusinessProfile
import com.example.sande.data.entities.SaleItem
import com.example.sande.data.entities.SaleSession
import com.example.sande.data.repository.BusinessProfileRepository
import com.example.sande.data.repository.SaleItemRepository
import com.example.sande.data.repository.SaleSessionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class ReportsViewModel(
    private val context: Context,
    private val saleSessionRepository: SaleSessionRepository,
    private val saleItemRepository: SaleItemRepository,
    private val businessProfileRepository: BusinessProfileRepository
) : ViewModel() {

    private val _selectedPeriod = MutableStateFlow("Today")
    val selectedPeriod: StateFlow<String> = _selectedPeriod
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), "Today")

    private val _startDate = MutableStateFlow(startOfToday())
    val startDate: StateFlow<Long> = _startDate
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), startOfToday())

    private val _endDate = MutableStateFlow(System.currentTimeMillis())
    val endDate: StateFlow<Long> = _endDate
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), System.currentTimeMillis())

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    /**
     * The business profile is read once and refreshed whenever the business
     * changes. `getActiveById` is a suspend function, so it is wrapped in a flow
     * instead of being called directly in the `stateIn` chain.
     */
    val businessProfile: StateFlow<BusinessProfile?> = flow {
        emit(businessProfileRepository.getActiveById(DEFAULT_BUSINESS_ID))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    private val tz: TimeZone
        get() = TimeZone.getTimeZone(businessProfile.value?.timezone ?: "Africa/Nairobi")

    /** The timezone the report is grouped by, shown in the UI. */
    val businessTimeZone: String
        get() = tz.id

    val reportData: StateFlow<ReportData> =
        combine(
            saleSessionRepository.getByBusinessAndDateRange(DEFAULT_BUSINESS_ID, startDate.value, endDate.value),
            saleItemRepository.getByBusinessAndDateRange(DEFAULT_BUSINESS_ID, startDate.value, endDate.value),
            selectedPeriod
        ) { sessions, items, _ ->
            buildReport(sessions, items)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReportData.empty())

    fun setPeriod(period: String) {
        _selectedPeriod.value = period
        val (start, end) = calculateDateRange(period)
        _startDate.value = start
        _endDate.value = end
    }

    fun setCustomDateRange(start: Long, end: Long) {
        _selectedPeriod.value = "Custom"
        _startDate.value = start
        _endDate.value = end
    }

    /**
     * Result of a CSV export attempt.
     *
     * The UI needs to tell the user whether the file was written and where it
     * went, or explain why the export failed.
     */
    sealed class CsvExportResult {
        data class Success(val filePath: String, val rowCount: Int) : CsvExportResult()
        data class Failure(val message: String) : CsvExportResult()
    }

    /**
     * Exports the current report data as CSV into the app cache and returns
     * where it was written.
     *
     * The file is written under `cacheDir/exports` so it can be handed to
     * other apps through the app's FileProvider (see `res/xml/file_paths.xml`).
     */
    suspend fun exportCsvToCache(): CsvExportResult = withContext(Dispatchers.IO) {
        val data = reportData.value
        try {
            val dir = File(context.cacheDir, "exports").apply { mkdirs() }
            val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
            val file = File(dir, "sande-report-$stamp.csv")

            val csv = buildCsv(data)
            file.writeText(csv)

            CsvExportResult.Success(file.absolutePath, data.transactionCount)
        } catch (e: Exception) {
            CsvExportResult.Failure(e.message ?: "Could not write the CSV file")
        }
    }

    /**
     * Builds the CSV body.
     *
     * Three blocks are written: a summary, the top products and the hourly
     * breakdown. Fields are quoted so that product names containing commas
     * or quotes do not break the column layout.
     */
    private fun buildCsv(data: ReportData): String {
        val sb = StringBuilder()

        sb.append("SANDE Report\n")
        sb.append("Period,").append(csvEscape(selectedPeriod.value)).append('\n')
        sb.append("Total sales (cents),").append(data.totalSales).append('\n')
        sb.append("Transactions,").append(data.transactionCount).append('\n')
        sb.append("Generated,").append(csvEscape(SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date()))).append('\n')
        sb.append('\n')

        sb.append("Top products\n")
        sb.append("Product ID,Name,Quantity sold,Revenue (cents)\n")
        data.topProducts.forEach { p ->
            sb.append(csvEscape(p.productId)).append(',')
            sb.append(csvEscape(p.name)).append(',')
            sb.append(p.quantitySold).append(',')
            sb.append(p.revenue).append('\n')
        }
        sb.append('\n')

        sb.append("Sales by hour\n")
        sb.append("Hour,Transactions,Total (cents)\n")
        data.salesByHour.forEach { h ->
            sb.append(h.hour).append(',')
            sb.append(h.count).append(',')
            sb.append(h.total).append('\n')
        }
        sb.append('\n')

        sb.append("Sales by cashier\n")
        sb.append("Cashier ID,Role,Transactions,Total (cents)\n")
        data.salesByCashier.forEach { c ->
            sb.append(csvEscape(c.cashierId)).append(',')
            sb.append(csvEscape(c.role)).append(',')
            sb.append(c.count).append(',')
            sb.append(c.total).append('\n')
        }

        return sb.toString()
    }

    /** Quotes a CSV field when it contains a separator, a quote or a newline. */
    private fun csvEscape(value: String): String {
        val needsQuotes = value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
        return if (needsQuotes) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else {
            value
        }
    }

    private fun calculateDateRange(period: String): Pair<Long, Long> {
        val cal = Calendar.getInstance(tz)
        val now = cal.timeInMillis
        return when (period) {
            "Today" -> {
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                cal.timeInMillis to now
            }
            "Yesterday" -> {
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                cal.add(Calendar.DAY_OF_MONTH, -1)
                val start = cal.timeInMillis
                cal.add(Calendar.DAY_OF_MONTH, 1)
                cal.add(Calendar.MILLISECOND, -1)
                start to cal.timeInMillis
            }
            "This Week" -> {
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                cal.set(Calendar.DAY_OF_WEEK, cal.getFirstDayOfWeek())
                val start = cal.timeInMillis
                cal.add(Calendar.DAY_OF_MONTH, 7)
                cal.add(Calendar.MILLISECOND, -1)
                start to cal.timeInMillis
            }
            "This Month" -> {
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                cal.set(Calendar.DAY_OF_MONTH, 1)
                val start = cal.timeInMillis
                cal.add(Calendar.MONTH, 1)
                cal.add(Calendar.MILLISECOND, -1)
                start to cal.timeInMillis
            }
            "Last Month" -> {
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                cal.set(Calendar.DAY_OF_MONTH, 1)
                cal.add(Calendar.MONTH, -1)
                val start = cal.timeInMillis
                cal.add(Calendar.MONTH, 1)
                cal.add(Calendar.MILLISECOND, -1)
                start to cal.timeInMillis
            }
            else -> startOfToday() to now
        }
    }

    private fun startOfToday(): Long {
        val cal = Calendar.getInstance(tz)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    private fun buildReport(sessions: List<SaleSession>, items: List<SaleItem>): ReportData {
        val completedSessions = sessions.filter { it.status == "completed" }

        val totalSales = completedSessions.sumOf { it.totalCents }
        val transactionCount = completedSessions.size

        // Top products, by revenue
        val topProducts = items
            .groupBy { it.productId }
            .map { (productId, group) ->
                val first = group.first()
                TopProductData(
                    productId = productId,
                    name = first.productName,
                    quantitySold = group.sumOf { it.quantity },
                    revenue = group.sumOf { it.totalCents }
                )
            }
            .sortedByDescending { it.revenue }
            .take(10)

        // Sales by hour
        val salesByHour = completedSessions
            .groupBy { session ->
                val cal = Calendar.getInstance(tz)
                cal.timeInMillis = session.createdAt
                cal.get(Calendar.HOUR_OF_DAY)
            }
            .map { (hour, hourSessions) ->
                HourlySalesData(
                    hour = hour,
                    count = hourSessions.size,
                    total = hourSessions.sumOf { it.totalCents }
                )
            }
            .sortedBy { it.hour }

        // Sales by cashier
        val salesByCashier = completedSessions
            .groupBy { it.salespersonId }
            .map { (id, cashierSessions) ->
                CashierSalesData(
                    cashierId = id,
                    role = cashierSessions.first().salespersonRole,
                    count = cashierSessions.size,
                    total = cashierSessions.sumOf { it.totalCents }
                )
            }
            .sortedByDescending { it.total }

        return ReportData(
            totalSales = totalSales,
            transactionCount = transactionCount,
            topProducts = topProducts,
            salesByHour = salesByHour,
            salesByCashier = salesByCashier
        )
    }

    companion object {
        const val DEFAULT_BUSINESS_ID = "default-business"
    }
}

data class ReportData(
    val totalSales: Long = 0,
    val transactionCount: Int = 0,
    val topProducts: List<TopProductData> = emptyList(),
    val salesByHour: List<HourlySalesData> = emptyList(),
    val salesByCashier: List<CashierSalesData> = emptyList()
) {
    companion object {
        fun empty() = ReportData()
    }
}

data class TopProductData(
    val productId: String,
    val name: String,
    val quantitySold: Long,
    val revenue: Long
)

data class HourlySalesData(
    val hour: Int,
    val count: Int,
    val total: Long
)

data class CashierSalesData(
    val cashierId: String,
    val role: String,
    val count: Int,
    val total: Long
)
