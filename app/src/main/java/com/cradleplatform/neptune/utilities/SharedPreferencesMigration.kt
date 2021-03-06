package com.cradleplatform.neptune.utilities

import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit

/**
 * Handles migrations to values stored in SharedPreferences.
 *
 * A new version signifies a change in values or structure, e.g., changing the type of a value
 * stored in the SharedPreference XML to another type, or changing the name of a key.
 * If we want to encrypt the SharedPreferences, we would have to do a conversion
 * here to ensure the values are all encrypted.
 *
 * A SharedPreference XML file looks like this on the user's device:
 *
 *  <map>
 *      <string name="loginEmail">admin123@admin.com</string>
 *      <string name="lastSyncTime">1604971619</string>
 *      <int name="cradle_shared_preferences_version" value="1" />
 *      <string name="setting_vht_name">Admin</string>
 *      <int name="userId" value="1" />
 *      <string name="token">eyJ0eXAiO...</string>
 *  </map>
 *
 *  TODO: Set version back to 0 when out of alpha testing. This will force all alpha users to
 *        log out. (Refer to issues ticket #28)
 */
class SharedPreferencesMigration constructor(
    private val sharedPreferences: SharedPreferences
) {
    companion object {
        private const val TAG = "SharedPrefMigration"

        /*
         * List of CRADLE SharedPreference versions.
         *
         * [LATEST_SHARED_PREF_VERSION] must be changed when adding a
         * new version.
         */

        /** The default version. Use this to force a migration through all versions. */
        private const val DEFAULT_VERSION = 0

        /**
         * The latest version. This MUST be changed so that it's the latest version
         */
        const val LATEST_SHARED_PREF_VERSION = 0

        const val KEY_SHARED_PREFERENCE_VERSION = "cradle_shared_preferences_version"
    }

    /**
     * Migrates the shared preferences to the latest version. The bulk of the migration code is
     * handled in [doMigration].
     *
     * @return true if the migration was successful, false if not. If migration is not successful,
     * the recommended action is to logout, since something went wrong (or we want to force
     * the user to logout).
     */
    fun migrate(): Boolean {
        // Try to get the shared preference version. If there isn't a version stored, we assume
        // that the user is logging in for the first time, so their shared preferences
        val currentVersion = try {
            sharedPreferences.getInt(KEY_SHARED_PREFERENCE_VERSION, DEFAULT_VERSION)
        } catch (e: ClassCastException) {
            sharedPreferences.edit(commit = true) { remove(KEY_SHARED_PREFERENCE_VERSION) }
            DEFAULT_VERSION
        }

        val isMigrationSuccessful = try {
            doMigration(currentVersion)
        } catch (exception: MigrationException) {
            Log.e(TAG, "Migration exception: ${exception.message}", exception)
            false
        }

        if (isMigrationSuccessful) {
            sharedPreferences.edit(commit = true) {
                putInt(KEY_SHARED_PREFERENCE_VERSION, LATEST_SHARED_PREF_VERSION)
            }
        } else {
            Log.e(
                TAG,
                "Migration from $currentVersion to latest version " +
                    "$LATEST_SHARED_PREF_VERSION failed",
            )
        }

        return isMigrationSuccessful
    }

    @Suppress("ThrowsCount")
    private fun doMigration(oldVersion: Int): Boolean {
        if (oldVersion == LATEST_SHARED_PREF_VERSION) {
            return true
        }
        Log.d(TAG, "Migrating from version $oldVersion to $LATEST_SHARED_PREF_VERSION")
        if (oldVersion > LATEST_SHARED_PREF_VERSION) {
            Log.w(
                TAG,
                "The latest shared preferences version in the code is " +
                    "$LATEST_SHARED_PREF_VERSION but the current shared preferences version is" +
                    "$oldVersion"
            )
            // This happens if you try to checkout a version that's lower
            // than the current. As a result, there may be unexpected crashes due to changes in the
            // XML layout for the shared preferences, so we have to force the user to logout
            // and delete all of their data. This should not happen in production, because
            // we shouldn't be pushing releases that downgrade a shared preference version.
            throw MigrationException(
                "attempting to downgrade shared preference version from" +
                    "$oldVersion to $LATEST_SHARED_PREF_VERSION"
            )
        }

        /*
        Add migrations here. For example:

                (oldVersion < CHANGE_USER_ID_TO_INT).runIfTrue {
                    if (!sharedPreferences.contains("userId")) {
                        return@runIfTrue
                    }

                    val idAsString = try {
                        sharedPreferences.getString("userId", "")
                    } catch (e: ClassCastException) {
                        throw MigrationException("userId isn't a string")
                    }

                    if (idAsString == null || idAsString == "") {
                        throw MigrationException("bad userId stored")
                    }

                    val idAsInt = idAsString.toIntOrNull()
                        ?: throw MigrationException("userId isn't a numeric string")

                    sharedPreferences.edit(commit = true) {
                        remove("userId")
                        putInt("userId", idAsInt)
                    }
                }
         */

        Log.d(TAG, "Migrating from version $oldVersion to $LATEST_SHARED_PREF_VERSION successful")
        return true
    }
}

/**
 * The point of this is that we get to do local control-flow returns (return@runIfTrue)
 * for convenience.
 */
private inline fun Boolean.runIfTrue(block: () -> Unit) {
    if (this) {
        block()
    }
}

private class MigrationException(message: String) : Exception(message)
