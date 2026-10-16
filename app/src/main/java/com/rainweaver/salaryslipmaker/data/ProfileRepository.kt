package com.rainweaver.salaryslipmaker.data

class ProfileRepository(
    private val businessDao: BusinessProfileDao,
    private val employeeDao: EmployeeDao,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    fun observeBusiness() = businessDao.observe()
    fun observeEmployees() = employeeDao.observeAll()

    suspend fun saveBusiness(profile: BusinessProfileEntity) {
        businessDao.save(profile.copy(id = BusinessProfileEntity.SINGLETON_ID, updatedAt = clock()))
    }

    suspend fun saveEmployee(employee: EmployeeEntity): Long {
        val now = clock()
        val normalized = employee.copy(
            fullName = employee.fullName.trim(),
            employeeIdentifier = employee.employeeIdentifier.trim(),
            jobTitle = employee.jobTitle.trim(),
            department = employee.department.trim(),
            contactInfo = employee.contactInfo.trim(),
            notes = employee.notes.trim(),
            updatedAt = now,
        )
        return if (employee.id == 0L) {
            employeeDao.insert(normalized.copy(createdAt = now))
        } else {
            employeeDao.update(normalized)
            employee.id
        }
    }

    suspend fun setArchived(id: Long, archived: Boolean) =
        employeeDao.setArchived(id, archived, clock())
}
