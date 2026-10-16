package com.rainweaver.salaryslipmaker.domain

import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PayslipPolicyTest {
    @Test
    fun permitsIssuanceForCompleteNonnegativePayslip() {
        assertTrue(PayslipPolicy.issueErrors(validInput()).isEmpty())
    }

    @Test
    fun requiresBusinessEmployeeDatesCurrencyAndAtLeastOneEarning() {
        val errors = PayslipPolicy.issueErrors(
            validInput().copy(
                businessName = " ",
                employeeName = "",
                periodStartDay = null,
                paymentDay = null,
                currencyCode = "",
                lines = listOf(SalaryLine("Tax", SalaryLineKind.DEDUCTION, BigDecimal("1"))),
            ),
        )
        assertTrue(errors.contains(PayslipPolicy.ERROR_BUSINESS))
        assertTrue(errors.contains(PayslipPolicy.ERROR_EMPLOYEE))
        assertTrue(errors.contains(PayslipPolicy.ERROR_DATES))
        assertTrue(errors.contains(PayslipPolicy.ERROR_CURRENCY))
        assertTrue(errors.contains(PayslipPolicy.ERROR_EARNING))
    }

    @Test
    fun blocksIssuanceWhenPeriodIsReversedOrNetWouldBeNegative() {
        val reversed = PayslipPolicy.issueErrors(validInput().copy(periodStartDay = 20, periodEndDay = 10))
        assertTrue(reversed.contains(PayslipPolicy.ERROR_DATES))

        val negative = PayslipPolicy.issueErrors(
            validInput().copy(
                lines = listOf(
                    SalaryLine("Base", SalaryLineKind.EARNING, BigDecimal("50")),
                    SalaryLine("Deduction", SalaryLineKind.DEDUCTION, BigDecimal("60")),
                ),
            ),
        )
        assertTrue(negative.contains(PayslipPolicy.ERROR_NEGATIVE_NET))
    }

    @Test
    fun onlyDraftsCanBeEditedAndOnlyIssuedRecordsCanBeCorrected() {
        assertTrue(PayslipPolicy.isEditable(PayslipStatus.DRAFT))
        assertFalse(PayslipPolicy.isEditable(PayslipStatus.ISSUED))
        assertTrue(PayslipPolicy.canCorrect(PayslipStatus.ISSUED))
        assertFalse(PayslipPolicy.canCorrect(PayslipStatus.DRAFT))
    }

    @Test
    fun rejectsAmountPrecisionThatCannotBeShownInSelectedCurrency() {
        val errors = PayslipPolicy.issueErrors(
            validInput().copy(currencyCode = "JPY", lines = listOf(SalaryLine("Base", SalaryLineKind.EARNING, BigDecimal("100.50")))),
        )
        assertTrue(errors.contains(PayslipPolicy.ERROR_PRECISION))
        assertEquals(0, SalaryMoneyFormatter.fractionDigits("JPY"))
        assertEquals(2, SalaryMoneyFormatter.fractionDigits("USD"))
        assertEquals(2, SalaryMoneyFormatter.fractionDigits("Rp"))
    }

    @Test
    fun blocksIssuanceWhenSavedSummaryDoesNotMatchSalaryLines() {
        val errors = PayslipPolicy.issueErrors(
            validInput().copy(savedGrossEarnings = "100", savedTotalDeductions = "0", savedNetPay = "99"),
        )
        assertTrue(errors.contains(PayslipPolicy.ERROR_SAVED_TOTALS))
    }

    @Test
    fun unsavedDraftSnapshotChangesBlockIssueUntilSaved() {
        val snapshot = PayslipDraftSnapshot(1, 30, 31, "IDR", "Bank", "ref", "", validInput().lines)
        assertFalse(PayslipPolicy.isSnapshotCurrent(snapshot, snapshot.copy(notes = "Changed")))
        assertTrue(PayslipPolicy.isSnapshotCurrent(snapshot, snapshot))
    }

    @Test
    fun historyMonthFilterMatchesAnyMonthTouchedByThePayPeriod() {
        val start = java.time.LocalDate.of(2026, 8, 29).toEpochDay()
        val end = java.time.LocalDate.of(2026, 9, 2).toEpochDay()
        assertTrue(PayslipPolicy.periodMatches(start, end, java.time.YearMonth.of(2026, 8)))
        assertTrue(PayslipPolicy.periodMatches(start, end, java.time.YearMonth.of(2026, 9)))
        assertFalse(PayslipPolicy.periodMatches(start, end, java.time.YearMonth.of(2026, 7)))
    }

    private fun validInput() = PayslipIssueInput(
        businessName = "Rainweaver Studio",
        employeeName = "Ari",
        periodStartDay = 1,
        periodEndDay = 30,
        paymentDay = 31,
        currencyCode = "IDR",
        lines = listOf(SalaryLine("Base salary", SalaryLineKind.EARNING, BigDecimal("100"))),
    )
}
