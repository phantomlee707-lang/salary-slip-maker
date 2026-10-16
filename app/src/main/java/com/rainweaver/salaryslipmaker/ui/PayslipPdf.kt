package com.rainweaver.salaryslipmaker.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import androidx.core.content.FileProvider
import com.rainweaver.salaryslipmaker.data.PayslipBundle
import com.rainweaver.salaryslipmaker.data.PayslipEntity
import com.rainweaver.salaryslipmaker.domain.PayslipStatus
import com.rainweaver.salaryslipmaker.domain.SalaryLineKind
import com.rainweaver.salaryslipmaker.domain.SalaryMath
import com.rainweaver.salaryslipmaker.domain.SalaryMoneyFormatter
import java.io.File
import java.io.FileOutputStream
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val PAGE_WIDTH = 595
private const val PAGE_HEIGHT = 842
private const val MARGIN = 42f

suspend fun sharePayslipPdf(context: Context, bundle: PayslipBundle) {
    val file = createPayslipPdf(context, bundle)
    withContext(Dispatchers.Main) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Salary slip ${bundle.payslip.number}")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share salary slip"))
    }
}

suspend fun exportPayslipPdf(context: Context, bundle: PayslipBundle, target: Uri) {
    val file = createPayslipPdf(context, bundle)
    withContext(Dispatchers.IO) {
        val out = context.contentResolver.openOutputStream(target, "w") ?: error("Could not open the selected destination")
        out.use { stream -> file.inputStream().use { it.copyTo(stream) } }
    }
}

suspend fun printPayslipPdf(context: Context, bundle: PayslipBundle) {
    val file = createPayslipPdf(context, bundle)
    withContext(Dispatchers.Main) {
        val manager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
        manager.print("Salary slip ${bundle.payslip.number}", object : PrintDocumentAdapter() {
            override fun onLayout(oldAttributes: PrintAttributes?, newAttributes: PrintAttributes, cancellationSignal: CancellationSignal, callback: LayoutResultCallback, extras: Bundle?) {
                if (cancellationSignal.isCanceled) callback.onLayoutCancelled()
                else callback.onLayoutFinished(
                    PrintDocumentInfo.Builder("${bundle.payslip.number}.pdf")
                        .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                        .setPageCount(PrintDocumentInfo.PAGE_COUNT_UNKNOWN)
                        .build(), true,
                )
            }

            override fun onWrite(pages: Array<out PageRange>, destination: ParcelFileDescriptor, cancellationSignal: CancellationSignal, callback: WriteResultCallback) {
                try {
                    if (cancellationSignal.isCanceled) { callback.onWriteCancelled(); return }
                    FileOutputStream(destination.fileDescriptor).use { output -> file.inputStream().use { it.copyTo(output) } }
                    callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                } catch (error: Exception) { callback.onWriteFailed(error.message) }
            }
        }, PrintAttributes.Builder().setMediaSize(PrintAttributes.MediaSize.ISO_A4).build())
    }
}

private suspend fun createPayslipPdf(context: Context, bundle: PayslipBundle): File = withContext(Dispatchers.IO) {
    require(bundle.payslip.status == PayslipStatus.ISSUED) { "Only issued payslips can be exported" }
    require(bundle.invalidLineCount == 0) { "Payslip contains invalid salary lines" }
    val slip = bundle.payslip
    val totals = SalaryMath.calculate(bundle.lines)
    require(!totals.negativeNet) { "Net pay cannot be negative" }
    require(decimal(slip.grossEarnings).compareTo(totals.grossEarnings) == 0 &&
        decimal(slip.totalDeductions).compareTo(totals.totalDeductions) == 0 &&
        decimal(slip.netPay).compareTo(totals.netPay) == 0) { "Saved totals do not match salary lines" }

    val directory = File(context.cacheDir, "payslips").apply { mkdirs() }
    val file = File(directory, "${slip.number}.pdf")
    val document = PdfDocument()
    var bitmap: Bitmap? = null
    try {
        val accent = runCatching { android.graphics.Color.parseColor(slip.businessAccentColorHex) }.getOrDefault(android.graphics.Color.rgb(29, 91, 84))
        val regular = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.rgb(40, 47, 45); textSize = 10.5f }
        val muted = Paint(regular).apply { color = android.graphics.Color.rgb(100, 107, 104); textSize = 9f }
        val bold = Paint(regular).apply { textSize = 12f; typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD) }
        val title = Paint(bold).apply { textSize = 21f; color = accent }
        val rule = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accent; strokeWidth = 1.2f }
        var pageNumber = 1
        var page = document.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
        var canvas: Canvas = page.canvas
        var y = 44f
        bitmap = loadLogo(context, slip.businessLogoUri)

        fun footer() {
            val p = Paint(muted).apply { textAlign = Paint.Align.CENTER; textSize = 8f }
            canvas.drawLine(MARGIN, PAGE_HEIGHT - 34f, PAGE_WIDTH - MARGIN, PAGE_HEIGHT - 34f, Paint(rule).apply { color = android.graphics.Color.LTGRAY; strokeWidth = .6f })
            canvas.drawText("Salary Slip Maker · ${slip.number} · Page $pageNumber", PAGE_WIDTH / 2f, PAGE_HEIGHT - 19f, p)
        }
        fun nextPage() {
            footer(); document.finishPage(page); pageNumber++
            page = document.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
            canvas = page.canvas; y = MARGIN
            canvas.drawText("Payslip ${slip.number} — continued", MARGIN, y, bold); y += 24f
            canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, rule); y += 18f
        }
        fun ensureSpace(height: Float) { if (y + height > PAGE_HEIGHT - 55f) nextPage() }
        fun drawWrapped(text: String, paint: Paint, x: Float = MARGIN, maxWidth: Float = PAGE_WIDTH - MARGIN * 2, lineHeight: Float = 15f) {
            wrap(text, paint, maxWidth).forEach { segment -> ensureSpace(lineHeight); canvas.drawText(segment, x, y, paint); y += lineHeight }
        }
        fun drawValue(label: String, value: String) {
            val valueLines = wrap(value, regular, PAGE_WIDTH - MARGIN * 2 - 170f)
            ensureSpace(valueLines.size.coerceAtLeast(1) * 18f)
            canvas.drawText(label, MARGIN, y, muted)
            valueLines.forEach { segment ->
                canvas.drawText(segment, PAGE_WIDTH - MARGIN - regular.measureText(segment), y, regular)
                y += 18f
            }
        }

        canvas.drawRect(0f, 0f, PAGE_WIDTH.toFloat(), 8f, Paint().apply { color = accent })
        canvas.drawText("SALARY SLIP", MARGIN, y + 5f, title)
        canvas.drawText(slip.number, PAGE_WIDTH - MARGIN - bold.measureText(slip.number), y, bold); y += 28f
        val brandWidth = PAGE_WIDTH - MARGIN * 2 - if (bitmap != null) 72f else 0f
        drawWrapped(slip.businessName.ifBlank { "Business name not provided" }, bold, maxWidth = brandWidth)
        listOf(slip.businessAddress, slip.businessPhone, slip.businessEmail, slip.businessRegistrationId, slip.businessAdditionalInfo)
            .filter(String::isNotBlank).forEach { drawWrapped(it, muted, maxWidth = brandWidth) }
        bitmap?.let { logo -> canvas.drawBitmap(logo, null, RectF(PAGE_WIDTH - MARGIN - 58f, 44f, PAGE_WIDTH - MARGIN, 102f), Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)) }
        y += 8f; canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, rule); y += 20f
        drawWrapped("EMPLOYEE", bold); drawWrapped(slip.employeeName, regular)
        listOf(slip.employeeIdentifier, slip.employeeJobTitle, slip.employeeDepartment).filter(String::isNotBlank).forEach { drawWrapped(it, muted) }
        y += 7f
        drawValue("Pay period", "${day(slip.periodStartDay)} – ${day(slip.periodEndDay)}")
        drawValue("Payment date", day(slip.paymentDay))
        if (slip.paymentMethod.isNotBlank()) drawValue("Payment method", slip.paymentMethod)
        if (slip.paymentReference.isNotBlank()) drawValue("Payment reference", slip.paymentReference)
        slip.issuedAt?.let { drawValue("Issued", DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(it))) }
        slip.supersedesPayslipNumber?.let { drawValue("Correction of payslip", it) }
        y += 8f; ensureSpace(24f)
        canvas.drawRect(MARGIN, y - 13f, PAGE_WIDTH - MARGIN, y + 5f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.rgb(235, 242, 240) })
        canvas.drawText("EARNINGS AND DEDUCTIONS", MARGIN + 5f, y, bold)
        canvas.drawText("AMOUNT", PAGE_WIDTH - MARGIN - bold.measureText("AMOUNT") - 5f, y, bold); y += 22f
        bundle.lines.forEach { line ->
            val prefix = if (line.kind == SalaryLineKind.DEDUCTION) "Deduction · " else "Earning · "
            val amountLines = wrap(SalaryMoneyFormatter.format(line.amount, slip.currencyCode), regular, 155f)
            val labelLines = wrap(prefix + line.label, regular, PAGE_WIDTH - MARGIN * 2 - 165f)
            val rowLines = maxOf(1, amountLines.size, labelLines.size)
            repeat(rowLines) { index ->
                ensureSpace(16f)
                labelLines.getOrNull(index)?.let { canvas.drawText(it, MARGIN + 5f, y, regular) }
                amountLines.getOrNull(index)?.let { segment -> canvas.drawText(segment, PAGE_WIDTH - MARGIN - 5f - regular.measureText(segment), y, regular) }
                y += 16f
            }
            y += 3f
        }
        y += 4f; canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, rule); y += 20f
        drawValue("Gross earnings", SalaryMoneyFormatter.format(totals.grossEarnings, slip.currencyCode))
        drawValue("Total deductions", SalaryMoneyFormatter.format(totals.totalDeductions, slip.currencyCode))
        ensureSpace(32f)
        canvas.drawRect(MARGIN, y - 14f, PAGE_WIDTH - MARGIN, y + 8f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accent })
        val whiteBold = Paint(bold).apply { color = android.graphics.Color.WHITE; textSize = 13f }
        canvas.drawText("NET PAY", MARGIN + 7f, y + 1f, whiteBold)
        val net = SalaryMoneyFormatter.format(totals.netPay, slip.currencyCode)
        canvas.drawText(net, PAGE_WIDTH - MARGIN - 7f - whiteBold.measureText(net), y + 1f, whiteBold); y += 32f
        if (slip.notes.isNotBlank()) { ensureSpace(32f); drawWrapped("NOTES", bold); drawWrapped(slip.notes, regular) }
        ensureSpace(50f); drawWrapped("This document records amounts entered by the employer. Tax and statutory payroll calculations are not included.", muted)
        footer(); document.finishPage(page)
        FileOutputStream(file).use { document.writeTo(it) }
    } finally { document.close(); bitmap?.takeIf { !it.isRecycled }?.recycle() }
    file
}

private fun decimal(value: String): BigDecimal = value.toBigDecimalOrNull() ?: error("Saved total is invalid")
private fun day(epochDay: Long?): String = epochDay?.let { runCatching { LocalDate.ofEpochDay(it).format(DateTimeFormatter.ISO_LOCAL_DATE) }.getOrNull() } ?: "—"

private fun loadLogo(context: Context, raw: String?): Bitmap? {
    if (raw.isNullOrBlank()) return null
    return runCatching {
        val uri = Uri.parse(raw)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        val sample = (maxOf(bounds.outWidth, bounds.outHeight) / 256).coerceAtLeast(1)
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
    }.getOrNull()
}

private fun wrap(text: String, paint: Paint, width: Float): List<String> {
    if (text.isBlank()) return emptyList()
    val result = mutableListOf<String>(); var line = StringBuilder()
    for (word in text.trim().split(Regex("\\s+"))) {
        if (paint.measureText(word) > width) {
            if (line.isNotEmpty()) { result += line.toString(); line = StringBuilder() }
            var piece = StringBuilder()
            word.forEach { ch ->
                if (paint.measureText(piece.toString() + ch) > width && piece.isNotEmpty()) { result += piece.toString(); piece = StringBuilder() }
                piece.append(ch)
            }
            if (piece.isNotEmpty()) line.append(piece)
        } else if (line.isEmpty()) line.append(word)
        else if (paint.measureText("$line $word") <= width) line.append(" ").append(word)
        else { result += line.toString(); line = StringBuilder(word) }
    }
    if (line.isNotEmpty()) result += line.toString()
    return result
}
