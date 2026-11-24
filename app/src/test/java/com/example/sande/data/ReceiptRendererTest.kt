package com.example.sande.data

import com.example.sande.data.entities.BusinessProfile
import com.example.sande.data.entities.Receipt
import com.example.sande.data.entities.SaleItem
import com.example.sande.data.entities.SaleSession
import com.example.sande.data.receipt.ReceiptData
import com.example.sande.data.receipt.ThermalPrinterFormatter
import com.example.sande.data.receipt.formatCurrency
import com.example.sande.data.receipt.renderReceiptText
import com.example.sande.data.receipt.renderReceiptHtml
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Tests for receipt rendering (text, HTML, thermal).
 */
class ReceiptRendererTest {

    private val business = BusinessProfile(
        id = "biz1",
        name = "Sunday Hardware",
        category = "hardware",
        phone = "+254700123456",
        countryCode = "KE",
        currencyCode = "KES",
        timezone = "Africa/Nairobi",
        address = "Nairobi, Kenya",
        logoUri = null,
        negativeStockPolicy = "warn",
        receiptFooter = "Thank you for your business!",
        createdAt = System.currentTimeMillis(),
        updatedAt = System.currentTimeMillis(),
        deletedAt = 0,
        schemaVersion = 1
    )

    private val session = SaleSession(
        id = "session1",
        businessId = "biz1",
        receiptNumber = "SAL-LOC-00001",
        deviceId = "dev1",
        deviceCode = "LOC",
        salespersonId = "user1",
        salespersonRole = "owner",
        subtotalCents = 5000,
        taxCents = 0,
        totalCents = 5000,
        status = "completed",
        createdAt = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm").parse("2026-01-15 14:30")?.time ?: System.currentTimeMillis(),
        updatedAt = System.currentTimeMillis(),
        synced = false
    )

    private val items = listOf(
        SaleItem(
            id = "item1",
            sessionId = "session1",
            productId = "prod1",
            productName = "Hammer 16oz",
            categoryName = "Tools",
            quantity = 2,
            unitPriceCents = 1500,
            totalCents = 3000,
            businessId = "biz1",
            createdAt = System.currentTimeMillis()
        ),
        SaleItem(
            id = "item2",
            sessionId = "session1",
            productId = "prod2",
            productName = "Nails 1kg",
            categoryName = "Fasteners",
            quantity = 1,
            unitPriceCents = 2000,
            totalCents = 2000,
            businessId = "biz1",
            createdAt = System.currentTimeMillis()
        )
    )

    private val receipt = Receipt(
        id = "receipt1",
        sessionId = "session1",
        businessId = "biz1",
        deviceCode = "LOC",
        counter = 1,
        receiptNumber = "SAL-LOC-00001",
        format = "thermal_58mm",
        contentHash = "abc123",
        createdAt = System.currentTimeMillis()
    )

    private val data = ReceiptData(session, items, business, receipt)

    @Test
    fun renderReceiptTextContainsAllFields() {
        val text = renderReceiptText(data)

        assertThat(text).contains("Sunday Hardware")
        assertThat(text).contains("Nairobi, Kenya")
        assertThat(text).contains("Tel: +254700123456")
        assertThat(text).contains("RECEIPT")
        assertThat(text).contains("SAL-LOC-00001")
        assertThat(text).contains("Hammer 16oz")
        assertThat(text).contains("Nails 1kg")
        assertThat(text).contains("2 x")
        assertThat(text).contains("1 x")
        assertThat(text).contains("KES") // Currency
        assertThat(text).contains("Thank you for your business!")
        assertThat(text).contains("Powered by SANDE")
    }

    @Test
    fun renderReceiptTextHasCorrectTotals() {
        val text = renderReceiptText(data)

        // Subtotal: 5000 cents = KES 50.00
        assertThat(text).contains("Subtotal:")
        assertThat(text).contains("TOTAL:")
    }

    @Test
    fun renderReceiptHtmlIsValidHtml() {
        val html = renderReceiptHtml(data)

        assertThat(html).contains("<!DOCTYPE html>")
        assertThat(html).contains("<html>")
        assertThat(html).contains("</html>")
        assertThat(html).contains("Sunday Hardware")
        assertThat(html).contains("Hammer 16oz")
        assertThat(html).contains("Nails 1kg")
        assertThat(html).contains("SAL-LOC-00001")
        assertThat(html).contains("table")
        assertThat(html).contains("th")
        assertThat(html).contains("td")
    }

    @Test
    fun thermalFormatter58mmProducesBytes() {
        val formatter = ThermalPrinterFormatter(ThermalPrinterFormatter.PrinterProfile.PROFILE_58MM)
        val bytes = formatter.formatReceipt(session, items, business, receipt)

        assertThat(bytes).isNotEmpty()
        // Should start with ESC @ (init)
        assertThat(bytes[0]).isEqualTo(0x1B)
        assertThat(bytes[1]).isEqualTo(0x40)
        // Should end with the ESC/POS partial-cut command: GS V 1 (1D 56 01)
        assertThat(bytes[bytes.size - 3]).isEqualTo(0x1D)
        assertThat(bytes[bytes.size - 2]).isEqualTo(0x56)
        assertThat(bytes[bytes.size - 1]).isEqualTo(0x01)
    }

    @Test
    fun thermalFormatter80mmProducesBytes() {
        val formatter = ThermalPrinterFormatter(ThermalPrinterFormatter.PrinterProfile.PROFILE_80MM)
        val bytes = formatter.formatReceipt(session, items, business, receipt)

        assertThat(bytes).isNotEmpty()
        assertThat(bytes[0]).isEqualTo(0x1B)
        assertThat(bytes[1]).isEqualTo(0x40)
    }

    @Test
    fun thermalTestPrintProducesBytes() {
        val formatter = ThermalPrinterFormatter()
        val bytes = formatter.formatTestPrint()

        assertThat(bytes).isNotEmpty()
        assertThat(bytes[0]).isEqualTo(0x1B) // ESC @
    }

    @Test
    fun formatCurrencyFormatsCorrectly() {
        assertThat(formatCurrency(0)).contains("0.00")
        assertThat(formatCurrency(100)).contains("1.00")
        assertThat(formatCurrency(1234)).contains("12.34")
        assertThat(formatCurrency(10000)).contains("100.00")
        assertThat(formatCurrency(5000)).contains("50.00")
    }

    @Test
    fun receiptWithTaxShowsTaxLine() {
        val sessionWithTax = session.copy(taxCents = 800, totalCents = 5800)
        val dataWithTax = data.copy(session = sessionWithTax)

        val text = renderReceiptText(dataWithTax)

        assertThat(text).contains("Tax:")
    }

    @Test
    fun receiptWithLongProductNameTruncates() {
        val longNameItem = SaleItem(
            id = "item3",
            sessionId = "session1",
            productId = "prod3",
            productName = "Very Long Product Name That Exceeds The Character Limit For Thermal Printer",
            categoryName = "Test",
            quantity = 1,
            unitPriceCents = 1000,
            totalCents = 1000,
            businessId = "biz1",
            createdAt = System.currentTimeMillis()
        )
        val dataWithLongName = data.copy(items = listOf(longNameItem))

        val text = renderReceiptText(dataWithLongName)

        // Should not crash and should contain truncated name
        assertThat(text).contains("...")
    }
}
