package com.rainweaver.salaryslipmaker.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

/** Exact decimal salary arithmetic; Double/Float is never used for money. */
enum class SalaryLineKind { EARNING, DEDUCTION }

data class SalaryLine(
    val label: String,
    val kind: SalaryLineKind,
    val amount: BigDecimal,
)

data class SalaryTotals(
    val grossEarnings: BigDecimal,
    val totalDeductions: BigDecimal,
    val netPay: BigDecimal,
) {
    val negativeNet: Boolean get() = netPay.signum() < 0
}

object SalaryMath {
    private val amountPattern = Regex("(?:[0-9]{1,3}(?:,[0-9]{3})+|[0-9]+)(?:\\.[0-9]{1,6})?")

    /** Accepts plain digits or correctly grouped commas; a period is the decimal separator. */
    fun parseAmount(raw: String): BigDecimal? {
        val input = raw.trim()
        if (input.isEmpty() || input.length > 64 || !amountPattern.matches(input)) return null
        return runCatching { BigDecimal(input.replace(",", "")) }.getOrNull()
    }

    fun calculate(lines: List<SalaryLine>): SalaryTotals {
        require(lines.all { it.label.isNotBlank() }) { "Every salary line needs a label" }
        require(lines.all { it.amount.signum() >= 0 }) { "Salary line amounts cannot be negative" }
        val earnings = lines.asSequence()
            .filter { it.kind == SalaryLineKind.EARNING }
            .fold(BigDecimal.ZERO) { total, line -> total.add(line.amount) }
        val deductions = lines.asSequence()
            .filter { it.kind == SalaryLineKind.DEDUCTION }
            .fold(BigDecimal.ZERO) { total, line -> total.add(line.amount) }
        return SalaryTotals(earnings, deductions, earnings.subtract(deductions))
    }
}

/** Shared display formatter used by both Compose summaries and PDF output. */
object SalaryMoneyFormatter {
    fun fractionDigits(currencyCode: String): Int {
        val currency = runCatching { Currency.getInstance(currencyCode.trim().uppercase(Locale.ROOT)) }.getOrNull()
        return (currency?.defaultFractionDigits ?: 2).coerceIn(0, 6)
    }

    fun format(value: BigDecimal, currencyCode: String, locale: Locale = Locale.getDefault()): String {
        val code = currencyCode.trim().uppercase(Locale.ROOT)
        val currency = runCatching { Currency.getInstance(code) }.getOrNull()
        val digits = fractionDigits(code)
        val rounded = value.setScale(digits, RoundingMode.HALF_UP)
        val formatter = if (currency != null) {
            NumberFormat.getCurrencyInstance(locale).apply {
                this.currency = currency
                minimumFractionDigits = digits
                maximumFractionDigits = digits
                roundingMode = RoundingMode.HALF_UP
            }
        } else {
            NumberFormat.getNumberInstance(locale).apply {
                minimumFractionDigits = digits
                maximumFractionDigits = digits
                roundingMode = RoundingMode.HALF_UP
            }
        }
        val number = formatter.format(rounded)
        return if (currency != null || code.isEmpty()) number else "$code $number"
    }
}
