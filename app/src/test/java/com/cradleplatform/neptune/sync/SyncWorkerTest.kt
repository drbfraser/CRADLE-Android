package com.cradleplatform.neptune.sync

/**
 * Integration test Class for [SyncWorker] that utilizes parsing functionalities
 * from [com.cradleplatform.neptune.net.RestApi]
 *
 * To be implemented:
 */
class SyncWorkerTest {

/*
    TODO: this is moved from LoginManagerTest when it was testing download (refer to issue 67)
     These are some very useful code snippets for reference that belongs to here when testing download(Sync).

    // **** The Mock Api functionalities was moved to [MockWebServerUtils.kt] ****

    // **** Moved from old LoginManagerTest.kt, useful for testing IO error ****
    @Test
    fun `login with right credentials and logout`() {
        failTheLoginWithIOIssues = false

        runBlocking {

            val result = withTimeout(Duration.seconds(10)) {
                loginManager.login(LoginManagerTests.TEST_USER_EMAIL, LoginManagerTests.TEST_USER_PASSWORD, parallelDownload = false)
            }
            assert(result is NetworkResult.Success) {
                "expected login to be successful, but it failed, with " +
                    "result $result and\n" +
                    "shared prefs map $fakeSharedPreferences"
            }

            val latestVersion = SharedPreferencesMigration.LATEST_SHARED_PREF_VERSION
            val versionStored = fakeSharedPreferences[
                SharedPreferencesMigration.KEY_SHARED_PREFERENCE_VERSION] as? Int
            Assertions.assertEquals(latestVersion, versionStored) {
                "expected stored shared pref version to be $latestVersion, but got $versionStored" +
                    "make sure that the login stores the SharedPreferences version, otherwise " +
                    "migrations in the future can fail"
            }

            assert(loginManager.isLoggedIn())
            Assertions.assertEquals(LoginManagerTests.TEST_AUTH_TOKEN, fakeSharedPreferences["token"]) { "bad auth token" }
            Assertions.assertEquals(LoginManagerTests.TEST_USER_ID, fakeSharedPreferences["userId"]) { "bad userId" }
            Assertions.assertEquals(
                LoginManagerTests.TEST_FIRST_NAME,
                fakeSharedPreferences[mockContext.getString(R.string.key_vht_name)]
            ) { "bad first name" }

            val role = UserRole.safeValueOf(fakeSharedPreferences[mockContext.getString(R.string.key_role)] as String)
            Assertions.assertEquals(LoginManagerTests.TEST_USER_ROLE, role) { "unexpected role $role; expected ${LoginManagerTests.TEST_USER_ROLE}" }

            assert(fakeSharedPreferences.containsKey(SyncWorker.LAST_PATIENT_SYNC)) {
                "missing last patient sync time; shared pref dump: $fakeSharedPreferences"
            }
            assert(BigInteger(fakeSharedPreferences[SyncWorker.LAST_PATIENT_SYNC] as String).toLong() > 100L) {
                "last patient sync time too small"
            }
            assert(fakeSharedPreferences.containsKey(SyncWorker.LAST_READING_SYNC)) {
                "missing last reading sync time"
            }
            assert(BigInteger(fakeSharedPreferences[SyncWorker.LAST_READING_SYNC]!! as String).toLong() > 100L) {
                "last reading sync time too small"
            }
            Assertions.assertNotEquals(0, fakeHealthFacilityDatabase.size) {
                "LoginManager failed to do health facility download; dumping facility db: $fakeHealthFacilityDatabase"
            }

            val userSelectedHealthFacilities = fakeHealthFacilityDatabase
                .filter { it.isUserSelected }
            Assertions.assertNotEquals(0, userSelectedHealthFacilities.size) {
                "LoginManager failed to select a health facility; dumping facility db: $fakeHealthFacilityDatabase"
            }
            Assertions.assertEquals(1, userSelectedHealthFacilities.size) {
                "LoginManager selected too many health health facilities"
            }
            Assertions.assertEquals(LoginManagerTests.TEST_USER_FACILITY_NAME, userSelectedHealthFacilities[0].name) {
                "wrong health facility selected"
            }

            Assertions.assertEquals(
                CommonPatientReadingJsons.allPatientsJsonExpectedPair.second.size,
                fakePatientDatabase.size
            ) {
                "parsing the patients failed: not enough patients parsed"
            }

            Assertions.assertEquals(
                CommonReadingJsons.allReadingsJsonExpectedPair.second.size,
                fakeReadingDatabase.size
            ) {
                "parsing the readings failed: not enough readings parsed"
            }

            fakeReadingDatabase.forEach {
                assert(it.isUploadedToServer)
            }
            fakePatientDatabase.forEach {
                assert(it.lastServerUpdate != null)
            }

            // Verify that the streamed parsing via Jackson of the simulated downloading of all
            // patients and their readings from the server was correct.
            val expectedPatients = CommonPatientReadingJsons
                .allPatientsJsonExpectedPair.second.map { it.patient }
            expectedPatients.forEach { expectedPatient ->
                // The patient ID must have been parsed correctly at least since that's the
                // primary key. When we find a match, we then check to see if all of the fields
                // are the same. Doing a direct equality check gives us less information about
                // what failed.
                fakePatientDatabase.find { it.id == expectedPatient.id }
                    ?.let { parsedPatient ->
                        Assertions.assertEquals(expectedPatient, parsedPatient) {
                            "found a patient with the same ID, but one or more properties are wrong"
                        }
                    }
                    ?: fail { "couldn't find expected patient in fake database: $expectedPatient" }
            }

            val expectedReadings = CommonReadingJsons.allReadingsJsonExpectedPair.second
            expectedReadings.forEach { reading ->
                val expectedReading = reading.copy(isUploadedToServer = true)
                fakeReadingDatabase.find { it.id == expectedReading.id }
                    ?.let { parsedReading ->
                        Assertions.assertEquals(expectedReading, parsedReading) {
                            "found a reading with the same ID, but one or more properties are" +
                                " wrong"
                        }
                    }
                    ?: fail { "couldn't find expected reading in fake database: $expectedReading" }
            }

            loginManager.logout()

            assert(fakeSharedPreferences.isEmpty())
            assert(fakePatientDatabase.isEmpty())
            assert(fakeReadingDatabase.isEmpty())
            assert(fakeHealthFacilityDatabase.isEmpty())
        }
    }

    // **** Moved from old LoginManagerTest.kt, useful for testing IO error ****
     @Test
    fun `login with right credentials, IO error during download, nothing should be added`() {
        failTheLoginWithIOIssues = true
        mockDatabase.clearAllTables()

        runBlocking {
            val result = loginManager.login(
                TEST_USER_EMAIL,
                TEST_USER_PASSWORD,
                parallelDownload = false
            )
            // Note: we say success, but this just lets us move on from the LoginActivity.
            assert(result is NetworkResult.Success) {
                "expected a Success, but got result $result and " +
                    "shared prefs map $fakeSharedPreferences"
            }

            assert(fakeSharedPreferences.containsKey(SyncWorker.LAST_PATIENT_SYNC)) {
                "sync time should be stored as the patient download process is successful"
            }

            assert(fakeSharedPreferences.containsKey(SyncWorker.LAST_READING_SYNC)) {
                "sync time should be stored as the reading download process is successful"
            }

            // Should be logged in, but the download of patients and facilities failed
            assert(loginManager.isLoggedIn())

            // withTransaction should make it so that the changes are not committed.
            assertEquals(0, fakePatientDatabase.size) { "nothing should be added" }
            assertEquals(0, fakeReadingDatabase.size) { "nothing should be added" }
        }
    }


    */




}