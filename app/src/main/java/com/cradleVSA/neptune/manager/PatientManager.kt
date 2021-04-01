package com.cradleVSA.neptune.manager

import androidx.room.withTransaction
import com.cradleVSA.neptune.database.CradleDatabase
import com.cradleVSA.neptune.database.daos.PatientDao
import com.cradleVSA.neptune.database.daos.ReadingDao
import com.cradleVSA.neptune.model.Patient
import com.cradleVSA.neptune.model.PatientAndReadings
import com.cradleVSA.neptune.model.Reading
import com.cradleVSA.neptune.net.Failure
import com.cradleVSA.neptune.net.NetworkResult
import com.cradleVSA.neptune.net.RestApi
import com.cradleVSA.neptune.net.Success
import kotlinx.coroutines.yield
import javax.inject.Inject

/**
 * Manager to interact with the [Patient] table in the database.
 */
class PatientManager @Inject constructor(
    private val database: CradleDatabase,
    private val patientDao: PatientDao,
    private val readingDao: ReadingDao,
    private val restApi: RestApi
) {

    /**
     * add a single patient
     */
    suspend fun add(patient: Patient) {
        patientDao.updateOrInsertIfNotExists(patient)
    }

    /**
     * Adds a patient and its reading to the database in a single transaction.
     * The [reading] should be for the given [patient].
     *
     * @throws IllegalArgumentException if the [reading] given has a different patient ID from the
     * given [patient]'s ID
     */
    suspend fun addPatientWithReading(
        patient: Patient,
        reading: Reading,
        isReadingFromServer: Boolean,
    ) {
        if (patient.id != reading.patientId) {
            throw IllegalArgumentException(
                "reading's patient ID doesn't match with given patient's ID"
            )
        }

        database.withTransaction {
            patientDao.updateOrInsertIfNotExists(patient)
            if (isReadingFromServer) {
                reading.isUploadedToServer = true
            }
            readingDao.insert(reading)
        }
    }

    /**
     * Adds a patient and its readings to the local database in a single transaction.
     */
    suspend fun addPatientWithReadings(
        patient: Patient,
        readings: List<Reading>,
        areReadingsFromServer: Boolean
    ) {
        if (areReadingsFromServer) {
            readings.forEach { it.isUploadedToServer = true }
        }

        database.withTransaction {
            patientDao.updateOrInsertIfNotExists(patient)
            readingDao.insertAll(readings)
        }
    }

    /**
     * get a list of patient ids for all patients.
     */
    suspend fun getPatientIdsOnly(): List<String> = patientDao.getPatientIdsList()

    /**
     * get individual patient by id if exists
     */
    suspend fun getPatientById(id: String): Patient? = patientDao.getPatientById(id)

    /**
     * Get patients.
     */
    suspend fun getPatientsForUpload(): List<Patient> = patientDao.getPatientsForUpload()

    /**
     * Get patients.
     */
    suspend fun getNumberOfPatientsForUpload(): List<Patient> = patientDao.getPatientsForUpload()

    /**
     * Uploads a patient and associated readings to the server.
     *
     * Upon successful upload, the patient's `base` field will be updated to
     * reflect the fact that any changes made on mobile have been received by
     * the server.
     *
     * @param patientAndReadings the patient to upload
     * @return whether the upload succeeded or not
     */
    suspend fun uploadNewPatient(patientAndReadings: PatientAndReadings): NetworkResult<Unit> {
        val result = restApi.postPatient(patientAndReadings)
        if (result is Success) {
            // Update the patient's `base` field if successfully uploaded
            val patient = patientAndReadings.patient
            patient.base = patient.lastEdited
            add(patient)
        }

        return result.map { }
    }

    /**
     * Uploads an edited patient to the server.
     *
     * Upon successful upload, the patient's `base` field will be updated to
     * reflect the fact that any changes made on mobile have been received by
     * the server.
     *
     * @param patient the patient to upload
     * @return whether the upload succeeded or not
     */
    suspend fun updatePatientOnServer(patient: Patient): NetworkResult<Unit> {
        val result = restApi.putPatient(patient)
        if (result is Success) {
            // Update the patient's `base` field if successfully uploaded
            patient.base = patient.lastEdited
            add(patient)
        }

        return result.map { }
    }

    suspend fun downloadEditedPatientInfoFromServer(patientId: String): NetworkResult<Unit> {
        val result = restApi.getPatientInfo(patientId)
        if (result is Success) {
            add(result.value)
        }
        return result.map { }
    }

    /**
     * Downloads just the demographic information about a patient with ID of [patientId] from
     * the server. No associated readings will be downloaded.
     */
    suspend fun downloadPatientInfoFromServer(patientId: String): NetworkResult<Patient> =
        restApi.getPatientInfo(patientId)

    /**
     * Downloads all the information for a patient from the server.
     *
     * @param id id of the patient to download
     */
    suspend fun downloadPatientAndReading(id: String): NetworkResult<PatientAndReadings> =
        restApi.getPatient(id)

    /**
     * Associates a given patient to the active user.
     *
     * This tells the server that this user would like to track changes to this
     * patient and allows it to be synced using the mobile's sync system.
     *
     * @param id id of the patient to associate
     */
    suspend fun associatePatientWithUser(id: String): NetworkResult<Unit> =
        restApi.associatePatientToUser(id)

    /**
     * Downloads patient information and readings for the given [patientId], associates the patient
     * with the server, then saves the patient and its readings into the local database. The act of
     * associating a patient to a user means that the patient will be tracked when syncing with the
     * server.
     *
     * The patient and readings will not be saved in the local database if association fails.
     *
     * @param patientId id of the patient to download, associate, and save
     * @return A [NetworkResult] of type [Success] for the download patient result if the patient
     * was associated and saved successfully, else a [Failure] or [Exception] if either downloading
     * or association failed.
     */
    suspend fun downloadAssociateAndSavePatient(
        patientId: String
    ): NetworkResult<PatientAndReadings> {
        val downloadResult = downloadPatientAndReading(patientId)
        if (downloadResult !is Success) {
            return downloadResult
        }
        // Safe to cancel here since we only downloaded patients + readings
        // After this point, we shouldn't have cancellation points, because we want the
        // resulting operations to be atomic.
        yield()

        val downloadedPatient = downloadResult.value.patient
        val associateResult = associatePatientWithUser(downloadedPatient.id)
        if (associateResult !is Success) {
            // If association failed, we shouldn't add the patient and readings to our database.
            return associateResult.cast()
        }

        // Otherwise, association was successful, so add to the database
        val downloadedReadings = downloadResult.value.readings
        addPatientWithReadings(downloadedPatient, downloadedReadings, areReadingsFromServer = true)

        return downloadResult
    }
}
