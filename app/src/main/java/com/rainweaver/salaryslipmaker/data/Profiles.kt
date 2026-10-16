package com.rainweaver.salaryslipmaker.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "business_profile")
data class BusinessProfileEntity(
    @PrimaryKey val id: Int = SINGLETON_ID,
    val businessName: String = "",
    val logoUri: String? = null,
    val address: String = "",
    val phone: String = "",
    val email: String = "",
    val registrationOrTaxId: String = "",
    val accentColorHex: String = "#1D5B54",
    val additionalInfo: String = "",
    val updatedAt: Long = System.currentTimeMillis(),
) {
    companion object { const val SINGLETON_ID = 1 }
}

@Entity(tableName = "employees")
data class EmployeeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fullName: String,
    val employeeIdentifier: String = "",
    val jobTitle: String = "",
    val department: String = "",
    val contactInfo: String = "",
    val notes: String = "",
    val archived: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)
