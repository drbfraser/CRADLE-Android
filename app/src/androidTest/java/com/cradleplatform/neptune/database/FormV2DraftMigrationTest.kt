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

    @Test
    fun migrate4To5KeepsExistingDraftAndAddsAnEmptySubmissionId() {
        val oldDatabase = helper.createDatabase("form-v2-submission-id-migration-test", 4)
        oldDatabase.execSQL(
            "INSERT INTO FormV2Draft VALUES ('patient-1', 'template-1', 1, '{}', '[]', 'English', 1000, 1000)"
        )
        oldDatabase.close()

        val migrated = helper.runMigrationsAndValidate(
            "form-v2-submission-id-migration-test", 5, true, *Migrations.ALL_MIGRATIONS
        )
        assertEquals(null, value(migrated, "FormV2Draft", "submissionId"))
        assertEquals("English", value(migrated, "FormV2Draft", "language"))
        migrated.close()
    }

    @Test
    fun migrate7To8PreservesDraftsAndV1ResponsesAndAddsSubmissionCache() {
        val databaseName = "form-v2-submission-cache-migration-test"
        val oldDatabase = helper.createDatabase(databaseName, 7)
        insertPatient(oldDatabase)
        insertV1FormResponse(oldDatabase)
        oldDatabase.execSQL(
            """
            INSERT INTO FormV2Draft VALUES
                ('patient-1', 'template-1', 1, '{}', '[]', 'French', 1000, 2000, 'stable-id')
            """.trimIndent()
        )
        oldDatabase.close()

        val migrated = helper.runMigrationsAndValidate(databaseName, 8, true, *Migrations.ALL_MIGRATIONS)
        assertEquals(1, count(migrated, "Patient"))
        assertEquals(1, count(migrated, "FormResponse"))
        assertEquals("{\"q1\":{\"text\":\"existing answer\"}}", value(migrated, "FormResponse", "answers"))
        assertEquals(1, count(migrated, "FormV2Draft"))
        assertEquals("stable-id", value(migrated, "FormV2Draft", "submissionId"))
        assertEquals("French", value(migrated, "FormV2Draft", "language"))
        assertEquals("1000", value(migrated, "FormV2Draft", "createdAt"))
        assertEquals("2000", value(migrated, "FormV2Draft", "updatedAt"))
        assertEquals(0, count(migrated, "FormSubmissionV2Entity"))
        migrated.close()
    }

    @Test
    fun migrate8To9PreservesSubmissionsWithUnknownRevision() {
        val databaseName = "$DATABASE-8-to-9"
        val oldDatabase = helper.createDatabase(databaseName, 8)
        insertPatient(oldDatabase)
        oldDatabase.execSQL(
            """
            INSERT INTO FormSubmissionV2Entity
                (id, formTemplateId, patientId, userId, dateSubmitted, lastEdited, lang, archived, name, answers)
            VALUES ('s1', 't1', 'patient-1', 1, 1000, 1000, 'English', 0, 'Form', '[]')
            """.trimIndent()
        )
        oldDatabase.close()
        val migrated = helper.runMigrationsAndValidate(databaseName, 9, true, *Migrations.ALL_MIGRATIONS)
        assertEquals(1, count(migrated, "Patient"))
        assertEquals("[]", value(migrated, "FormSubmissionV2Entity", "answers"))
        migrated.query("SELECT revision FROM FormSubmissionV2Entity WHERE id = 's1'").use {
            assertEquals(true, it.moveToFirst())
            assertEquals(true, it.isNull(0))
        }
        migrated.close()
    }

    @Test
    fun migrate9To10PreservesDraftsAndAddsAnUnknownSubmissionTimeZone() {
        val databaseName = "$DATABASE-9-to-10"
        val oldDatabase = helper.createDatabase(databaseName, 9)
        insertPatient(oldDatabase)
        oldDatabase.execSQL(
            """
            INSERT INTO FormV2Draft VALUES
                ('patient-1', 'template-1', 1, '{}', '[]', 'French', 1000, 2000, 'stable-id')
            """.trimIndent()
        )
        oldDatabase.close()

        val migrated = helper.runMigrationsAndValidate(databaseName, 10, true, *Migrations.ALL_MIGRATIONS)
        assertEquals(1, count(migrated, "Patient"))
        assertEquals("stable-id", value(migrated, "FormV2Draft", "submissionId"))
        assertEquals("French", value(migrated, "FormV2Draft", "language"))
        assertEquals(null, value(migrated, "FormV2Draft", "submissionTimeZoneId"))
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

    private fun value(database: SupportSQLiteDatabase, table: String, column: String): String? =
        database.query("SELECT `$column` FROM `$table`").use { cursor ->
            cursor.moveToFirst()
            if (cursor.isNull(0)) null else cursor.getString(0)
        }

    companion object {
        private const val DATABASE = "form-v2-draft-migration-test"
    }
}
