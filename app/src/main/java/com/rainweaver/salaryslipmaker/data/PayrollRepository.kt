package com.rainweaver.salaryslipmaker.data

import androidx.room.withTransaction
import com.rainweaver.salaryslipmaker.domain.PayslipIssueInput
import com.rainweaver.salaryslipmaker.domain.PayslipPolicy
import com.rainweaver.salaryslipmaker.domain.PayslipStatus
import com.rainweaver.salaryslipmaker.domain.SalaryLine
import com.rainweaver.salaryslipmaker.domain.SalaryLineKind
import com.rainweaver.salaryslipmaker.domain.SalaryMath
import com.rainweaver.salaryslipmaker.domain.SalaryMoneyFormatter
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale
import java.util.UUID

data class PayslipBundle(
    val payslip: PayslipEntity,
    val lines: List<SalaryLine>,
    val invalidLineCount: Int = 0,
)

data class SalaryTemplateBundle(
    val template: SalaryTemplateEntity,
    val lines: List<SalaryLine>,
    val invalidLineCount: Int = 0,
)

class PayrollRepository(
    private val database: ProfileDatabase,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val payrollDao = database.payrollDao()
    private val businessDao = database.businessProfileDao()
    private val employeeDao = database.employeeDao()

    fun observePayslips(): Flow<List<PayslipEntity>> = payrollDao.observePayslips()
    fun observeTemplates(): Flow<List<SalaryTemplateEntity>> = payrollDao.observeTemplates()

    suspend fun getBundle(id: Long): PayslipBundle? {
        val slip = payrollDao.getPayslip(id) ?: return null
        val raw = payrollDao.getPayslipLines(id)
        val (valid, invalid) = parseLines(raw.map { it.kind to (it.label to it.amount) })
        return PayslipBundle(slip, valid, invalid)
    }

    suspend fun getTemplateBundle(id: Long): SalaryTemplateBundle? {
        val template = payrollDao.getTemplate(id) ?: return null
        val raw = payrollDao.getTemplateLines(id)
        val (valid, invalid) = parseLines(raw.map { it.kind to (it.label to it.amount) })
        return SalaryTemplateBundle(template, valid, invalid)
    }

    suspend fun getReplacement(id: Long): PayslipEntity? = payrollDao.getIssuedReplacement(id)

    suspend fun applyCurrentBusinessProfileToDraft(id: Long) = database.withTransaction {
        val current = payrollDao.getPayslip(id) ?: error("Payslip not found")
        require(PayslipPolicy.isEditable(current.status)) { "Issued payslips are immutable" }
        val profile = businessDao.get()?.takeIf { it.businessName.isNotBlank() }
            ?: error("Save the company profile before applying it to this draft")
        check(payrollDao.updatePayslip(current.applyBusinessProfileSnapshot(profile, clock())) == 1) {
            "Company profile could not be applied"
        }
    }

    suspend fun createDraft(employeeId: Long, currencyCode: String = "IDR"): Long = database.withTransaction {
        val employee = employeeDao.getById(employeeId) ?: error("Employee not found")
        require(!employee.archived) { "Restore the archived employee before creating a new payslip" }
        val business = businessDao.get() ?: BusinessProfileEntity()
        val now = clock()
        val today = LocalDate.now()
        val period = YearMonth.from(today)
        payrollDao.insertPayslip(
            PayslipEntity(
                number = uniqueNumber(),
                businessName = business.businessName.trim(),
                businessLogoUri = business.logoUri,
                businessAddress = business.address,
                businessPhone = business.phone,
                businessEmail = business.email,
                businessRegistrationId = business.registrationOrTaxId,
                businessAccentColorHex = business.accentColorHex,
                businessAdditionalInfo = business.additionalInfo,
                employeeId = employee.id,
                employeeName = employee.fullName,
                employeeIdentifier = employee.employeeIdentifier,
                employeeJobTitle = employee.jobTitle,
                employeeDepartment = employee.department,
                periodStartDay = period.atDay(1).toEpochDay(),
                periodEndDay = period.atEndOfMonth().toEpochDay(),
                paymentDay = today.toEpochDay(),
                currencyCode = normalizeCurrency(currencyCode),
                createdAt = now,
                updatedAt = now,
            ),
        )
    }

    suspend fun createFromTemplate(templateId: Long, employeeId: Long): Long = database.withTransaction {
        val template = payrollDao.getTemplate(templateId) ?: error("Template not found")
        require(!template.archived) { "This template is archived" }
        require(template.employeeId == null || template.employeeId == employeeId) {
            "This template is assigned to a different employee"
        }
        val employee = employeeDao.getById(employeeId) ?: error("Employee not found")
        require(!employee.archived) { "Restore the archived employee before creating a new payslip" }
        val business = businessDao.get() ?: BusinessProfileEntity()
        val now = clock()
        val today = LocalDate.now()
        val period = YearMonth.from(today)
        val slipId = payrollDao.insertPayslip(
            PayslipEntity(
                number = uniqueNumber(),
                businessName = business.businessName.trim(),
                businessLogoUri = business.logoUri,
                businessAddress = business.address,
                businessPhone = business.phone,
                businessEmail = business.email,
                businessRegistrationId = business.registrationOrTaxId,
                businessAccentColorHex = business.accentColorHex,
                businessAdditionalInfo = business.additionalInfo,
                employeeId = employee.id,
                employeeName = employee.fullName,
                employeeIdentifier = employee.employeeIdentifier,
                employeeJobTitle = employee.jobTitle,
                employeeDepartment = employee.department,
                periodStartDay = period.atDay(1).toEpochDay(),
                periodEndDay = period.atEndOfMonth().toEpochDay(),
                paymentDay = today.toEpochDay(),
                currencyCode = normalizeCurrency(template.currencyCode),
                createdAt = now,
                updatedAt = now,
            ),
        )
        val rawLines = payrollDao.getTemplateLines(templateId)
        val lines = rawLines.map { row ->
            val amount = SalaryMath.parseAmount(row.amount) ?: error("Template contains an invalid amount")
            val kind = runCatching { SalaryLineKind.valueOf(row.kind) }.getOrNull() ?: error("Template contains an invalid line type")
            SalaryLine(row.label, kind, amount)
        }
        validateLines(lines, template.currencyCode)
        require(lines.any { it.kind == SalaryLineKind.EARNING && it.amount.signum() > 0 }) { "Template needs at least one earning line" }
        val totals = SalaryMath.calculate(lines)
        val slip = checkNotNull(payrollDao.getPayslip(slipId))
        check(
            payrollDao.updatePayslip(
                slip.copy(
                    grossEarnings = totals.grossEarnings.toPlainString(),
                    totalDeductions = totals.totalDeductions.toPlainString(),
                    netPay = totals.netPay.toPlainString(),
                ),
            ) == 1,
        )
        insertPayslipLines(slipId, lines)
        slipId
    }

    suspend fun saveDraft(payslip: PayslipEntity, lines: List<SalaryLine>): Long = database.withTransaction {
        val current = payrollDao.getPayslip(payslip.id) ?: error("Payslip not found")
        require(PayslipPolicy.isEditable(current.status)) { "Issued payslips cannot be edited; create a correction instead" }
        validateLines(lines, payslip.currencyCode)
        val totals = SalaryMath.calculate(lines)
        val updated = payslip.copy(
            id = current.id,
            number = current.number,
            status = PayslipStatus.DRAFT,
            grossEarnings = totals.grossEarnings.toPlainString(),
            totalDeductions = totals.totalDeductions.toPlainString(),
            netPay = totals.netPay.toPlainString(),
            createdAt = current.createdAt,
            issuedAt = null,
            updatedAt = clock(),
        )
        check(payrollDao.updatePayslip(updated) == 1) { "Payslip could not be saved" }
        payrollDao.deletePayslipLines(current.id)
        insertPayslipLines(current.id, lines)
        current.id
    }

    suspend fun issue(id: Long): List<String> = database.withTransaction {
        val bundle = getBundle(id) ?: return@withTransaction listOf("Payslip not found")
        if (bundle.payslip.status != PayslipStatus.DRAFT) return@withTransaction listOf("Issued payslips are locked")
        if (bundle.invalidLineCount > 0) return@withTransaction listOf("Invalid stored salary lines; edit and save the draft again")
        val p = bundle.payslip
        val errors = PayslipPolicy.issueErrors(
            PayslipIssueInput(
                businessName = p.businessName,
                employeeName = p.employeeName,
                periodStartDay = p.periodStartDay,
                periodEndDay = p.periodEndDay,
                paymentDay = p.paymentDay,
                currencyCode = p.currencyCode,
                lines = bundle.lines,
                savedGrossEarnings = p.grossEarnings,
                savedTotalDeductions = p.totalDeductions,
                savedNetPay = p.netPay,
            ),
        )
        if (errors.isNotEmpty()) return@withTransaction errors
        if (payrollDao.markIssuedIfDraft(id, clock()) != 1) return@withTransaction listOf("Payslip changed; reload and try again")
        emptyList()
    }

    suspend fun copyDraft(sourceId: Long, correction: Boolean = false): Long = database.withTransaction {
        val source = payrollDao.getPayslip(sourceId) ?: error("Payslip not found")
        require(!correction || PayslipPolicy.canCorrect(source.status)) {
            "Only an issued payslip can have a correction"
        }
        val now = clock()
        val newId = payrollDao.insertPayslip(
            source.copy(
                id = 0,
                number = uniqueNumber(),
                status = PayslipStatus.DRAFT,
                supersedesPayslipId = if (correction) source.id else null,
                supersedesPayslipNumber = if (correction) source.number else null,
                createdAt = now,
                updatedAt = now,
                issuedAt = null,
            ),
        )
        val lines = payrollDao.getPayslipLines(sourceId).map { it.copy(id = 0, payslipId = newId) }
        if (lines.isNotEmpty()) payrollDao.insertPayslipLines(lines)
        newId
    }

    suspend fun deleteDraft(id: Long): Boolean = database.withTransaction {
        val slip = payrollDao.getPayslip(id) ?: return@withTransaction false
        if (!PayslipPolicy.isEditable(slip.status)) return@withTransaction false
        payrollDao.deleteDraft(id) == 1
    }

    suspend fun saveTemplate(template: SalaryTemplateEntity, lines: List<SalaryLine>): Long = database.withTransaction {
        require(template.name.isNotBlank()) { "Template name is required" }
        require(template.currencyCode.isNotBlank()) { "Currency is required" }
        validateLines(lines, template.currencyCode)
        require(lines.any { it.kind == SalaryLineKind.EARNING && it.amount.signum() > 0 }) {
            "Add at least one earning line"
        }
        val now = clock()
        val savedId = if (template.id == 0L) {
            payrollDao.insertTemplate(
                template.copy(
                    name = template.name.trim(),
                    currencyCode = normalizeCurrency(template.currencyCode),
                    updatedAt = now,
                    createdAt = now,
                ),
            )
        } else {
            val current = payrollDao.getTemplate(template.id) ?: error("Template not found")
            require(!current.archived) { "Restore the archived template before editing" }
            check(
                payrollDao.updateTemplate(
                    template.copy(
                        name = template.name.trim(),
                        currencyCode = normalizeCurrency(template.currencyCode),
                        createdAt = current.createdAt,
                        updatedAt = now,
                    ),
                ) == 1,
            )
            template.id
        }
        payrollDao.deleteTemplateLines(savedId)
        val rows = lines.mapIndexed { index, line ->
            SalaryTemplateLineEntity(
                templateId = savedId,
                kind = line.kind.name,
                label = line.label.trim(),
                amount = line.amount.toPlainString(),
                sortOrder = index,
            )
        }
        if (rows.isNotEmpty()) payrollDao.insertTemplateLines(rows)
        savedId
    }

    suspend fun archiveTemplate(id: Long): Boolean = payrollDao.archiveTemplate(id, clock()) == 1
    suspend fun restoreTemplate(id: Long): Boolean = payrollDao.restoreTemplate(id, clock()) == 1

    private suspend fun uniqueNumber(): String {
        var number: String
        do {
            number = "SS-${UUID.randomUUID().toString().take(8).uppercase(Locale.ROOT)}"
        } while (payrollDao.getByNumber(number) != null)
        return number
    }

    private fun validateLines(lines: List<SalaryLine>, currencyCode: String) {
        require(lines.all { it.label.isNotBlank() }) { "Every salary line needs a label" }
        SalaryMath.calculate(lines)
        val digits = SalaryMoneyFormatter.fractionDigits(currencyCode)
        require(lines.none { it.amount.stripTrailingZeros().scale().coerceAtLeast(0) > digits }) {
            "One or more amounts use more decimal places than $currencyCode supports"
        }
    }

    private suspend fun insertPayslipLines(payslipId: Long, lines: List<SalaryLine>) {
        if (lines.isEmpty()) return
        payrollDao.insertPayslipLines(lines.mapIndexed { index, line ->
            PayslipLineEntity(
                payslipId = payslipId,
                kind = line.kind.name,
                label = line.label.trim(),
                amount = line.amount.toPlainString(),
                sortOrder = index,
            )
        })
    }

    private fun parseLines(rows: List<Pair<String, Pair<String, String>>>): Pair<List<SalaryLine>, Int> {
        val parsed = rows.mapNotNull { (kindText, labelAndAmount) ->
            val (label, amountText) = labelAndAmount
            val kind = runCatching { SalaryLineKind.valueOf(kindText) }.getOrNull()
            val amount = SalaryMath.parseAmount(amountText)
            if (kind == null || amount == null || label.isBlank()) null else SalaryLine(label, kind, amount)
        }
        return parsed to (rows.size - parsed.size)
    }

    private fun normalizeCurrency(value: String): String = value.trim().uppercase(Locale.ROOT).ifBlank { "IDR" }
}
