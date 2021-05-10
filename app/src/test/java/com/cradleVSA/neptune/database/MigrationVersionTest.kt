package com.cradleVSA.neptune.database

import androidx.room.RoomDatabase
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MigrationVersionTest {
    @Test
    fun `test that there is a migration path to the current version`() {
        val migrations = RoomDatabase.MigrationContainer().apply {
            addMigrations(*Migrations.ALL_MIGRATIONS)
        }
        for (currentVersion in 1 until CURRENT_DATABASE_VERSION) {
            val migrationPath = migrations.findMigrationPath(currentVersion, CURRENT_DATABASE_VERSION)
            assert(!migrationPath.isNullOrEmpty()) {
                "missing path from db version $currentVersion to db version $CURRENT_DATABASE_VERSION"
            }
        }
    }

    @Test
    fun `test the maximum migration is actually current database version`() {
        if (Migrations.ALL_MIGRATIONS.isEmpty()) {
            assert(CURRENT_DATABASE_VERSION in 0..1) {
                "If there are no migrations, we expect the database version is either 0 or 1! " +
                    "Instead, the CURRENT_DATABASE_VERSION is $CURRENT_DATABASE_VERSION"
            }
        } else {
            assertEquals(
                CURRENT_DATABASE_VERSION,
                Migrations.ALL_MIGRATIONS.maxOf { it.endVersion }
            ) {
                """
                    The current database version is $CURRENT_DATABASE_VERSION, but array of migrations
                    ${Migrations::class.simpleName}.${Migrations::ALL_MIGRATIONS.name} does have the
                     latest version as the highest migration version.
                """.trimIndent()
            }
        }
    }
}