package com.rainweaver.salaryslipmaker.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [BusinessProfileEntity::class, EmployeeEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class ProfileDatabase : RoomDatabase() {
    abstract fun businessProfileDao(): BusinessProfileDao
    abstract fun employeeDao(): EmployeeDao

    companion object {
        @Volatile private var instance: ProfileDatabase? = null

        fun get(context: Context): ProfileDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                ProfileDatabase::class.java,
                "salary_slip_maker.db",
            ).build().also { instance = it }
        }
    }
}
