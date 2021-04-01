package com.cradleVSA.neptune.database

import androidx.room.RoomDatabase
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
}