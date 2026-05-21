package com.adyapan.leaddialer

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities     = [Lead::class, CallRecord::class, AttendanceRecord::class],
    version      = 11,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun leadDao()       : LeadDao
    abstract fun callRecordDao() : CallRecordDao
    abstract fun attendanceDao() : AttendanceDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /** Migration 3 → 4: add nullable firestoreId column. */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE leads ADD COLUMN firestoreId TEXT")
            }
        }

        /** Migration 5 → 6: add collegeName and collegeCity columns. */
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE leads ADD COLUMN collegeName TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE leads ADD COLUMN collegeCity TEXT NOT NULL DEFAULT ''")
            }
        }

        /** Migration 6 → 7: add isHotLead column. */
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE leads ADD COLUMN isHotLead INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** Migration 7 → 8: add calledBy column. */
        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE leads ADD COLUMN calledBy TEXT NOT NULL DEFAULT ''")
            }
        }

        /**
         * Migration 8 → 9: rename `timestamp` → `calledAt` in call_records.
         * SQLite < 3.25 doesn't support RENAME COLUMN, so we recreate the table.
         */
        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // 1. Create new table with calledAt instead of timestamp
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS call_records_new (
                        id       INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        phone    TEXT NOT NULL,
                        name     TEXT NOT NULL,
                        duration INTEGER NOT NULL,
                        calledAt INTEGER NOT NULL,
                        status   TEXT NOT NULL
                    )
                """.trimIndent())
                // 2. Copy old data, mapping timestamp → calledAt
                database.execSQL(
                    """
    INSERT INTO call_records_new
    (id, phone, name, duration, calledAt, status)

    SELECT
    id,
    phone,
    name,
    duration,
    calledAt,
    status

    FROM call_records
    """.trimIndent()
                )
                // 3. Drop old table and rename new
                database.execSQL("DROP TABLE call_records")
                database.execSQL("ALTER TABLE call_records_new RENAME TO call_records")
            }
        }

        /**
         * Migration 9 → 10: deduplicate call_records then add a unique index on (phone, calledAt).
         * Step 1 deletes true duplicates (same phone + same calledAt), keeping the row with the
         * highest id. Step 2 then safely creates the unique index.
         */
        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Delete duplicate rows — keep only the one with the MAX id per (phone, calledAt) pair
                database.execSQL("""
                    DELETE FROM call_records
                    WHERE id NOT IN (
                        SELECT MAX(id)
                        FROM call_records
                        GROUP BY phone, calledAt
                    )
                """.trimIndent())

                // Now safe to add the unique index
                database.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_call_records_phone_calledAt ON call_records (phone, calledAt)"
                )
            }
        }

        /** Migration 10 → 11: add salesDone column to track completed sales. */
        private val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE leads ADD COLUMN salesDone INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "crm_database"
                )
                    .addMigrations(MIGRATION_3_4, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11)
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}