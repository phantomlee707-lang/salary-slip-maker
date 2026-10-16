package com.rainweaver.salaryslipmaker.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface BusinessProfileDao {
    @Query("SELECT * FROM business_profile WHERE id = 1 LIMIT 1")
    fun observe(): Flow<BusinessProfileEntity?>

    @Query("SELECT * FROM business_profile WHERE id = 1 LIMIT 1")
    suspend fun get(): BusinessProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(profile: BusinessProfileEntity)

    @Query("DELETE FROM business_profile")
    suspend fun clearForRestore()
}

@Dao
interface EmployeeDao {
    @Insert
    suspend fun insert(employee: EmployeeEntity): Long

    @Update
    suspend fun update(employee: EmployeeEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun restoreAll(employees: List<EmployeeEntity>)

    @Query("SELECT * FROM employees ORDER BY id ASC")
    suspend fun getAllForBackup(): List<EmployeeEntity>

    @Query("DELETE FROM employees")
    suspend fun clearForRestore()

    @Query("SELECT * FROM employees ORDER BY archived ASC, fullName COLLATE NOCASE ASC, id ASC")
    fun observeAll(): Flow<List<EmployeeEntity>>

    @Query("SELECT * FROM employees WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): EmployeeEntity?

    @Query("UPDATE employees SET archived = :archived, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setArchived(id: Long, archived: Boolean, updatedAt: Long)
}
