package com.rainweaver.salaryslipmaker.data

import com.rainweaver.salaryslipmaker.domain.PayslipStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PayslipBusinessProfileTest {
    @Test
    fun appliesLatestCompanyDetailsToDraftWithoutMutatingOtherSnapshotFields() {
        val draft = PayslipEntity(
            id = 7,
            number = "SS-EXAMPLE",
            businessName = "",
            employeeName = "Ari",
            notes = "Keep these draft notes",
            createdAt = 100,
            updatedAt = 110,
        )
        val profile = BusinessProfileEntity(
            businessName = "Rainweaver Studio",
            logoUri = "content://logos/current",
            address = "Tokyo",
            phone = "+81 00 0000 0000",
            email = "payroll@example.test",
            registrationOrTaxId = "REG-1",
            accentColorHex = "#00897B",
            additionalInfo = "Company footer",
        )

        val refreshed = draft.applyBusinessProfileSnapshot(profile, updatedAt = 200)

        assertEquals("Rainweaver Studio", refreshed.businessName)
        assertEquals(profile.logoUri, refreshed.businessLogoUri)
        assertEquals(profile.address, refreshed.businessAddress)
        assertEquals(profile.phone, refreshed.businessPhone)
        assertEquals(profile.email, refreshed.businessEmail)
        assertEquals(profile.registrationOrTaxId, refreshed.businessRegistrationId)
        assertEquals(profile.accentColorHex, refreshed.businessAccentColorHex)
        assertEquals(profile.additionalInfo, refreshed.businessAdditionalInfo)
        assertEquals("Ari", refreshed.employeeName)
        assertEquals("Keep these draft notes", refreshed.notes)
        assertEquals(100L, refreshed.createdAt)
        assertEquals(200L, refreshed.updatedAt)
        assertEquals(PayslipStatus.DRAFT, refreshed.status)
        assertEquals("", draft.businessName)
        assertEquals(110L, draft.updatedAt)
    }

    @Test
    fun refusesToChangeCompanySnapshotOnIssuedPayslip() {
        val issued = PayslipEntity(
            id = 8,
            number = "SS-ISSUED",
            status = PayslipStatus.ISSUED,
            businessName = "Original Company",
            employeeName = "Ari",
            issuedAt = 123,
        )
        assertThrows(IllegalArgumentException::class.java) {
            issued.applyBusinessProfileSnapshot(BusinessProfileEntity(businessName = "New Company"), 200)
        }
    }
}
