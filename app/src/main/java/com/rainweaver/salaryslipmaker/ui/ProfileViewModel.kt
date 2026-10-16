package com.rainweaver.salaryslipmaker.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rainweaver.salaryslipmaker.data.BusinessProfileEntity
import com.rainweaver.salaryslipmaker.data.EmployeeEntity
import com.rainweaver.salaryslipmaker.data.ProfileDatabase
import com.rainweaver.salaryslipmaker.data.ProfileRepository
import com.rainweaver.salaryslipmaker.domain.ProfileValidation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProfileUiState(
    val business: BusinessProfileEntity = BusinessProfileEntity(),
    val employees: List<EmployeeEntity> = emptyList(),
    val loaded: Boolean = false,
    val message: String? = null,
)

class ProfileViewModel(app: Application) : AndroidViewModel(app) {
    private val database = ProfileDatabase.get(app)
    private val repository = ProfileRepository(database.businessProfileDao(), database.employeeDao())
    private val _state = MutableStateFlow(ProfileUiState())
    val state: StateFlow<ProfileUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeBusiness().collect { profile ->
                _state.update { it.copy(business = profile ?: BusinessProfileEntity(), loaded = true) }
            }
        }
        viewModelScope.launch {
            repository.observeEmployees().collect { employees ->
                _state.update { it.copy(employees = employees) }
            }
        }
    }

    fun saveBusiness(profile: BusinessProfileEntity) = viewModelScope.launch {
        if (ProfileValidation.requiredNameError(profile.businessName) != null) {
            message("Business name is required")
            return@launch
        }
        repository.saveBusiness(profile.copy(businessName = profile.businessName.trim()))
        message("Business profile saved")
    }

    fun saveEmployee(employee: EmployeeEntity) = viewModelScope.launch {
        if (ProfileValidation.requiredNameError(employee.fullName) != null) {
            message("Employee name is required")
            return@launch
        }
        repository.saveEmployee(employee)
        message(if (employee.id == 0L) "Employee added" else "Employee updated")
    }

    fun setArchived(id: Long, archived: Boolean) = viewModelScope.launch {
        repository.setArchived(id, archived)
        message(if (archived) "Employee archived" else "Employee restored")
    }

    fun message(value: String) = _state.update { it.copy(message = value) }
    fun consumeMessage() = _state.update { it.copy(message = null) }
}
