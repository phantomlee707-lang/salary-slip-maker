package com.rainweaver.salaryslipmaker.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rainweaver.salaryslipmaker.data.PayslipBundle
import com.rainweaver.salaryslipmaker.data.PayslipEntity
import com.rainweaver.salaryslipmaker.data.PayrollRepository
import com.rainweaver.salaryslipmaker.data.ProfileDatabase
import com.rainweaver.salaryslipmaker.data.SalaryTemplateBundle
import com.rainweaver.salaryslipmaker.data.SalaryTemplateEntity
import com.rainweaver.salaryslipmaker.domain.SalaryLine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PayrollUiState(
    val payslips: List<PayslipEntity> = emptyList(),
    val templates: List<SalaryTemplateEntity> = emptyList(),
    val message: String? = null,
)

class PayrollViewModel(app: Application) : AndroidViewModel(app) {
    private val repository = PayrollRepository(ProfileDatabase.get(app))
    private val _state = MutableStateFlow(PayrollUiState())
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observePayslips().collect { records -> _state.update { it.copy(payslips = records) } }
        }
        viewModelScope.launch {
            repository.observeTemplates().collect { records -> _state.update { it.copy(templates = records) } }
        }
    }

    suspend fun createDraft(employeeId: Long): Long = repository.createDraft(employeeId)
    suspend fun createFromTemplate(templateId: Long, employeeId: Long): Long = repository.createFromTemplate(templateId, employeeId)
    suspend fun copyDraft(id: Long): Long = repository.copyDraft(id)
    suspend fun createCorrection(id: Long): Long = repository.copyDraft(id, correction = true)
    suspend fun loadPayslip(id: Long): PayslipBundle? = repository.getBundle(id)
    suspend fun loadTemplate(id: Long): SalaryTemplateBundle? = repository.getTemplateBundle(id)
    suspend fun replacement(id: Long): PayslipEntity? = repository.getReplacement(id)
    suspend fun saveDraft(payslip: PayslipEntity, lines: List<SalaryLine>): Long = repository.saveDraft(payslip, lines)
    suspend fun issue(id: Long): List<String> = repository.issue(id)
    suspend fun deleteDraft(id: Long): Boolean = repository.deleteDraft(id)
    suspend fun saveTemplate(template: SalaryTemplateEntity, lines: List<SalaryLine>): Long = repository.saveTemplate(template, lines)
    suspend fun archiveTemplate(id: Long): Boolean = repository.archiveTemplate(id)
    suspend fun restoreTemplate(id: Long): Boolean = repository.restoreTemplate(id)

    fun message(value: String) = _state.update { it.copy(message = value) }
    fun consumeMessage() = _state.update { it.copy(message = null) }
}
