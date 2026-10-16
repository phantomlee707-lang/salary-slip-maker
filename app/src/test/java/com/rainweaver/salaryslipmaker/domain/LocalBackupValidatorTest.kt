package com.rainweaver.salaryslipmaker.domain

import com.rainweaver.salaryslipmaker.data.BusinessProfileEntity
import com.rainweaver.salaryslipmaker.data.EmployeeEntity
import com.rainweaver.salaryslipmaker.data.PayslipEntity
import com.rainweaver.salaryslipmaker.data.PayslipLineEntity
import com.rainweaver.salaryslipmaker.data.SalaryTemplateEntity
import com.rainweaver.salaryslipmaker.data.SalaryTemplateLineEntity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalBackupValidatorTest {
    @Test
    fun acceptsConsistentSelfContainedBackup() {
        assertTrue(LocalBackupValidator.validate(validBackup()).isEmpty())
    }

    @Test
    fun rejectsUnsupportedBackupVersionAndDanglingLines() {
        val data = validBackup().copy(schemaVersion = 99, payslipLines = listOf(PayslipLineEntity(payslipId = 77, kind = "EARNING", label = "Base", amount = "100", sortOrder = 0)))
        val errors = LocalBackupValidator.validate(data)
        assertTrue(errors.isNotEmpty())
    }

    @Test
    fun rejectsDuplicatePayslipNumbersAndTotalsThatDoNotMatchLines() {
        val original = validBackup()
        val duplicate = original.payslips.single().copy(id = 2)
        val errors = LocalBackupValidator.validate(original.copy(payslips = original.payslips + duplicate))
        assertFalse(errors.isEmpty())

        val wrongTotal = original.payslips.single().copy(netPay = "999")
        assertFalse(LocalBackupValidator.validate(original.copy(payslips = listOf(wrongTotal))).isEmpty())
    }

    @Test
    fun rejectsNonCanonicalMoneyStrings() {
        val data = validBackup().copy(payslipLines = listOf(PayslipLineEntity(payslipId = 1, kind = "EARNING", label = "Base", amount = "1,000", sortOrder = 0)))
        assertFalse(LocalBackupValidator.validate(data).isEmpty())
    }

    @Test
    fun rejectsDuplicateLineIdsBeforeRestore() {
        val original = validBackup()
        val duplicate = original.templateLines.single().copy(sortOrder = 1, label = "Bonus")
        assertFalse(LocalBackupValidator.validate(original.copy(templateLines = original.templateLines + duplicate)).isEmpty())
    }

    private fun validBackup() = LocalBackupPayload(
        schemaVersion = LocalBackupValidator.CURRENT_VERSION,
        business = BusinessProfileEntity(businessName = "Rainweaver Studio"),
        employees = listOf(EmployeeEntity(id = 1, fullName = "Ari")),
        templates = listOf(SalaryTemplateEntity(id = 1, name = "Monthly")),
        templateLines = listOf(SalaryTemplateLineEntity(id = 1, templateId = 1, kind = "EARNING", label = "Base", amount = "1000", sortOrder = 0)),
        payslips = listOf(PayslipEntity(
            id = 1, number = "SS-TEST1234", status = PayslipStatus.ISSUED,
            businessName = "Rainweaver Studio", employeeId = 1, employeeName = "Ari",
            periodStartDay = 1, periodEndDay = 30, paymentDay = 31, currencyCode = "USD",
            grossEarnings = "1000", totalDeductions = "100", netPay = "900", issuedAt = 1000,
        )),
        payslipLines = listOf(
            PayslipLineEntity(id = 1, payslipId = 1, kind = "EARNING", label = "Base", amount = "1000", sortOrder = 0),
            PayslipLineEntity(id = 2, payslipId = 1, kind = "DEDUCTION", label = "Tax", amount = "100", sortOrder = 1),
        ),
    )
}
