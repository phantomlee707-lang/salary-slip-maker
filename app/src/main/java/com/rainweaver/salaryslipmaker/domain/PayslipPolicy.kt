package com.rainweaver.salaryslipmaker.domain

import java.time.LocalDate
import java.time.YearMonth

enum class PayslipStatus { DRAFT, ISSUED }

data class PayslipIssueInput(
    val businessName: String,
    val employeeName: String,
    val periodStartDay: Long?,
    val periodEndDay: Long?,
    val paymentDay: Long?,
    val currencyCode: String,
    val lines: List<SalaryLine>,
    val savedGrossEarnings: String? = null,
    val savedTotalDeductions: String? = null,
    val savedNetPay: String? = null,
)

data class PayslipDraftSnapshot(
    val periodStartDay: Long?,
    val periodEndDay: Long?,
    val paymentDay: Long?,
    val currencyCode: String,
    val paymentMethod: String,
    val paymentReference: String,
    val notes: String,
    val lines: List<SalaryLine>,
)

object PayslipPolicy {
    const val ERROR_BUSINESS = "Business name is required"
    const val ERROR_EMPLOYEE = "Employee name is required"
    const val ERROR_DATES = "Enter a valid pay period and payment date"
    const val ERROR_CURRENCY = "Currency is required"
    const val ERROR_EARNING = "Add at least one earning line"
    const val ERROR_LINES = "Check each salary line and amount"
    const val ERROR_PRECISION = "One or more amounts use more decimal places than this currency supports"
    const val ERROR_NEGATIVE_NET = "Deductions cannot exceed earnings"
    const val ERROR_SAVED_TOTALS = "Save the draft so its totals match the salary lines before issuing"
    const val ERROR_SAVED_CHANGES = "Save draft changes before issuing"

    fun issueErrors(input: PayslipIssueInput): List<String> = buildList {
        if (input.businessName.isBlank()) add(ERROR_BUSINESS)
        if (input.employeeName.isBlank()) add(ERROR_EMPLOYEE)
        val start = input.periodStartDay
        val end = input.periodEndDay
        val payment = input.paymentDay
        if (start == null || end == null || payment == null || start > end ||
            !validEpochDay(start) || !validEpochDay(end) || !validEpochDay(payment)
        ) add(ERROR_DATES)
        if (input.currencyCode.isBlank()) add(ERROR_CURRENCY)
        if (input.lines.none { it.kind == SalaryLineKind.EARNING && it.amount.signum() > 0 }) add(ERROR_EARNING)
        if (input.lines.any { it.amount.stripTrailingZeros().scale().coerceAtLeast(0) > SalaryMoneyFormatter.fractionDigits(input.currencyCode) }) add(ERROR_PRECISION)
        val totals = runCatching { SalaryMath.calculate(input.lines) }.getOrNull()
        if (totals == null) add(ERROR_LINES)
        else {
            if (totals.negativeNet) add(ERROR_NEGATIVE_NET)
            if (listOf(input.savedGrossEarnings, input.savedTotalDeductions, input.savedNetPay).any { it != null } &&
                (!matches(input.savedGrossEarnings, totals.grossEarnings) ||
                    !matches(input.savedTotalDeductions, totals.totalDeductions) ||
                    !matches(input.savedNetPay, totals.netPay))
            ) add(ERROR_SAVED_TOTALS)
        }
    }

    fun isSnapshotCurrent(saved: PayslipDraftSnapshot, edited: PayslipDraftSnapshot): Boolean = saved == edited

    fun periodMatches(startDay: Long?, endDay: Long?, selectedMonth: YearMonth): Boolean {
        val start = startDay?.let { runCatching { LocalDate.ofEpochDay(it) }.getOrNull() } ?: return false
        val end = endDay?.let { runCatching { LocalDate.ofEpochDay(it) }.getOrNull() } ?: return false
        if (start.isAfter(end)) return false
        val firstMonth = YearMonth.from(start)
        val lastMonth = YearMonth.from(end)
        return !selectedMonth.isBefore(firstMonth) && !selectedMonth.isAfter(lastMonth)
    }

    private fun matches(raw: String?, expected: java.math.BigDecimal): Boolean =
        raw?.toBigDecimalOrNull()?.compareTo(expected) == 0

    fun isEditable(status: PayslipStatus): Boolean = status == PayslipStatus.DRAFT

    fun canCorrect(status: PayslipStatus): Boolean = status == PayslipStatus.ISSUED

    private fun validEpochDay(day: Long): Boolean = runCatching { LocalDate.ofEpochDay(day) }.isSuccess
}
