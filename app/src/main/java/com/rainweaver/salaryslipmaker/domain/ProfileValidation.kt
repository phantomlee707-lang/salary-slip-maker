package com.rainweaver.salaryslipmaker.domain

/** Shared required-name validation for business and employee profiles. */
object ProfileValidation {
    fun requiredNameError(value: String): String? =
        if (value.isBlank()) "Name is required" else null
}
