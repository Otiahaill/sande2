package com.example.sande.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sande.data.entities.Movement
import com.example.sande.data.entities.SaleHeader
import com.example.sande.data.repository.MovementRepository
import com.example.sande.data.repository.SaleHeaderRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.ZoneId

@Serializable
data class ProductSales(
    val productId: String,
    val productName: String,
    val quantitySold: Long,
    val revenueCents: Long
)

@Serializable
data class SalespersonSummary(
    val salespersonId: String,
    val salespersonRole: String,
    val salesCount: Int,
    val revenueCents: Long
)

@Serializable
data class DailyReport(
    val date: String,
    val salesCount: Int,
    val totalRevenueCents: Long,
    val itemsSold: Long,
    val topProducts: List<ProductSales>,
    val salesBySalesperson: List<SalespersonSummary>
)

@Serializable
data class PeriodReport(
    val startDate: String,
    val endDate: String,
    val totalSales: Int,
    val totalRevenueCents: Long,
    val totalItemsSold: Long,
    val averageTicketCents: Long,
    val dailyBreakdown: List<DailyReport>
)

class ReportsViewModel(
    private val saleHeaderRepository: SaleHeaderRepository,
    private val movementRepository: MovementRepository
) : ViewModel() {

    enum class PeriodType {
        TODAY, YESTERDAY, LAST_7_DAYS, LAST_30_DAYS, CUSTOM
    }

    private val _selectedPeriod = MutableStateFlow<PeriodType>(PeriodType.LAST_7_DAYS)
    val selectedPeriod: StateFlow<PeriodType> = _selectedPeriod.asStateFlow()

    private val _customStartDate = MutableStateFlow<java.time.LocalDate?>(null)
    val customStartDate: StateFlow<java.time.LocalDate?> = _customStartDate.asStateFlow()

    private val _customEndDate = MutableStateFlow<java.time.LocalDate?>(null)
    val customEndDate: StateFlow<java.time.LocalDate?> = _customEndDate.asStateFlow()

    val periodReport: StateFlow<PeriodReport> = combine(
        saleHeaderRepository.getByBusiness("default-business"),
        movementRepository.getAll,
        _selectedPeriod,
        _customStartDate,
        _customEndDate
    ) { sales, movements, periodType, customStart, customEnd ->
        generatePeriodReport(sales, movements, periodType, customStart, customEnd)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PeriodReport(
        startDate = java.time.LocalDate.now().toString(),
        endDate = java.time.LocalDate.now().toString(),
        totalSales = 0,
        totalRevenueCents = 0,
        totalItemsSold = 0,
        averageTicketCents = 0,
        dailyBreakdown = emptyList()
    ))

    fun setPeriod(period: PeriodType) {
        _selectedPeriod.value = period
    }

    fun setCustomRange(start: java.time.LocalDate, end: java.time.LocalDate) {
        _customStartDate.value = start
        _customEndDate.value = end
        _selectedPeriod.value = PeriodType.CUSTOM
    }

    private fun generatePeriodReport(
        sales: List<SaleHeader>,
        movements: List<Movement>,
        periodType: PeriodType,
        customStart: java.time.LocalDate?,
        customEnd: java.time.LocalDate?
    ): PeriodReport {
        val now = java.time.LocalDate.now(java.time.ZoneId.systemDefault())
        val (startDate, endDate) = when (periodType) {
            PeriodType.TODAY -> now to now
            PeriodType.YESTERDAY -> now.minusDays(1) to now.minusDays(1)
            PeriodType.LAST_7_DAYS -> now.minusDays(6) to now
            PeriodType.LAST_30_DAYS -> now.minusDays(29) to now
            PeriodType.CUSTOM -> (customStart ?: now.minusDays(6)) to (customEnd ?: now)
        }

        val filteredSales = sales.filter { sale ->
            val saleDate = java.time.Instant.ofEpochMilli(sale.createdAt).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
            saleDate >= startDate && saleDate <= endDate && sale.status == "completed"
        }

        val filteredMovements = movements.filter { movement ->
            val movementDate = java.time.Instant.ofEpochMilli(movement.createdAt).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
            movementDate >= startDate && movementDate <= endDate && movement.type == com.example.sande.data.entities.MovementType.SALE
        }

        val dailyReports = buildDailyReports(startDate, endDate, filteredSales, filteredMovements)

        val totalSales = filteredSales.size
        val totalRevenueCents = filteredSales.sumOf { it.totalCents }
        val totalItemsSold = filteredMovements.sumOf { it.quantity }
        val averageTicketCents = if (totalSales > 0) totalRevenueCents / totalSales else 0L

        return PeriodReport(
            startDate = startDate.toString(),
            endDate = endDate.toString(),
            totalSales = totalSales,
            totalRevenueCents = totalRevenueCents,
            totalItemsSold = totalItemsSold,
            averageTicketCents = averageTicketCents,
            dailyBreakdown = dailyReports
        )
    }

    private fun buildDailyReports(
        startDate: java.time.LocalDate,
        endDate: java.time.LocalDate,
        sales: List<SaleHeader>,
        movements: List<Movement>
    ): List<DailyReport> {
        val dailyMap = mutableMapOf<java.time.LocalDate, MutableList<SaleHeader>>()
        var current = startDate
        while (current <= endDate) {
            dailyMap[current] = mutableListOf()
            current = current.plusDays(1)
        }

        sales.forEach { sale ->
            val date = java.time.Instant.ofEpochMilli(sale.createdAt).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
            dailyMap[date]?.add(sale)
        }

        val movementByDate = movements.groupBy { movement ->
            java.time.Instant.ofEpochMilli(movement.createdAt).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
        }

        return dailyMap.entries.map { (date, daySales) ->
            val dayMovements = movementByDate[date] ?: emptyList()
            
            val productSales = dayMovements
                .groupBy { it.productId }
                .map { (productId, productMovements) ->
                    val first = productMovements.first()
                    val totalQty = productMovements.sumOf { it.quantity }
                    val totalRevenue = productMovements.sumOf { it.quantity * (it.unitPriceCents ?: 0L) }
                    ProductSales(
                        productId = productId,
                        productName = first.referenceId ?: productId,
                        quantitySold = totalQty,
                        revenueCents = totalRevenue
                    )
                }.sortedByDescending { it.revenueCents }

            val salespersonSummary = daySales.groupBy { it.salespersonId }
                .map { (salespersonId, personSales) ->
                    val first = personSales.first()
                    SalespersonSummary(
                        salespersonId = salespersonId,
                        salespersonRole = first.salespersonRole,
                        salesCount = personSales.size,
                        revenueCents = personSales.sumOf { it.totalCents }
                    )
                }.sortedByDescending { it.revenueCents }

            DailyReport(
                date = date.toString(),
                salesCount = daySales.size,
                totalRevenueCents = daySales.sumOf { it.totalCents },
                itemsSold = dayMovements.sumOf { it.quantity },
                topProducts = productSales,
                salesBySalesperson = salespersonSummary
            )
        }.filter { it.salesCount > 0 }
    }

    suspend fun exportReportCsv(report: PeriodReport): String {
        val sb = StringBuilder()
        sb.append("Date,Sales Count,Revenue (cents),Items Sold,Average Ticket (cents)\n")
        report.dailyBreakdown.forEach { day ->
            sb.append("${day.date},${day.salesCount},${day.totalRevenueCents},${day.itemsSold},")
            sb.append("${if (day.salesCount > 0) day.totalRevenueCents / day.salesCount else 0}\n")
        }
        return sb.toString()
    }

    suspend fun exportReportJson(report: PeriodReport): String {
        return Json.encodeToString(PeriodReport.serializer(), report)
    }
}
