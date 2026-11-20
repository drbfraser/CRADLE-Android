package com.cradleplatform.neptune.database

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class PatientNumberMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        CradleDatabase::class.java.canonicalName,
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun migrate6To7CopiesTypedIdsToPatientNumberAndLeavesUuidsEmpty() {
        val oldDatabase = helper.createDatabase(DATABASE, 6)
        insertPatient(oldDatabase, id = "12345", name = "Typed on mobile")
        insertPatient(oldDatabase, id = UUID_ID, name = "Created on web")
        oldDatabase.close()

        // Also checks that the Patient table and the LocalSearchPatient view match what Room
        // expects for version 7.
        val migrated = helper.runMigrationsAndValidate(DATABASE, 7, true, *Migrations.ALL_MIGRATIONS)

        // IDs are kept, so that patients still match the server.
        assertEquals("12345", patientNumberOf(migrated, "12345"))
        assertEquals(null, patientNumberOf(migrated, UUID_ID))

        // The patient list view includes the patient number.
        migrated.query(
            "SELECT patientNumber FROM LocalSearchPatient WHERE id = '12345'"
        ).use { cursor ->
            cursor.moveToFirst()
            assertEquals("12345", cursor.getString(0))
        }
        migrated.close()
    }

    private fun insertPatient(database: SupportSQLiteDatabase, id: String, name: String) {
        val values = ContentValues().apply {
            put("id", id)
            put("name", name)
            put("sex", "FEMALE")
            put("isPregnant", 0)
            put("drugHistory", "")
            put("medicalHistory", "")
            put("allergy", "")
            put("isArchived", 0)
        }
        database.insert("Patient", SQLiteDatabase.CONFLICT_ABORT, values)
    }

    private fun patientNumberOf(database: SupportSQLiteDatabase, id: String): String? =
        database.query("SELECT patientNumber FROM Patient WHERE id = ?", arrayOf(id)).use { cursor ->
            cursor.moveToFirst()
            if (cursor.isNull(0)) null else cursor.getString(0)
        }

    companion object {
        private const val DATABASE = "patient-number-migration-test"
        private const val UUID_ID = "0b8f2c1e-7d4a-4c5b-9e3f-2a6d8c1b4e7f"
    }
}
