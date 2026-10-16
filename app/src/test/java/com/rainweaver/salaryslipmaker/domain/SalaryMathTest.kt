package com.rainweaver.salaryslipmaker.domain

import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SalaryMathTest {
    @Test
    fun calculatesGrossDeductionsAndNetWithoutFloatingPointDrift() {
        val totals = SalaryMath.calculate(
            listOf(
                SalaryLine("Base pay", SalaryLineKind.EARNING, BigDecimal("1250.10")),
                SalaryLine("Bonus", SalaryLineKind.EARNING, BigDecimal("0.20")),
                SalaryLine("Tax", SalaryLineKind.DEDUCTION, BigDecimal("150.05")),
            ),
        )

        assertMoney("1250.30", totals.grossEarnings)
        assertMoney("150.05", totals.totalDeductions)
        assertMoney("1100.25", totals.netPay)
        assertFalse(totals.negativeNet)
    }

    @Test
    fun acceptsValidGroupedAndUngroupedDecimalInput() {
        assertMoney("1234567.89", SalaryMath.parseAmount("1,234,567.89")!!)
        assertMoney("4000000", SalaryMath.parseAmount("4000000")!!)
    }

    @Test
    fun rejectsMalformedGroupingAndNegativeAmountInput() {
        assertNull(SalaryMath.parseAmount("12,34"))
        assertNull(SalaryMath.parseAmount("-500"))
        assertNull(SalaryMath.parseAmount("Rp 500"))
    }

    @Test
    fun flagsNetPayBelowZeroInsteadOfHidingIt() {
        val totals = SalaryMath.calculate(
            listOf(
                SalaryLine("Base", SalaryLineKind.EARNING, BigDecimal("50")),
                SalaryLine("Advance recovery", SalaryLineKind.DEDUCTION, BigDecimal("75")),
            ),
        )
        assertMoney("-25", totals.netPay)
        assertTrue(totals.negativeNet)
    }

    @Test
    fun rejectsBlankLabelsAndNegativeComponents() {
        assertThrows(IllegalArgumentException::class.java) {
            SalaryMath.calculate(listOf(SalaryLine("  ", SalaryLineKind.EARNING, BigDecimal("2"))))
        }
        assertThrows(IllegalArgumentException::class.java) {
            SalaryMath.calculate(listOf(SalaryLine("Tax", SalaryLineKind.DEDUCTION, BigDecimal("-1"))))
        }
    }

    private fun assertMoney(expected: String, actual: BigDecimal) {
        assertEquals(0, BigDecimal(expected).compareTo(actual))
    }
}
