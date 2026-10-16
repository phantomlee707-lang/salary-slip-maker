package com.rainweaver.salaryslipmaker.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.rainweaver.salaryslipmaker.domain.PayslipStatus

class PayrollConverters {
    @TypeConverter
    fun statusToStorage(status: PayslipStatus): String = status.name

    @TypeConverter
    fun storageToStatus(value: String): PayslipStatus = PayslipStatus.valueOf(value)
}

@Database(
    entities = [
        BusinessProfileEntity::class,
        EmployeeEntity::class,
        SalaryTemplateEntity::class,
        SalaryTemplateLineEntity::class,
        PayslipEntity::class,
        PayslipLineEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
@TypeConverters(PayrollConverters::class)
abstract class ProfileDatabase : RoomDatabase() {
    abstract fun businessProfileDao(): BusinessProfileDao
    abstract fun employeeDao(): EmployeeDao
    abstract fun payrollDao(): PayrollDao

    companion object {
        @Volatile private var instance: ProfileDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `salary_templates` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `employeeId` INTEGER, `currencyCode` TEXT NOT NULL, `archived` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_salary_templates_employeeId` ON `salary_templates` (`employeeId`)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `salary_template_lines` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `templateId` INTEGER NOT NULL, `kind` TEXT NOT NULL, `label` TEXT NOT NULL, `amount` TEXT NOT NULL, `sortOrder` INTEGER NOT NULL, FOREIGN KEY(`templateId`) REFERENCES `salary_templates`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_salary_template_lines_templateId` ON `salary_template_lines` (`templateId`)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `payslips` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `number` TEXT NOT NULL, `status` TEXT NOT NULL, `businessName` TEXT NOT NULL, `businessLogoUri` TEXT, `businessAddress` TEXT NOT NULL, `businessPhone` TEXT NOT NULL, `businessEmail` TEXT NOT NULL, `businessRegistrationId` TEXT NOT NULL, `businessAccentColorHex` TEXT NOT NULL, `businessAdditionalInfo` TEXT NOT NULL, `employeeId` INTEGER, `employeeName` TEXT NOT NULL, `employeeIdentifier` TEXT NOT NULL, `employeeJobTitle` TEXT NOT NULL, `employeeDepartment` TEXT NOT NULL, `periodStartDay` INTEGER, `periodEndDay` INTEGER, `paymentDay` INTEGER, `currencyCode` TEXT NOT NULL, `grossEarnings` TEXT NOT NULL, `totalDeductions` TEXT NOT NULL, `netPay` TEXT NOT NULL, `paymentMethod` TEXT NOT NULL, `paymentReference` TEXT NOT NULL, `notes` TEXT NOT NULL, `supersedesPayslipId` INTEGER, `supersedesPayslipNumber` TEXT, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `issuedAt` INTEGER)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_payslips_number` ON `payslips` (`number`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_payslips_employeeId` ON `payslips` (`employeeId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_payslips_supersedesPayslipId` ON `payslips` (`supersedesPayslipId`)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `payslip_lines` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `payslipId` INTEGER NOT NULL, `kind` TEXT NOT NULL, `label` TEXT NOT NULL, `amount` TEXT NOT NULL, `sortOrder` INTEGER NOT NULL, FOREIGN KEY(`payslipId`) REFERENCES `payslips`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_payslip_lines_payslipId` ON `payslip_lines` (`payslipId`)")
            }
        }

        fun get(context: Context): ProfileDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                ProfileDatabase::class.java,
                "salary_slip_maker.db",
            ).addMigrations(MIGRATION_1_2).build().also { instance = it }
        }

        fun closeForRestore() = synchronized(this) {
            instance?.close()
            instance = null
        }
    }
}
