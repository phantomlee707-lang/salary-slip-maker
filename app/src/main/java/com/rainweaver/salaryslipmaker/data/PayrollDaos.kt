package com.rainweaver.salaryslipmaker.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PayrollDao {
    @Insert
    suspend fun insertPayslip(payslip: PayslipEntity): Long

    @Update
    suspend fun updatePayslip(payslip: PayslipEntity): Int

    @Query("SELECT * FROM payslips WHERE id = :id LIMIT 1")
    suspend fun getPayslip(id: Long): PayslipEntity?

    @Query("SELECT * FROM payslips WHERE number = :number LIMIT 1")
    suspend fun getByNumber(number: String): PayslipEntity?

    @Query("SELECT * FROM payslips ORDER BY createdAt DESC, id DESC")
    fun observePayslips(): Flow<List<PayslipEntity>>

    @Query("SELECT * FROM payslips WHERE supersedesPayslipId = :id AND status = 'ISSUED' ORDER BY issuedAt DESC LIMIT 1")
    suspend fun getIssuedReplacement(id: Long): PayslipEntity?

    @Insert
    suspend fun insertPayslipLines(lines: List<PayslipLineEntity>)

    @Query("SELECT * FROM payslip_lines WHERE payslipId = :payslipId ORDER BY sortOrder ASC, id ASC")
    suspend fun getPayslipLines(payslipId: Long): List<PayslipLineEntity>

    @Query("DELETE FROM payslip_lines WHERE payslipId = :payslipId")
    suspend fun deletePayslipLines(payslipId: Long)

    @Query("UPDATE payslips SET status = 'ISSUED', issuedAt = :issuedAt, updatedAt = :issuedAt WHERE id = :id AND status = 'DRAFT'")
    suspend fun markIssuedIfDraft(id: Long, issuedAt: Long): Int

    @Query("DELETE FROM payslips WHERE id = :id AND status = 'DRAFT'")
    suspend fun deleteDraft(id: Long): Int

    @Insert
    suspend fun insertTemplate(template: SalaryTemplateEntity): Long

    @Update
    suspend fun updateTemplate(template: SalaryTemplateEntity): Int

    @Query("SELECT * FROM salary_templates WHERE id = :id LIMIT 1")
    suspend fun getTemplate(id: Long): SalaryTemplateEntity?

    @Query("SELECT * FROM salary_templates ORDER BY archived ASC, name COLLATE NOCASE ASC, id ASC")
    fun observeTemplates(): Flow<List<SalaryTemplateEntity>>

    @Query("SELECT * FROM salary_template_lines WHERE templateId = :templateId ORDER BY sortOrder ASC, id ASC")
    suspend fun getTemplateLines(templateId: Long): List<SalaryTemplateLineEntity>

    @Insert
    suspend fun insertTemplateLines(lines: List<SalaryTemplateLineEntity>)

    @Query("DELETE FROM salary_template_lines WHERE templateId = :templateId")
    suspend fun deleteTemplateLines(templateId: Long)

    @Query("UPDATE salary_templates SET archived = 1, updatedAt = :updatedAt WHERE id = :id")
    suspend fun archiveTemplate(id: Long, updatedAt: Long): Int

    @Query("UPDATE salary_templates SET archived = 0, updatedAt = :updatedAt WHERE id = :id")
    suspend fun restoreTemplate(id: Long, updatedAt: Long): Int

    @Query("SELECT * FROM payslips ORDER BY id ASC")
    suspend fun getAllPayslipsForBackup(): List<PayslipEntity>

    @Query("SELECT * FROM payslip_lines ORDER BY id ASC")
    suspend fun getAllPayslipLinesForBackup(): List<PayslipLineEntity>

    @Query("SELECT * FROM salary_templates ORDER BY id ASC")
    suspend fun getAllTemplatesForBackup(): List<SalaryTemplateEntity>

    @Query("SELECT * FROM salary_template_lines ORDER BY id ASC")
    suspend fun getAllTemplateLinesForBackup(): List<SalaryTemplateLineEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun restorePayslips(rows: List<PayslipEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun restorePayslipLines(rows: List<PayslipLineEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun restoreTemplates(rows: List<SalaryTemplateEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun restoreTemplateLines(rows: List<SalaryTemplateLineEntity>)

    @Query("DELETE FROM payslips")
    suspend fun clearPayslipsForRestore()

    @Query("DELETE FROM salary_templates")
    suspend fun clearTemplatesForRestore()
}
