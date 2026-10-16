package com.rainweaver.salaryslipmaker.domain

import com.rainweaver.salaryslipmaker.data.BusinessProfileEntity
import com.rainweaver.salaryslipmaker.data.EmployeeEntity
import com.rainweaver.salaryslipmaker.data.PayslipEntity
import com.rainweaver.salaryslipmaker.data.PayslipLineEntity
import com.rainweaver.salaryslipmaker.data.SalaryTemplateEntity
import com.rainweaver.salaryslipmaker.data.SalaryTemplateLineEntity

/** Portable local backup snapshot. It is validated fully before any live records are replaced. */
data class LocalBackupPayload(
    val schemaVersion: Int,
    val exportedAt: Long = 0,
    val business: BusinessProfileEntity? = null,
    val employees: List<EmployeeEntity> = emptyList(),
    val templates: List<SalaryTemplateEntity> = emptyList(),
    val templateLines: List<SalaryTemplateLineEntity> = emptyList(),
    val payslips: List<PayslipEntity> = emptyList(),
    val payslipLines: List<PayslipLineEntity> = emptyList(),
)

object LocalBackupValidator {
    const val CURRENT_VERSION = 1

    fun validate(payload: LocalBackupPayload): List<String> = buildList {
        if (payload.schemaVersion != CURRENT_VERSION) add("Unsupported backup version")
        if (payload.business?.id?.let { it != BusinessProfileEntity.SINGLETON_ID } == true) add("Invalid business profile")
        if (payload.employees.any { it.id <= 0 || it.fullName.isBlank() } || payload.employees.map { it.id }.distinct().size != payload.employees.size) add("Invalid or duplicate employee records")
        if (payload.templates.any { it.id <= 0 || it.name.isBlank() || it.currencyCode.isBlank() } || payload.templates.map { it.id }.distinct().size != payload.templates.size) add("Invalid or duplicate salary templates")
        if (payload.payslips.any { it.id <= 0 || it.number.isBlank() || it.businessName.isBlank() || it.employeeName.isBlank() || it.currencyCode.isBlank() } ||
            payload.payslips.map { it.id }.distinct().size != payload.payslips.size ||
            payload.payslips.map { it.number }.distinct().size != payload.payslips.size
        ) add("Invalid or duplicate payslip records")

        if (payload.templateLines.map { it.id }.distinct().size != payload.templateLines.size) add("Duplicate template salary line IDs")
        if (payload.payslipLines.map { it.id }.distinct().size != payload.payslipLines.size) add("Duplicate payslip salary line IDs")
        val templateIds = payload.templates.mapTo(mutableSetOf()) { it.id }
        val slipById = payload.payslips.associateBy { it.id }
        val lineErrors = mutableSetOf<Long>()
        payload.templateLines.forEach { line ->
            if (line.id <= 0 || line.templateId !in templateIds || !validLine(line.kind, line.label, line.amount)) add("Invalid template salary line")
        }
        payload.payslipLines.forEach { line ->
            if (line.id <= 0 || line.payslipId !in slipById || !validLine(line.kind, line.label, line.amount)) {
                add("Invalid payslip salary line")
                lineErrors += line.payslipId
            }
        }

        payload.templates.forEach { template ->
            val lines = payload.templateLines.filter { it.templateId == template.id }
            if (lines.isNotEmpty() && lines.map { it.sortOrder }.distinct().size != lines.size) add("Duplicate template line order")
        }
        payload.payslips.forEach { slip ->
            val rows = payload.payslipLines.filter { it.payslipId == slip.id }
            if (rows.map { it.sortOrder }.distinct().size != rows.size) add("Duplicate payslip line order")
            if (slip.id in lineErrors) return@forEach
            val domainLines = rows.mapNotNull { row ->
                val kind = runCatching { SalaryLineKind.valueOf(row.kind) }.getOrNull() ?: return@mapNotNull null
                val amount = row.amount.toBigDecimalOrNull() ?: return@mapNotNull null
                SalaryLine(row.label, kind, amount)
            }
            val totals = runCatching { SalaryMath.calculate(domainLines) }.getOrNull()
            if (totals == null || !sameAmount(slip.grossEarnings, totals.grossEarnings) ||
                !sameAmount(slip.totalDeductions, totals.totalDeductions) || !sameAmount(slip.netPay, totals.netPay)
            ) add("Saved payslip totals do not match salary lines")
            if (slip.status == PayslipStatus.ISSUED) {
                if (slip.issuedAt == null) add("Issued payslip is missing its issue timestamp")
                val errors = PayslipPolicy.issueErrors(
                    PayslipIssueInput(slip.businessName, slip.employeeName, slip.periodStartDay, slip.periodEndDay, slip.paymentDay, slip.currencyCode, domainLines, slip.grossEarnings, slip.totalDeductions, slip.netPay),
                )
                if (errors.isNotEmpty()) add("Issued payslip is not valid")
            }
            slip.supersedesPayslipId?.let { sourceId ->
                val source = slipById[sourceId]
                if (source == null || sourceId == slip.id || source.status != PayslipStatus.ISSUED || slip.supersedesPayslipNumber != source.number) add("Invalid correction link")
            }
        }
    }

    private fun validLine(kind: String, label: String, amount: String): Boolean {
        val parsed = SalaryMath.parseAmount(amount) ?: return false
        return kind in setOf(SalaryLineKind.EARNING.name, SalaryLineKind.DEDUCTION.name) &&
            label.isNotBlank() && parsed.toPlainString() == amount
    }

    private fun sameAmount(raw: String, value: java.math.BigDecimal): Boolean =
        raw.toBigDecimalOrNull()?.compareTo(value) == 0
}
