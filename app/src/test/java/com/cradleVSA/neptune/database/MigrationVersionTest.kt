package com.cradleVSA.neptune.database

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Test

class MigrationVersionTest {
    @Test
    fun `test that there is a migration to current version`() {
        assert(Migrations.ALL_MIGRATIONS.count { it.endVersion == CURRENT_DATABASE_VERSION } >= 1) {
            "Missing a migration to database version $CURRENT_DATABASE_VERSION"
        }
    }

    @Test
    fun `test that there is a migration path to the current version`() {
        runBlocking {
            withTimeout(10_000L) {
                val startingVersion = 1
                assert(isTherePathToTarget(startingVersion, CURRENT_DATABASE_VERSION)) {
                    "Missing a migration to database version $CURRENT_DATABASE_VERSION"
                }
            }
        }
    }

    private fun isTherePathToTarget(currentVersion: Int, targetVersion: Int): Boolean {
        if (currentVersion > targetVersion) {
            // we don't support downgrades
            return false
        }
        if (currentVersion == targetVersion)  {
            return true
        }

        Migrations.ALL_MIGRATIONS
            .filter { it.startVersion == currentVersion }
            .also {
                if (it.isEmpty()) {
                    return false
                }
            }
            .forEach {
                if (isTherePathToTarget(it.endVersion, targetVersion)) {
                    return true
                }
            }

        return false
    }
}