package com.rainweaver.salaryslipmaker.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.rainweaver.salaryslipmaker.domain.PayslipStatus

@Entity(
    tableName = "salary_templates",
    indices = [Index("employeeId")],
)
data class SalaryTemplateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val employeeId: Long? = null,
    val currencyCode: String = "IDR",
    val archived: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "salary_template_lines",
    foreignKeys = [ForeignKey(
        entity = SalaryTemplateEntity::class,
        parentColumns = ["id"],
        childColumns = ["templateId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("templateId")],
)
data class SalaryTemplateLineEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val templateId: Long,
    val kind: String,
    val label: String,
    val amount: String,
    val sortOrder: Int,
)

@Entity(
    tableName = "payslips",
    indices = [Index(value = ["number"], unique = true), Index("employeeId"), Index("supersedesPayslipId")],
)
data class PayslipEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val number: String,
    val status: PayslipStatus = PayslipStatus.DRAFT,
    val businessName: String,
    val businessLogoUri: String? = null,
    val businessAddress: String = "",
    val businessPhone: String = "",
    val businessEmail: String = "",
    val businessRegistrationId: String = "",
    val businessAccentColorHex: String = "#1D5B54",
    val businessAdditionalInfo: String = "",
    val employeeId: Long? = null,
    val employeeName: String,
    val employeeIdentifier: String = "",
    val employeeJobTitle: String = "",
    val employeeDepartment: String = "",
    val periodStartDay: Long? = null,
    val periodEndDay: Long? = null,
    val paymentDay: Long? = null,
    val currencyCode: String = "IDR",
    val grossEarnings: String = "0",
    val totalDeductions: String = "0",
    val netPay: String = "0",
    val paymentMethod: String = "",
    val paymentReference: String = "",
    val notes: String = "",
    val supersedesPayslipId: Long? = null,
    val supersedesPayslipNumber: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val issuedAt: Long? = null,
)

@Entity(
    tableName = "payslip_lines",
    foreignKeys = [ForeignKey(
        entity = PayslipEntity::class,
        parentColumns = ["id"],
        childColumns = ["payslipId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("payslipId")],
)
data class PayslipLineEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val payslipId: Long,
    val kind: String,
    val label: String,
    val amount: String,
    val sortOrder: Int,
)

fun PayslipEntity.applyBusinessProfileSnapshot(
    profile: BusinessProfileEntity,
    updatedAt: Long,
): PayslipEntity {
    require(status == PayslipStatus.DRAFT) { "Issued payslips are immutable" }
    require(profile.businessName.isNotBlank()) { "Company name is required" }
    return copy(
        businessName = profile.businessName.trim(),
        businessLogoUri = profile.logoUri,
        businessAddress = profile.address,
        businessPhone = profile.phone,
        businessEmail = profile.email,
        businessRegistrationId = profile.registrationOrTaxId,
        businessAccentColorHex = profile.accentColorHex,
        businessAdditionalInfo = profile.additionalInfo,
        updatedAt = updatedAt,
    )
}
