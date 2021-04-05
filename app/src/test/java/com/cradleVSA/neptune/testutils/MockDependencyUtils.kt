package com.cradleVSA.neptune.testutils

import android.content.SharedPreferences
import androidx.room.withTransaction
import com.cradleVSA.neptune.database.CradleDatabase
import com.cradleVSA.neptune.database.daos.HealthFacilityDao
import com.cradleVSA.neptune.database.daos.PatientDao
import com.cradleVSA.neptune.database.daos.ReadingDao
import com.cradleVSA.neptune.manager.HealthFacilityManager
import com.cradleVSA.neptune.model.HealthFacility
import com.cradleVSA.neptune.model.Patient
import com.cradleVSA.neptune.model.Reading
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic

object MockDependencyUtils {
    private fun <T> putInFakeSharedPreference(
        prefs: MutableMap<String, Any?>,
        key: String?,
        value: T?
    ) {
        key ?: return
        prefs[key] = value
    }

    /**
     * Returns a map and a mocked [SharedPreferences] using the map as the underlying
     * data structure for the SharedPreferences.
     */
    fun createMockSharedPreferences(): Pair<MutableMap<String, Any?>, SharedPreferences> {
        val sharedPreferencesMap = mutableMapOf<String, Any?>()
        val sharedPreferences = mockk<SharedPreferences> {
            every { edit() } returns mockk editor@{
                every { putString(any(), any()) } answers {
                    putInFakeSharedPreference<String?>(sharedPreferencesMap, firstArg(), secondArg())
                    this@editor
                }

                every { getString(any(), any()) } answers {
                    val stringValue = sharedPreferencesMap[firstArg()] as String?
                    if (stringValue == null && !sharedPreferencesMap.contains(firstArg())) {
                        secondArg()
                    } else {
                        stringValue
                    }
                }

                every { putInt(any(), any()) } answers {
                    putInFakeSharedPreference<Int?>(sharedPreferencesMap, firstArg(), secondArg())
                    this@editor
                }

                every { putLong(any(), any()) } answers {
                    putInFakeSharedPreference<Long?>(sharedPreferencesMap, firstArg(), secondArg())
                    this@editor
                }

                every { contains(any()) } answers { sharedPreferencesMap.containsKey(firstArg()) }

                every { commit() } returns true
                every { apply() } returns Unit
                every { clear() } answers {
                    sharedPreferencesMap.clear()
                    this@editor
                }
            }
        }
        return sharedPreferencesMap to sharedPreferences
    }

    /**
     * Returns a list as the underlying "database" implementation along with a mocked
     * [HealthFacilityManager] that uses the list as its database.
     */
    fun createMockHealthFacilityManager(): Pair<MutableList<HealthFacility>, HealthFacilityManager> {
        val healthFacilityDatabase = mutableListOf<HealthFacility>()
        val mockHealthFacilityManager = mockk<HealthFacilityManager> {
            coEvery {
                addAll(any())
            } answers {
                val healthFacilities: List<HealthFacility> = arg(0) ?: return@answers
                healthFacilities.forEach { facilityToBeAdded ->
                    healthFacilityDatabase.find { it.id == facilityToBeAdded.id }.let {
                        healthFacilityDatabase.remove(it)
                    }
                }
                healthFacilityDatabase.addAll(healthFacilities)
            }
            coEvery {
                add(any())
            } answers {
                val healthFacility = firstArg<HealthFacility>()
                // Replace
                healthFacilityDatabase.find { it.id == healthFacility.id }.let {
                    healthFacilityDatabase.remove(it)
                }
                healthFacilityDatabase.add(firstArg())
            }
        }
        return healthFacilityDatabase to mockHealthFacilityManager
    }

    var isMockkStaticForDatabaseKtDone = false

    fun createMockedDatabaseDependencies(): MockedDatabaseDependencies {
        val fakePatientDatabase = mutableListOf<Patient>()
        val fakeReadingDatabase = mutableListOf<Reading>()
        val fakeHealthFacilityDatabase = mutableListOf<HealthFacility>()

        val mockHealthFacilityDao = mockk<HealthFacilityDao>(relaxed = true) {
            coEvery { insert(any()) } answers {
                fakeHealthFacilityDatabase.add(firstArg())
                // some database row id?
                fakeHealthFacilityDatabase.size.toLong()
            }
        }

        val mockPatientDao = mockk<PatientDao> {
            coEvery { insert(any()) } answers {
                fakePatientDatabase.add(firstArg())
                // some database row id?
                fakePatientDatabase.size.toLong()
            }
            coEvery { updateOrInsertIfNotExists(any()) } answers {
                val patient = firstArg<Patient>()
                var indexOfPatient = -1
                run loop@{
                    fakePatientDatabase.forEachIndexed { currentIndex, p ->
                        if (p == patient) {
                            indexOfPatient = currentIndex
                            return@loop
                        }
                    }
                }
                fakePatientDatabase.apply {
                    if (indexOfPatient == -1) {
                        add(firstArg())
                    } else {
                        removeAt(indexOfPatient)
                        add(indexOfPatient, patient)
                    }
                }
            }
        }

        val mockReadingDao = mockk<ReadingDao> {
            coEvery { insertAll(any()) } answers {
                val readings = firstArg<List<Reading>>()
                readings.forEach { reading ->
                    fakePatientDatabase.find { it.id == reading.patientId }
                        ?: error(
                            "foreign key constraint: trying to insert reading for nonexistent patient" +
                                "; patientId ${reading.patientId} not in fake database.\n" +
                                "reading=$reading\n" +
                                "fakePatientDatabase=$fakePatientDatabase\n" +
                                "fakeReadingDatabase=$fakeReadingDatabase"
                        )
                }
                fakeReadingDatabase.addAll(readings)
            }
            coEvery { insert(any()) } answers {
                val reading: Reading = firstArg()
                fakePatientDatabase.find { it.id == reading.patientId }
                    ?: error(
                        "foreign key constraint: trying to insert reading for nonexistent patient; " +
                            "patientId ${reading.patientId} not in fake database.\n" +
                            "reading=$reading\n" +
                            "fakePatientDatabase=$fakePatientDatabase\n" +
                            "fakeReadingDatabase=$fakeReadingDatabase"
                    )
                fakeReadingDatabase.add(reading)
            }
        }

        val mockDatabase = mockk<CradleDatabase> {
            // https://stackoverflow.com/a/56652529 - if we don't do this, test will hang forever
            if (!isMockkStaticForDatabaseKtDone) {
                mockkStatic("androidx.room.RoomDatabaseKt")
                isMockkStaticForDatabaseKtDone = true
            }

            every { readingDao() } returns mockReadingDao
            every { patientDao() } returns mockPatientDao
            every { healthFacility() } returns mockHealthFacilityDao

            // FIXME: With multiple coroutines using withTransaction in LoginManager, there
            //  will be suspend lambdas that don't get called at all, because when a lambda is
            //  captured, it overrides any lambdas that was there before. To work around this,
            //  we don't test parallel downloads in LoginManager.
            coEvery { withTransaction(captureLambda<suspend () -> Unit>()) } coAnswers {
                var isTransactionSuccessful = false
                try {
                    lambda<suspend () -> Unit>().captured.invoke()
                    isTransactionSuccessful = true
                } finally {
                    if (!isTransactionSuccessful) {
                        // Simulate rollback
                        fakeHealthFacilityDatabase.clear()
                        fakePatientDatabase.clear()
                        fakeReadingDatabase.clear()
                    }
                }
            }

            every { clearAllTables() } answers {
                fakeHealthFacilityDatabase.clear()
                fakePatientDatabase.clear()
                fakeReadingDatabase.clear()
            }
            every { close() } returns Unit
        }

        return MockedDatabaseDependencies(
            mockDatabase,
            mockPatientDao,
            mockReadingDao,
            mockHealthFacilityDao,
            fakePatientDatabase,
            fakeReadingDatabase,
            fakeHealthFacilityDatabase
        )
    }

    data class MockedDatabaseDependencies(
        val mockedDatabase: CradleDatabase,
        val mockedPatientDao: PatientDao,
        val mockedReadingDao: ReadingDao,
        val mockedHealthFacilityDao: HealthFacilityDao,
        val underlyingPatientDatabase: MutableList<Patient>,
        val underlyingReadingDatabase: MutableList<Reading>,
        val underlyingHealthFacilityDatabase: MutableList<HealthFacility>
    )
}