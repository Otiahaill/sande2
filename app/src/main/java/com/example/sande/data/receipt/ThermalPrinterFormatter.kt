package com.example.sande.data.receipt

import com.example.sande.data.entities.BusinessProfile
import com.example.sande.data.entities.Receipt
import com.example.sande.data.entities.SaleItem
import com.example.sande.data.entities.SaleSession
import kotlinx.coroutines.tasks.await
import java.io.ByteArrayOutputStream

/**
 * ESC/POS commands for thermal printers.
 * Supports 58mm (32 chars) and 80mm (48 chars) paper widths.
 */
class ThermalPrinterFormatter {

    companion object {
        // ESC/POS command bytes
        private const val ESC = 0x1B
        private const val GS = 0x1D
        private const val LF = 0x0A
        private const val CR = 0x0D

        // Initialize printer
        private val INIT = byteArrayOf(ESC, 0x40)

        // Alignment
        private val ALIGN_LEFT = byteArrayOf(ESC, 0x61, 0x00)
        private val ALIGN_CENTER = byteArrayOf(ESC, 0x61, 0x01)
        private val ALIGN_RIGHT = byteArrayOf(ESC, 0x61, 0x02)

        // Font styles
        private val FONT_A = byteArrayOf(ESC, 0x4D, 0x00) // Font A (12x24)
        private val FONT_B = byteArrayOf(ESC, 0x4D, 0x01) // Font B (9x17)
        private val BOLD_ON = byteArrayOf(ESC, 0x45, 0x01)
        private val BOLD_OFF = byteArrayOf(ESC, 0x45, 0x00)
        private val UNDERLINE_ON = byteArrayOf(ESC, 0x2D, 0x01)
        private val UNDERLINE_OFF = byteArrayOf(ESC, 0x2D, 0x00)
        private val DOUBLE_HEIGHT_ON = byteArrayOf(ESC, 0x21, 0x10)
        private val DOUBLE_HEIGHT_OFF = byteArrayOf(ESC, 0x21, 0x00)
        private val DOUBLE_WIDTH_ON = byteArrayOf(ESC, 0x21, 0x20)
        private val DOUBLE_WIDTH_OFF = byteArrayOf(ESC, 0x21, 0x00)

        // Barcode
        private val BARCODE_HEIGHT = byteArrayOf(GS, 0x68, 0x50)
        private val BARCODE_WIDTH = byteArrayOf(GS, 0x77, 0x02)
        private val BARCODE_HRI_BELOW = byteArrayOf(GS, 0x48, 0x02)
        private val BARCODE_CODE128 = byteArrayOf(GS, 0x6B, 0x49)

        // Cut paper
        private val CUT_PAPER = byteArrayOf(GS, 0x56, 0x00)
        private val CUT_PARTIAL = byteArrayOf(GS, 0x56, 0x01)

        // Feed lines
        private val FEED_LINE = byteArrayOf(ESC, 0x64, 0x03)
    }

    data class PrinterProfile(
        val name: String,
        val widthChars: Int, // Characters per line
        val widthDots: Int, // Dots per line (for graphics)
        val maxCharsPerLine: Int
    ) {
        companion object {
            val PROFILE_58MM = PrinterProfile(
                name = "58mm",
                widthChars = 32,
                widthDots = 384,
                maxCharsPerLine = 32
            )
            val PROFILE_80MM = PrinterProfile(
                name = "80mm",
                widthChars = 48,
                widthDots = 576,
                maxCharsPerLine = 48
            )
        }
    }

    private val profile: PrinterProfile

    constructor(profile: PrinterProfile = PrinterProfile.PROFILE_58MM) {
        this.profile = profile
    }

    /**
     * Formats a receipt for thermal printing.
     * Returns byte array ready to send to printer.
     */
    fun formatReceipt(
        session: SaleSession,
        items: List<SaleItem>,
        business: BusinessProfile,
        receipt: Receipt
    ): ByteArray {
        val out = ByteArrayOutputStream()
        val w = profile.maxCharsPerLine

        fun write(bytes: ByteArray) = out.write(bytes)
        fun writeText(text: String) = out.write(text.toByteArray())
        fun writeLine(text: String = "") {
            out.write(text.toByteArray())
            out.write(LF)
        }

        // Initialize
        write(INIT)

        // Header
        write(ALIGN_CENTER)
        write(BOLD_ON)
        write(DOUBLE_HEIGHT_ON)
        writeText(business.name)
        write(DOUBLE_HEIGHT_OFF)
        write(BOLD_OFF)
        writeLine()

        if (business.address != null && business.address.isNotBlank()) {
            writeLine(business.address!!)
        }
        if (business.phone != null && business.phone.isNotBlank()) {
            writeLine("Tel: ${business.phone}")
        }
        writeLine("-".repeat(w))

        // Receipt title
        write(ALIGN_CENTER)
        write(BOLD_ON)
        writeLine("RECEIPT")
        write(BOLD_OFF)
        writeLine("-".repeat(w))

        // Receipt info
        write(ALIGN_LEFT)
        writeLine(padBoth("Receipt #:", receipt.receiptNumber))
        writeLine(padBoth("Date:", java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date(session.createdAt))))
        writeLine(padBoth("Cashier:", "${session.salespersonId} (${session.salespersonRole})"))
        writeLine(padBoth("Device:", session.deviceCode))
        writeLine("-".repeat(w))

        // Items header
        write(ALIGN_CENTER)
        write(BOLD_ON)
        writeLine("ITEMS")
        write(BOLD_OFF)
        writeLine("-".repeat(w))

        // Items
        write(ALIGN_LEFT)
        write(FONT_B) // Smaller font for items
        items.forEach { item ->
            val name = truncate(item.productName, w)
            writeLine(name)
            val qtyPrice = "${item.quantity} x ${formatCurrency(item.unitPriceCents)}"
            val total = formatCurrency(item.totalCents)
            writeLine(padBoth(qtyPrice, total))
        }
        write(FONT_A)
        writeLine("-".repeat(w))

        // Totals
        writeLine(padBoth("Subtotal:", formatCurrency(session.subtotalCents)))
        if (session.taxCents > 0) {
            writeLine(padBoth("Tax:", formatCurrency(session.taxCents)))
        }
        writeLine("-".repeat(w))
        write(BOLD_ON)
        write(DOUBLE_HEIGHT_ON)
        writeLine(padBoth("TOTAL:", formatCurrency(session.totalCents)))
        write(DOUBLE_HEIGHT_OFF)
        write(BOLD_OFF)
        writeLine("-".repeat(w))

        // Payment
        writeLine(padBoth("Payment:", "Cash"))
        writeLine(padBoth("Change:", formatCurrency(0)))
        writeLine("-".repeat(w))

        // Footer
        write(ALIGN_CENTER)
        if (business.receiptFooter != null && business.receiptFooter.isNotBlank()) {
            writeLine(business.receiptFooter!!)
        }
        writeLine("Thank you for shopping!")
        writeLine("Powered by SANDE")

        // Optional barcode of receipt number
        // write(BARCODE_HEIGHT)
        // write(BARCODE_WIDTH)
        // write(BARCODE_HRI_BELOW)
        // write(BARCODE_CODE128)
        // writeText(receipt.receiptNumber)
        // writeText("$LF")

        // Feed and cut
        write(FEED_LINE)
        write(CUT_PARTIAL)

        return out.toByteArray()
    }

    private fun padBoth(left: String, right: String): String {
        val leftText = if (left.length > w - right.length - 1) {
            left.substring(0, w - right.length - 1)
        } else left
        val spaces = w - leftText.length - right.length
        return leftText + " ".repeat(maxOf(spaces, 0)) + right
    }

    private fun truncate(text: String, maxLen: Int): String {
        return if (text.length <= maxLen) text else text.substring(0, maxLen - 3) + "..."
    }

    private fun formatCurrency(cents: Long): String {
        val amount = cents / 100.0
        return java.text.NumberFormat.getCurrencyInstance(java.util.Locale.getDefault()).format(amount)
    }

    /**
     * Creates a test print (for printer verification).
     */
    fun formatTestPrint(): ByteArray {
        val out = ByteArrayOutputStream()
        val w = profile.maxCharsPerLine

        fun write(bytes: ByteArray) = out.write(bytes)
        fun writeLine(text: String = "") {
            out.write(text.toByteArray())
            out.write(LF)
        }

        write(INIT)
        write(ALIGN_CENTER)
        write(BOLD_ON)
        write(DOUBLE_HEIGHT_ON)
        writeLine("TEST PRINT")
        write(DOUBLE_HEIGHT_OFF)
        write(BOLD_OFF)
        writeLine("-".repeat(w))
        writeLine("Printer: ${profile.name}")
        writeLine("Width: ${profile.widthChars} chars")
        writeLine("Date: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm").format(java.util.Date())}")
        writeLine("-".repeat(w))
        write(ALIGN_LEFT)
        writeLine("Font A (normal)")
        write(FONT_B)
        writeLine("Font B (small)")
        write(FONT_A)
        write(BOLD_ON)
        writeLine("Bold text")
        write(BOLD_OFF)
        write(UNDERLINE_ON)
        writeLine("Underlined")
        write(UNDERLINE_OFF)
        writeLine("-".repeat(w))
        write(ALIGN_CENTER)
        writeLine("Alignment: Center")
        write(ALIGN_LEFT)
        writeLine("Alignment: Left")
        write(ALIGN_RIGHT)
        writeLine("Alignment: Right")
        write(ALIGN_LEFT)
        writeLine("-".repeat(w))
        write(FEED_LINE)
        write(CUT_PARTIAL)

        return out.toByteArray()
    }
}

/**
 * Bluetooth printer helper for Android.
 * Handles connection and sending bytes to paired Bluetooth printer.
 */
class BluetoothPrinterHelper {

    /**
     * Sends bytes to a bonded Bluetooth device.
     * Returns true if sent successfully.
     */
    suspend fun printToBluetooth(
        deviceAddress: String,
        data: ByteArray,
        onStatus: (String) -> Unit
    ): Boolean = try {
        onStatus("Connecting to printer...")
        // Implementation would use Android BluetoothSocket
        // This is a placeholder - actual implementation needs Bluetooth permissions
        // and runs on IO dispatcher
        onStatus("Printer not implemented yet")
        false
    } catch (e: Exception) {
        onStatus("Print failed: ${e.message}")
        false
    }
}
