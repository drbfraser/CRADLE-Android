package com.cradleplatform.neptune.database

import android.content.ContentValues
import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteDatabase
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test

class FormV2DraftMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        CradleDatabase::class.java.canonicalName,
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun migrate3To4PreservesExistingDataAndAddsAnEmptyDraftTable() {
        val oldDatabase = helper.createDatabase(DATABASE, 3)
        oldDatabase.execSQL("PRAGMA foreign_keys = ON")
        insertPatient(oldDatabase)
        insertV1FormResponse(oldDatabase)
        oldDatabase.close()

        val migrated = helper.runMigrationsAndValidate(DATABASE, 4, true, *Migrations.ALL_MIGRATIONS)
        migrated.execSQL("PRAGMA foreign_keys = ON")
        assertEquals(1, count(migrated, "Patient"))
        assertEquals(1, count(migrated, "FormResponse"))
        assertEquals("Existing patient", value(migrated, "Patient", "name"))
        assertEquals("{\"q1\":{\"text\":\"existing answer\"}}", value(migrated, "FormResponse", "answers"))
        assertTrue(rows(migrated, "FormV2Draft").isEmpty())

        migrated.execSQL(
            """
            INSERT INTO FormV2Draft VALUES ('patient-1', 'template-1', 1, '{}', '[]', 'English', 1000, 1000)
            """.trimIndent()
        )
        assertCompositeKeyAndPatientCascade(migrated)
        migrated.close()
    }

    private fun insertPatient(database: SupportSQLiteDatabase) {
        val values = ContentValues().apply {
            put("id", "patient-1")
            put("name", "Existing patient")
            put("sex", "FEMALE")
            put("isPregnant", 0)
            put("drugHistory", "existing drug history")
            put("medicalHistory", "existing medical history")
            put("allergy", "existing allergy")
            put("isArchived", 0)
        }
        database.insert("Patient", SQLiteDatabase.CONFLICT_ABORT, values)
    }

    private fun insertV1FormResponse(database: SupportSQLiteDatabase) {
        val values = ContentValues().apply {
            put("formTemplate", "{\"existing\":true}")
            put("answers", "{\"q1\":{\"text\":\"existing answer\"}}")
            put("saveResponseToSendLater", 1)
            put("archived", 0)
            put("formClassificationId", "classification-1")
            put("formClassificationName", "Existing classification")
            put("dateCreated", 1000L)
            put("language", "English")
            put("questionResponses", "[]")
            put("patientId", "patient-1")
            put("dateEdited", 1000L)
        }
        database.insert("FormResponse", SQLiteDatabase.CONFLICT_ABORT, values)
    }

    private fun assertCompositeKeyAndPatientCascade(database: SupportSQLiteDatabase) {
        try {
            database.execSQL(
                """
                INSERT INTO FormV2Draft VALUES ('patient-1', 'template-1', 2, '{}', '[]', 'English', 2000, 2000)
                """.trimIndent()
            )
            fail("The composite key must reject a second draft for the same patient and template")
        } catch (_: SQLiteConstraintException) {
            assertEquals(1, count(database, "FormV2Draft"))
        }
        database.execSQL("DELETE FROM Patient WHERE id = 'patient-1'")
        assertEquals(0, count(database, "FormV2Draft"))
        database.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
    }

    private fun count(database: SupportSQLiteDatabase, table: String): Int =
        database.query("SELECT COUNT(*) FROM `$table`").use { cursor ->
            cursor.moveToFirst()
            cursor.getInt(0)
        }

    private fun rows(database: SupportSQLiteDatabase, table: String): List<String> =
        database.query("SELECT * FROM `$table`").use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(cursor.getString(0))
            }
        }

    private fun value(database: SupportSQLiteDatabase, table: String, column: String): String =
        database.query("SELECT `$column` FROM `$table`").use { cursor ->
            cursor.moveToFirst()
            cursor.getString(0)
        }

    companion object {
        private const val DATABASE = "form-v2-draft-migration-test"
    }
}
