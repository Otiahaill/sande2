package com.example.sande.data.receipt

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.CancellationSignal
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.print.pdf.PrintedPdfDocument
import android.util.Log
import com.example.sande.data.entities.BusinessProfile
import com.example.sande.data.entities.Receipt
import com.example.sande.data.entities.SaleItem
import com.example.sande.data.entities.SaleSession

/**
 * PDF receipt renderer using Android's PdfDocument API.
 * Supports A4 and A5 paper sizes.
 */
class PdfReceiptRenderer(private val context: Context) {

    enum class PaperSize {
        A4, A5, THERMAL_58MM, THERMAL_80MM
    }

    data class RenderResult(
        val filePath: String,
        val pageCount: Int
    )

    /**
     * Renders a receipt to a PDF file.
     */
    suspend fun renderToFile(
        session: SaleSession,
        items: List<SaleItem>,
        business: BusinessProfile,
        receipt: Receipt,
        paperSize: PaperSize = PaperSize.A4,
        fileName: String = "receipt_${receipt.receiptNumber}.pdf"
    ): RenderResult = try {
        val document = createDocument(paperSize)
        val pageInfo = getPageInfo(paperSize)
        val page = document.startPage(pageInfo)
        val canvas = page.canvas

        drawReceipt(canvas, pageInfo, session, items, business, receipt, paperSize)

        document.finishPage(page)

        val file = context.getExternalFilesDir(null)?.resolve(fileName)
            ?: throw IllegalStateException("No external files dir")

        file.outputStream().use { output ->
            document.writeTo(output)
        }
        document.close()

        RenderResult(file.absolutePath, 1)
    } catch (e: Exception) {
        Log.e("PdfReceiptRenderer", "Failed to render PDF", e)
        throw e
    }

    /**
     * Prints the receipt using Android's Print framework.
     * Shows system print dialog allowing user to choose printer (Bluetooth, network, Save as PDF, etc.)
     */
    fun printReceipt(
        session: SaleSession,
        items: List<SaleItem>,
        business: BusinessProfile,
        receipt: Receipt,
        jobName: String = "Receipt ${receipt.receiptNumber}",
        paperSize: PaperSize = PaperSize.A4
    ) {
        val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
        val adapter = ReceiptPrintDocumentAdapter(session, items, business, receipt, paperSize)
        printManager.print(jobName, adapter, null)
    }

    private fun createDocument(paperSize: PaperSize): PdfDocument {
        return when (paperSize) {
            PaperSize.A4, PaperSize.A5 -> PrintedPdfDocument(context, PrintAttributes.Builder().build())
            else -> PdfDocument()
        }
    }

    private fun getPageInfo(paperSize: PaperSize): PdfDocument.PageInfo {
        return when (paperSize) {
            PaperSize.A4 -> PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4 in points (72 DPI)
            PaperSize.A5 -> PdfDocument.PageInfo.Builder(420, 595, 1).create() // A5 in points
            PaperSize.THERMAL_58MM -> PdfDocument.PageInfo.Builder(226, 842, 1).create() // ~58mm wide, tall
            PaperSize.THERMAL_80MM -> PdfDocument.PageInfo.Builder(339, 842, 1).create() // ~80mm wide, tall
        }
    }

    private fun drawReceipt(
        canvas: Canvas,
        pageInfo: PdfDocument.PageInfo,
        session: SaleSession,
        items: List<SaleItem>,
        business: BusinessProfile,
        receipt: Receipt,
        paperSize: PaperSize
    ) {
        val width = pageInfo.pageWidth.toFloat()
        val height = pageInfo.pageHeight.toFloat()
        val margin = 20f
        val contentWidth = width - 2 * margin
        var y = margin

        val paint = Paint().apply { isAntiAlias = true }
        val fontSize = if (paperSize == PaperSize.THERMAL_58MM || paperSize == PaperSize.THERMAL_80MM) 10f else 12f
        val titleSize = fontSize + 4f
        val headerSize = fontSize + 2f

        fun drawText(text: String, x: Float, yPos: Float, size: Float = fontSize, align: Paint.Align = Paint.Align.LEFT, bold: Boolean = false, color: Int = Color.BLACK) {
            paint.textSize = size
            paint.isFakeBoldText = bold
            paint.color = color
            paint.textAlign = align
            canvas.drawText(text, x, yPos, paint)
        }

        fun drawLine(yPos: Float, color: Int = Color.BLACK, strokeWidth: Float = 1f) {
            paint.color = color
            paint.strokeWidth = strokeWidth
            paint.style = Paint.Style.STROKE
            canvas.drawLine(margin, yPos, width - margin, yPos, paint)
            paint.style = Paint.Style.FILL
        }

        // Business name
        drawText(business.name, width / 2, y, titleSize, Paint.Align.CENTER, true)
        y += titleSize + 8

        // Address
        if (business.address != null && business.address.isNotBlank()) {
            drawText(business.address!!, width / 2, y, fontSize - 2, Paint.Align.CENTER)
            y += fontSize + 4
        }

        // Phone
        if (business.phone != null && business.phone.isNotBlank()) {
            drawText("Tel: ${business.phone}", width / 2, y, fontSize - 2, Paint.Align.CENTER)
            y += fontSize + 4
        }

        y += 4
        drawLine(y)
        y += 8

        // RECEIPT title
        drawText("RECEIPT", width / 2, y, headerSize, Paint.Align.CENTER, true)
        y += headerSize + 4
        drawLine(y)
        y += 8

        // Receipt info
        val infoPairs = listOf(
            "Receipt #:" to receipt.receiptNumber,
            "Date:" to java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date(session.createdAt)),
            "Cashier:" to "${session.salespersonId} (${session.salespersonRole})",
            "Device:" to session.deviceCode
        )

        infoPairs.forEach { (label, value) ->
            drawText(label, margin, y, fontSize, bold = true)
            drawText(value, width - margin, y, fontSize, Paint.Align.RIGHT)
            y += fontSize + 4
        }

        y += 4
        drawLine(y)
        y += 8

        // Items header
        drawText("ITEMS", width / 2, y, headerSize, Paint.Align.CENTER, true)
        y += headerSize + 4
        drawLine(y)
        y += 8

        // Column headers
        val colItem = margin
        val colQty = width * 0.6f
        val colPrice = width * 0.75f
        val colTotal = width - margin

        drawText("Item", colItem, y, fontSize - 1, bold = true)
        drawText("Qty", colQty, y, fontSize - 1, Paint.Align.RIGHT, true)
        drawText("Price", colPrice, y, fontSize - 1, Paint.Align.RIGHT, true)
        drawText("Total", colTotal, y, fontSize - 1, Paint.Align.RIGHT, true)
        y += fontSize + 4
        drawLine(y, Color.DKGRAY, 0.5f)
        y += 6

        // Items
        items.forEach { item ->
            // Check if we need a new page (for multi-page receipts)
            if (y > height - margin - 100) {
                // Would need multi-page support here
            }

            val name = if (item.productName.length > 30) item.productName.substring(0, 27) + "..." else item.productName
            drawText(name, colItem, y, fontSize)
            y += fontSize + 2

            drawText("${item.quantity}", colQty, y, fontSize, Paint.Align.RIGHT)
            drawText(formatCurrency(item.unitPriceCents, business.currencyCode), colPrice, y, fontSize, Paint.Align.RIGHT)
            drawText(formatCurrency(item.totalCents, business.currencyCode), colTotal, y, fontSize, Paint.Align.RIGHT)
            y += fontSize + 6
        }

        y += 4
        drawLine(y)
        y += 8

        // Totals
        val labelX = width * 0.6f
        val valueX = width - margin

        drawText("Subtotal:", labelX, y, fontSize, Paint.Align.RIGHT, true)
        drawText(formatCurrency(session.subtotalCents, business.currencyCode), valueX, y, fontSize, Paint.Align.RIGHT)
        y += fontSize + 4

        if (session.taxCents > 0) {
            drawText("Tax:", labelX, y, fontSize, Paint.Align.RIGHT, true)
            drawText(formatCurrency(session.taxCents, business.currencyCode), valueX, y, fontSize, Paint.Align.RIGHT)
            y += fontSize + 4
        }

        drawLine(y, Color.BLACK, 2f)
        y += 8

        drawText("TOTAL:", labelX, y, titleSize, Paint.Align.RIGHT, true)
        drawText(formatCurrency(session.totalCents, business.currencyCode), valueX, y, titleSize, Paint.Align.RIGHT)
        y += titleSize + 8

        drawLine(y)
        y += 8

        // Payment
        drawText("Payment:", labelX, y, fontSize, Paint.Align.RIGHT, true)
        drawText("Cash", valueX, y, fontSize, Paint.Align.RIGHT)
        y += fontSize + 4
        drawText("Change:", labelX, y, fontSize, Paint.Align.RIGHT, true)
        drawText(formatCurrency(0, business.currencyCode), valueX, y, fontSize, Paint.Align.RIGHT)
        y += fontSize + 8

        drawLine(y)
        y += 8

        // Footer
        if (business.receiptFooter != null && business.receiptFooter.isNotBlank()) {
            drawText(business.receiptFooter!!, width / 2, y, fontSize - 2, Paint.Align.CENTER)
            y += fontSize + 4
        }

        drawText("Thank you for shopping!", width / 2, y, fontSize, Paint.Align.CENTER)
        y += fontSize + 4
        drawText("Powered by SANDE", width / 2, y, fontSize - 2, Paint.Align.CENTER, false, Color.DKGRAY)
    }

    private fun formatCurrency(cents: Long, currencyCode: String): String =
        formatCurrency(cents, currencyCode)

    /**
     * PrintDocumentAdapter for Android Print framework.
     */
    private inner class ReceiptPrintDocumentAdapter(
        private val session: SaleSession,
        private val items: List<SaleItem>,
        private val business: BusinessProfile,
        private val receipt: Receipt,
        private val paperSize: PaperSize
    ) : PrintDocumentAdapter() {

        private var pdfDocument: PdfDocument? = null

        override fun onStart() {
            pdfDocument = createDocument(paperSize)
        }

        override fun onLayout(
            oldAttributes: PrintAttributes?,
            newAttributes: PrintAttributes,
            cancellationSignal: CancellationSignal,
            callback: LayoutResultCallback,
            extras: android.os.Bundle?
        ) {
            val info = PrintDocumentInfo.Builder("receipt_${receipt.receiptNumber}.pdf")
                .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                .setPageCount(1)
                .build()
            callback.onLayoutFinished(info, true)
        }

        override fun onWrite(
            pageRanges: Array<PageRange>,
            destination: android.os.ParcelFileDescriptor,
            cancellationSignal: CancellationSignal,
            callback: WriteResultCallback
        ) {
            try {
                val document = pdfDocument ?: createDocument(paperSize)
                val pageInfo = getPageInfo(paperSize)
                val page = document.startPage(pageInfo)
                drawReceipt(page.canvas, pageInfo, session, items, business, receipt, paperSize)
                document.finishPage(page)

                document.writeTo(java.io.FileOutputStream(destination.fileDescriptor))
                document.close()
                pdfDocument = null

                callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
            } catch (e: Exception) {
                callback.onWriteFailed(e.message)
            }
        }
    }
}
