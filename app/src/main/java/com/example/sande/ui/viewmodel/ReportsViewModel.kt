package com.example.sande.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sande.data.entities.BusinessProfile
import com.example.sande.data.entities.SaleItem
import com.example.sande.data.entities.SaleSession
import com.example.sande.data.repository.BusinessProfileRepository
import com.example.sande.data.repository.SaleItemRepository
import com.example.sande.data.repository.SaleSessionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import java.util.Calendar
import java.util.TimeZone

class ReportsViewModel(
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

    fun exportCsv() {
        // TODO: Implement CSV export
        // Would generate CSV and share via intent
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
