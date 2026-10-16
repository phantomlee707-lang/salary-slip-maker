package com.rainweaver.salaryslipmaker.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProfileValidationTest {
    @Test
    fun requiredNameRejectsBlankInput() {
        assertEquals("Name is required", ProfileValidation.requiredNameError("  \n"))
    }

    @Test
    fun requiredNameAcceptsTrimmedNonBlankInput() {
        assertNull(ProfileValidation.requiredNameError("  Rainweaver Studio  "))
    }

    @Test
    fun employeeNameUsesSameRequiredNameRule() {
        assertEquals("Name is required", ProfileValidation.requiredNameError("\t"))
        assertNull(ProfileValidation.requiredNameError("Ayu Santoso"))
    }
}
