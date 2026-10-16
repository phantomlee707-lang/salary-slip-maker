package com.rainweaver.salaryslipmaker.data

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.room.withTransaction
import com.rainweaver.salaryslipmaker.domain.LocalBackupPayload
import com.rainweaver.salaryslipmaker.domain.LocalBackupValidator

/** Plain JSON backup is stored only in a user-selected SAF destination; it is not encrypted. */
object LocalBackupService {
    private const val FORMAT = "RainweaverSalarySlipBackup"
    private const val APP_ID = "com.rainweaver.salaryslipmaker"
    private const val MAX_BACKUP_BYTES = 64 * 1024 * 1024

    suspend fun export(context: Context, target: Uri) {
        val db = ProfileDatabase.get(context)
        val payload = withContext(Dispatchers.IO) {
            db.withTransaction {
                LocalBackupPayload(
                    schemaVersion = LocalBackupValidator.CURRENT_VERSION, exportedAt = System.currentTimeMillis(),
                    business = db.businessProfileDao().get(), employees = db.employeeDao().getAllForBackup(),
                    templates = db.payrollDao().getAllTemplatesForBackup(), templateLines = db.payrollDao().getAllTemplateLinesForBackup(),
                    payslips = db.payrollDao().getAllPayslipsForBackup(), payslipLines = db.payrollDao().getAllPayslipLinesForBackup(),
                )
            }
        }
        validate(payload)
        val bytes = encode(payload).toString(2).toByteArray(StandardCharsets.UTF_8)
        require(bytes.size <= MAX_BACKUP_BYTES) { "Backup exceeds the 64 MB limit" }
        withContext(Dispatchers.IO) {
            val output = context.contentResolver.openOutputStream(target, "w") ?: error("Could not open backup destination")
            output.use { it.write(bytes) }
        }
    }

    suspend fun readAndValidate(context: Context, source: Uri): LocalBackupPayload = withContext(Dispatchers.IO) {
        val input = context.contentResolver.openInputStream(source) ?: error("Could not open selected backup")
        val bytes = input.use { readLimited(it) }
        val payload = decode(JSONObject(bytes.toString(StandardCharsets.UTF_8)))
        validate(payload)
        payload
    }

    suspend fun restore(context: Context, payload: LocalBackupPayload) = withContext(Dispatchers.IO) {
        validate(payload)
        val db = ProfileDatabase.get(context)
        db.withTransaction {
            val payroll = db.payrollDao()
            payroll.clearPayslipsForRestore()
            payroll.clearTemplatesForRestore()
            db.employeeDao().clearForRestore()
            db.businessProfileDao().clearForRestore()
            payload.business?.let { db.businessProfileDao().save(it) }
            if (payload.employees.isNotEmpty()) db.employeeDao().restoreAll(payload.employees)
            if (payload.templates.isNotEmpty()) payroll.restoreTemplates(payload.templates)
            if (payload.templateLines.isNotEmpty()) payroll.restoreTemplateLines(payload.templateLines)
            if (payload.payslips.isNotEmpty()) payroll.restorePayslips(payload.payslips)
            if (payload.payslipLines.isNotEmpty()) payroll.restorePayslipLines(payload.payslipLines)
        }
    }

    private fun validate(payload: LocalBackupPayload) {
        val errors = LocalBackupValidator.validate(payload)
        require(errors.isEmpty()) { "Backup validation failed: ${errors.distinct().joinToString("; ")}" }
    }

    private fun encode(payload: LocalBackupPayload): JSONObject = JSONObject().apply {
        put("format", FORMAT); put("appId", APP_ID); put("schemaVersion", payload.schemaVersion); put("exportedAt", payload.exportedAt)
        put("business", payload.business?.toJson() ?: JSONObject.NULL)
        put("employees", JSONArray().apply { payload.employees.forEach { put(it.toJson()) } })
        put("templates", JSONArray().apply { payload.templates.forEach { put(it.toJson()) } })
        put("templateLines", JSONArray().apply { payload.templateLines.forEach { put(it.toJson()) } })
        put("payslips", JSONArray().apply { payload.payslips.forEach { put(it.toJson()) } })
        put("payslipLines", JSONArray().apply { payload.payslipLines.forEach { put(it.toJson()) } })
    }

    private fun decode(root: JSONObject): LocalBackupPayload {
        require(root.string("format") == FORMAT) { "This is not a Salary Slip Maker backup" }
        require(root.string("appId") == APP_ID) { "Backup is for a different application" }
        val businessValue = root.get("business")
        return LocalBackupPayload(
            schemaVersion = root.int("schemaVersion"), exportedAt = root.long("exportedAt"),
            business = if (businessValue == JSONObject.NULL) null else (businessValue as? JSONObject)?.toBusiness() ?: error("Invalid business profile"),
            employees = root.objects("employees").map { it.toEmployee() }, templates = root.objects("templates").map { it.toTemplate() },
            templateLines = root.objects("templateLines").map { it.toTemplateLine() }, payslips = root.objects("payslips").map { it.toPayslip() },
            payslipLines = root.objects("payslipLines").map { it.toPayslipLine() },
        )
    }

    private fun BusinessProfileEntity.toJson() = JSONObject().apply {
        put("id", id); put("businessName", businessName); putNullable("logoUri", logoUri); put("address", address); put("phone", phone); put("email", email)
        put("registrationOrTaxId", registrationOrTaxId); put("accentColorHex", accentColorHex); put("additionalInfo", additionalInfo); put("updatedAt", updatedAt)
    }
    private fun EmployeeEntity.toJson() = JSONObject().apply {
        put("id", id); put("fullName", fullName); put("employeeIdentifier", employeeIdentifier); put("jobTitle", jobTitle); put("department", department)
        put("contactInfo", contactInfo); put("notes", notes); put("archived", archived); put("createdAt", createdAt); put("updatedAt", updatedAt)
    }
    private fun SalaryTemplateEntity.toJson() = JSONObject().apply {
        put("id", id); put("name", name); putNullable("employeeId", employeeId); put("currencyCode", currencyCode); put("archived", archived); put("createdAt", createdAt); put("updatedAt", updatedAt)
    }
    private fun SalaryTemplateLineEntity.toJson() = JSONObject().apply {
        put("id", id); put("templateId", templateId); put("kind", kind); put("label", label); put("amount", amount); put("sortOrder", sortOrder)
    }
    private fun PayslipEntity.toJson() = JSONObject().apply {
        put("id", id); put("number", number); put("status", status.name); put("businessName", businessName); putNullable("businessLogoUri", businessLogoUri)
        put("businessAddress", businessAddress); put("businessPhone", businessPhone); put("businessEmail", businessEmail); put("businessRegistrationId", businessRegistrationId)
        put("businessAccentColorHex", businessAccentColorHex); put("businessAdditionalInfo", businessAdditionalInfo); putNullable("employeeId", employeeId)
        put("employeeName", employeeName); put("employeeIdentifier", employeeIdentifier); put("employeeJobTitle", employeeJobTitle); put("employeeDepartment", employeeDepartment)
        putNullable("periodStartDay", periodStartDay); putNullable("periodEndDay", periodEndDay); putNullable("paymentDay", paymentDay)
        put("currencyCode", currencyCode); put("grossEarnings", grossEarnings); put("totalDeductions", totalDeductions); put("netPay", netPay)
        put("paymentMethod", paymentMethod); put("paymentReference", paymentReference); put("notes", notes); putNullable("supersedesPayslipId", supersedesPayslipId); putNullable("supersedesPayslipNumber", supersedesPayslipNumber)
        put("createdAt", createdAt); put("updatedAt", updatedAt); putNullable("issuedAt", issuedAt)
    }
    private fun PayslipLineEntity.toJson() = JSONObject().apply {
        put("id", id); put("payslipId", payslipId); put("kind", kind); put("label", label); put("amount", amount); put("sortOrder", sortOrder)
    }

    private fun JSONObject.toBusiness() = BusinessProfileEntity(
        id = int("id"), businessName = string("businessName"), logoUri = nullableString("logoUri"), address = string("address"), phone = string("phone"),
        email = string("email"), registrationOrTaxId = string("registrationOrTaxId"), accentColorHex = string("accentColorHex"), additionalInfo = string("additionalInfo"), updatedAt = long("updatedAt"),
    )
    private fun JSONObject.toEmployee() = EmployeeEntity(
        id = long("id"), fullName = string("fullName"), employeeIdentifier = string("employeeIdentifier"), jobTitle = string("jobTitle"), department = string("department"),
        contactInfo = string("contactInfo"), notes = string("notes"), archived = bool("archived"), createdAt = long("createdAt"), updatedAt = long("updatedAt"),
    )
    private fun JSONObject.toTemplate() = SalaryTemplateEntity(
        id = long("id"), name = string("name"), employeeId = nullableLong("employeeId"), currencyCode = string("currencyCode"), archived = bool("archived"), createdAt = long("createdAt"), updatedAt = long("updatedAt"),
    )
    private fun JSONObject.toTemplateLine() = SalaryTemplateLineEntity(id = long("id"), templateId = long("templateId"), kind = string("kind"), label = string("label"), amount = string("amount"), sortOrder = int("sortOrder"))
    private fun JSONObject.toPayslip() = PayslipEntity(
        id = long("id"), number = string("number"), status = runCatching { com.rainweaver.salaryslipmaker.domain.PayslipStatus.valueOf(string("status")) }.getOrElse { error("Invalid payslip status") },
        businessName = string("businessName"), businessLogoUri = nullableString("businessLogoUri"), businessAddress = string("businessAddress"), businessPhone = string("businessPhone"),
        businessEmail = string("businessEmail"), businessRegistrationId = string("businessRegistrationId"), businessAccentColorHex = string("businessAccentColorHex"), businessAdditionalInfo = string("businessAdditionalInfo"),
        employeeId = nullableLong("employeeId"), employeeName = string("employeeName"), employeeIdentifier = string("employeeIdentifier"), employeeJobTitle = string("employeeJobTitle"), employeeDepartment = string("employeeDepartment"),
        periodStartDay = nullableLong("periodStartDay"), periodEndDay = nullableLong("periodEndDay"), paymentDay = nullableLong("paymentDay"), currencyCode = string("currencyCode"),
        grossEarnings = string("grossEarnings"), totalDeductions = string("totalDeductions"), netPay = string("netPay"), paymentMethod = string("paymentMethod"), paymentReference = string("paymentReference"), notes = string("notes"),
        supersedesPayslipId = nullableLong("supersedesPayslipId"), supersedesPayslipNumber = nullableString("supersedesPayslipNumber"),
        createdAt = long("createdAt"), updatedAt = long("updatedAt"), issuedAt = nullableLong("issuedAt"),
    )
    private fun JSONObject.toPayslipLine() = PayslipLineEntity(id = long("id"), payslipId = long("payslipId"), kind = string("kind"), label = string("label"), amount = string("amount"), sortOrder = int("sortOrder"))

    private fun JSONObject.putNullable(key: String, value: Any?) { put(key, value ?: JSONObject.NULL) }
    private fun JSONObject.string(key: String): String = get(key) as? String ?: error("Invalid '$key' field")
    private fun JSONObject.bool(key: String): Boolean = get(key) as? Boolean ?: error("Invalid '$key' field")
    private fun JSONObject.long(key: String): Long = (get(key) as? Number)?.toLong() ?: error("Invalid '$key' field")
    private fun JSONObject.int(key: String): Int = (get(key) as? Number)?.toInt() ?: error("Invalid '$key' field")
    private fun JSONObject.nullableString(key: String): String? = if (isNull(key)) null else string(key)
    private fun JSONObject.nullableLong(key: String): Long? = if (isNull(key)) null else long(key)
    private fun JSONObject.objects(key: String): List<JSONObject> {
        val array = get(key) as? JSONArray ?: error("Invalid '$key' array")
        return (0 until array.length()).map { array.get(it) as? JSONObject ?: error("Invalid object in '$key'") }
    }

    private fun readLimited(input: java.io.InputStream): ByteArray {
        val output = ByteArrayOutputStream(); val buffer = ByteArray(8192); var total = 0
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            total += count
            require(total <= MAX_BACKUP_BYTES) { "Backup exceeds the 64 MB limit" }
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }
}
