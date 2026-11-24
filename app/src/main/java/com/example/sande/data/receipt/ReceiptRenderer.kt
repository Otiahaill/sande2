package com.example.sande.data.receipt

import com.example.sande.data.entities.BusinessProfile
import com.example.sande.data.entities.Product
import com.example.sande.data.entities.Receipt
import com.example.sande.data.entities.SaleItem
import com.example.sande.data.entities.SaleSession
import com.example.sande.data.repository.BusinessProfileRepository
import com.example.sande.data.repository.ProductRepository
import kotlinx.coroutines.flow.first

/**
 * Data class containing all information needed to render a receipt.
 */
data class ReceiptData(
    val session: SaleSession,
    val items: List<SaleItem>,
    val business: BusinessProfile,
    val receipt: Receipt
)

/**
 * Formats currency according to business locale.
 */
fun formatCurrency(cents: Long, currencyCode: String = "KES", locale: String = "en"): String {
    val amount = cents / 100.0
    return java.text.NumberFormat.getCurrencyInstance(java.util.Locale(locale)).format(amount)
}

/**
 * Renders a receipt as plain text (for thermal printers and fallback).
 */
fun renderReceiptText(data: ReceiptData): String {
    val sb = StringBuilder()
    val business = data.business
    val session = data.session
    val items = data.items
    val receipt = data.receipt

    val width = 32 // 58mm thermal printer character width
    val line = "-".repeat(width)

    fun center(text: String) {
        val padding = (width - text.length) / 2
        sb.append(" ".repeat(maxOf(padding, 0))).append(text).append("\n")
    }

    fun leftRight(left: String, right: String) {
        val leftText = left.take(width - right.length - 1)
        val spaces = width - leftText.length - right.length
        sb.append(leftText).append(" ".repeat(maxOf(spaces, 0))).append(right).append("\n")
    }

    // Header
    center(business.name)
    if (business.address != null && business.address.isNotBlank()) {
        center(business.address!!)
    }
    if (business.phone != null && business.phone.isNotBlank()) {
        center("Tel: ${business.phone}")
    }
    sb.append(line).append("\n")

    // Receipt info
    center("RECEIPT")
    sb.append(line).append("\n")
    leftRight("Receipt #:", receipt.receiptNumber)
    leftRight("Date:", java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date(session.createdAt)))
    leftRight("Cashier:", "${session.salespersonId} (${session.salespersonRole})")
    leftRight("Device:", session.deviceCode)
    sb.append(line).append("\n")

    // Items
    center("ITEMS")
    sb.append(line).append("\n")
    items.forEach { item ->
        val name = item.productName
        val qtyPrice = "${item.quantity} x ${formatCurrency(item.unitPriceCents, business.currencyCode)}"
        val total = formatCurrency(item.totalCents, business.currencyCode)

        // Truncate name if too long
        val displayName = if (name.length > width) name.substring(0, width - 3) + "..." else name
        sb.append(displayName).append("\n")
        leftRight(qtyPrice, total)
    }
    sb.append(line).append("\n")

    // Totals
    leftRight("Subtotal:", formatCurrency(session.subtotalCents, business.currencyCode))
    if (session.taxCents > 0) {
        leftRight("Tax:", formatCurrency(session.taxCents, business.currencyCode))
    }
    sb.append(line).append("\n")
    leftRight("TOTAL:", formatCurrency(session.totalCents, business.currencyCode)).also { sb.setLength(sb.length - 1); sb.append(" ").append(it).append("\n") }
    sb.append(line).append("\n")

    // Payment method (placeholder)
    leftRight("Payment:", "Cash")
    leftRight("Change:", formatCurrency(0, business.currencyCode))
    sb.append(line).append("\n")

    // Footer
    if (business.receiptFooter != null && business.receiptFooter.isNotBlank()) {
        center(business.receiptFooter!!)
    }
    center("Thank you for shopping!")
    center("Powered by SANDE")

    return sb.toString()
}

/**
 * Renders a receipt as HTML (for PDF generation).
 */
fun renderReceiptHtml(data: ReceiptData): String {
    val business = data.business
    val session = data.session
    val items = data.items
    val receipt = data.receipt

    val itemsHtml = items.map { item ->
        """
        <tr>
            <td>${item.productName}</td>
            <td style="text-align: right;">${item.quantity}</td>
            <td style="text-align: right;">${formatCurrency(item.unitPriceCents, business.currencyCode)}</td>
            <td style="text-align: right;">${formatCurrency(item.totalCents, business.currencyCode)}</td>
        </tr>
        """.trimIndent()
    }.joinToString("\n")

    return """
    <!DOCTYPE html>
    <html>
    <head>
        <meta charset="UTF-8">
        <style>
            body { font-family: monospace; font-size: 12px; margin: 0; padding: 20px; }
            .receipt { max-width: 300px; margin: 0 auto; border: 1px solid #ddd; padding: 20px; }
            .center { text-align: center; }
            .line { border-top: 1px dashed #000; margin: 10px 0; }
            .header { margin-bottom: 10px; }
            .info { margin: 10px 0; font-size: 11px; }
            .info div { display: flex; justify-content: space-between; margin: 2px 0; }
            table { width: 100%; border-collapse: collapse; margin: 10px 0; }
            th, td { padding: 4px 0; text-align: left; font-size: 11px; }
            th { border-bottom: 1px solid #000; }
            .totals { margin-top: 10px; }
            .totals div { display: flex; justify-content: space-between; margin: 3px 0; }
            .total { font-weight: bold; font-size: 14px; border-top: 2px solid #000; padding-top: 5px; }
            .footer { margin-top: 20px; font-size: 10px; }
        </style>
    </head>
    <body>
        <div class="receipt">
            <div class="header center">
                <h2 style="margin: 0 0 5px;">${business.name}</h2>
                ${business.address?.let { "<p style='margin: 2px 0;'>$it</p>" } ?: ""}
                ${business.phone?.let { "<p style='margin: 2px 0;'>Tel: $it</p>" } ?: ""}
            </div>
            <div class="line"></div>
            <div class="center"><strong>RECEIPT</strong></div>
            <div class="line"></div>
            <div class="info">
                <div><span>Receipt #:</span><span>${receipt.receiptNumber}</span></div>
                <div><span>Date:</span><span>${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date(session.createdAt))}</span></div>
                <div><span>Cashier:</span><span>${session.salespersonId} (${session.salespersonRole})</span></div>
                <div><span>Device:</span><span>${session.deviceCode}</span></div>
            </div>
            <div class="line"></div>
            <table>
                <thead>
                    <tr><th>Item</th><th>Qty</th><th>Price</th><th>Total</th></tr>
                </thead>
                <tbody>
                    $itemsHtml
                </tbody>
            </table>
            <div class="line"></div>
            <div class="totals">
                <div><span>Subtotal:</span><span>${formatCurrency(session.subtotalCents, business.currencyCode)}</span></div>
                ${if (session.taxCents > 0) "<div><span>Tax:</span><span>${formatCurrency(session.taxCents, business.currencyCode)}</span></div>" else ""}
                <div class="total"><span>TOTAL:</span><span>${formatCurrency(session.totalCents, business.currencyCode)}</span></div>
            </div>
            <div class="line"></div>
            <div class="info">
                <div><span>Payment:</span><span>Cash</span></div>
                <div><span>Change:</span><span>${formatCurrency(0, business.currencyCode)}</span></div>
            </div>
            <div class="footer center">
                ${business.receiptFooter?.let { "<p>$it</p>" } ?: ""}
                <p>Thank you for shopping!</p>
                <p>Powered by SANDE</p>
            </div>
        </div>
    </body>
    </html>
    """.trimIndent()
}
