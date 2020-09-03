package com.cradle.neptune.manager

import android.content.SharedPreferences
import android.util.Log
import com.cradle.neptune.net.NetworkResult
import com.cradle.neptune.net.RestApi
import com.cradle.neptune.net.Success
import com.cradle.neptune.net.monadicSequence
import com.cradle.neptune.sync.SyncStepperCallback
import com.cradle.neptune.sync.TotalRequestStatus
import com.cradle.neptune.utilitiles.UnixTimestamp
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

class SyncManager @Inject constructor(
    private val patientManager: PatientManager,
    private val readingManager: ReadingManager,
    private val sharedPreferences: SharedPreferences,
    private val restApi: RestApi
) {
    companion object {
        const val LAST_SYNC = "lastSyncTime"
    }

    /**
     * Invokes the mobile sync algorithm.
     */
    suspend fun sync(callback: SyncStepperCallback): NetworkResult<Unit> =
        withContext<NetworkResult<Unit>>(IO) context@{
            // Get update lists from the server
            val lastSyncTime = sharedPreferences.getLong(LAST_SYNC, 0)
            val updates = restApi.getUpdates(lastSyncTime)
            if (updates !is Success) {
                withContext(Main) {
                    callback.onFetchDataCompleted(false)
                }
                return@context updates.cast()
            }
            withContext(Main) {
                callback.onFetchDataCompleted(true)
            }

            // Upload new readings and patients from mobile to server
            val npUpload = async(IO) {
                val results = patientManager
                    .getUnUploadedPatients()
                    .filterNot { updates.value.newPatients.contains(it.patient.id) }
                    .map { patient -> patientManager.uploadPatient(patient) }
                monadicSequence(Unit, results)
            }

            val epUpload = async(IO) {
                val results = patientManager
                    .getEditedPatients(lastSyncTime)
                    .filterNot { updates.value.editedPatients.contains(it.id) }
                    .map { patient -> patientManager.updatePatientOnServer(patient) }
                monadicSequence(Unit, results)
            }

            val rUpload = async(IO) {
                val results = readingManager
                    .getUnUploadedReadingsForServerPatients()
                    .filterNot { updates.value.readings.contains(it.id) }
                    .map { reading -> restApi.postReading(reading).map { Unit } }
                monadicSequence(Unit, results)
            }

            val uploadResult = npUpload.await() sequence epUpload.await() sequence rUpload.await()
            // FIXME: UI is currently broken here as we've moved away from a
            //  sequential upload algorithm to a parallel one. We can either
            //  update each of the async blocks to increment a single status
            //  or just update the UI to something else.
            val uploadStatus = if (uploadResult is Success) {
                TotalRequestStatus(1, 0, 1)
            } else {
                TotalRequestStatus(1, 1, 0)
            }
            withContext(Main) {
                callback.onNewPatientAndReadingUploadFinish(uploadStatus)
            }

            // Download new information from server using the update lists as a guide
            val npDl = async(IO) {
                val results = updates
                    .value
                    .newPatients
                    .map { id -> // For each new patient in updates...
                        // download patient then store it in the database if successful
                        restApi.getPatient(id).map { patientAndReadings ->
                            patientManager.add(patientAndReadings.patient)
                            patientAndReadings.readings.forEach {
                                it.isUploadedToServer = true
                            }
                            readingManager.addAllReadings(patientAndReadings.readings)
                            Unit
                        }
                    }
                monadicSequence(Unit, results)
            }

            val epDl = async(IO) {
                val results = updates
                    .value
                    .editedPatients
                    .map { id -> // For each edited patient in updates...
                        // download patient then store it in the database if successful
                        restApi.getPatientInfo(id).map { patient ->
                            patientManager.add(patient)
                            Unit
                        }
                    }
                monadicSequence(Unit, results)
            }

            val rDl = async(IO) {
                val results = updates
                    .value
                    .readings
                    .map { id -> // For each reading id in updates...
                        // download reading then store it in the database if successful
                        restApi.getReading(id).map { reading ->
                            reading.isUploadedToServer = true
                            readingManager.addReading(reading)
                            Unit
                        }
                    }
                monadicSequence(Unit, results)
            }

            val fDl = async(IO) {
                val results = updates
                    .value
                    .followups
                    .map { id -> // For each assessment id in updates...
                        // Download assessment
                        restApi.getAssessment(id).map { assessment ->
                            // Lookup associated reading
                            val reading = runBlocking(IO) {
                                readingManager.getReadingById(assessment.readingId)
                            }
                            // Add assessment to reading
                            reading?.followUp = assessment
                            reading?.referral?.isAssessed = true
                            // Update reading in database
                            if (reading != null) {
                                readingManager.addReading(reading)
                            } else {
                                Log.e(
                                    this@SyncManager::class.simpleName,
                                    "Got assessment for unknown reading $assessment"
                                )
                            }
                            Unit
                        }
                    }
                monadicSequence(Unit, results)
            }

            val downloadResult = npDl.await() sequence epDl.await() sequence rDl.await() sequence fDl.await()
            // FIXME: See above
            val downloadStatus = if (downloadResult is Success) {
                TotalRequestStatus(1, 0, 1)
            } else {
                TotalRequestStatus(1, 1, 0)
            }
            withContext(Main) {
                callback.onNewPatientAndReadingDownloadFinish(downloadStatus)
            }

            // Finish up by updating the last sync timestamp in shared preferences

            // FIXME: We really should be aborting after the first error. For
            //  example, if we failed to upload we should not download etc.
            val result = uploadResult sequence downloadResult

            if (result is Success) {
                sharedPreferences.edit()
                    .putLong(LAST_SYNC, UnixTimestamp.now)
                    .apply()
                // FIXME: Not tracking errors like this anymore, find a new way to
                //  show errors to the user.
                withContext(Main) {
                    callback.onFinish(HashMap())
                }
            }

            // Return overall result
            result
        }
}
