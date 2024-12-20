package com.cradleplatform.neptune.manager

import androidx.room.withTransaction
import com.cradleplatform.neptune.database.CradleDatabase
import com.cradleplatform.neptune.database.daos.PatientDao
import com.cradleplatform.neptune.database.daos.ReadingDao
import com.cradleplatform.neptune.http_sms_service.http.NetworkResult
import com.cradleplatform.neptune.http_sms_service.http.RestApi
import com.cradleplatform.neptune.http_sms_service.http.map
import com.cradleplatform.neptune.model.Patient
import com.cradleplatform.neptune.model.PatientAndReadings
import com.cradleplatform.neptune.model.Reading
import com.cradleplatform.neptune.utilities.Protocol
import kotlinx.coroutines.yield
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manager to interact with the [Patient] table in the database.
 */
@Singleton
class PatientManager @Inject constructor(
    private val database: CradleDatabase,
    private val patientDao: PatientDao,
    private val readingDao: ReadingDao,
    private val restApi: RestApi,
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
     * Get all the patients that have been created or edited offline
     */
    suspend fun getPatientsToUpload(): List<Patient> = patientDao.readPatientsToUpload()

    /**
     * Get the number of patients that have been created or edited offline
     */
    suspend fun getNumberOfPatientsToUpload(): Int = patientDao.countPatientsToUpload()

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
        val result = restApi.postPatient(patientAndReadings, Protocol.HTTP)
        if (result is NetworkResult.Success) {
            // Update the patient's `base` field if successfully uploaded
            val patient = patientAndReadings.patient
            patient.lastServerUpdate = patient.lastEdited
            add(patient)
        }

        return result.map { }
    }

    suspend fun addPregnancyOnServerSaveOnSuccess(patient: Patient): NetworkResult<Unit> {
        val result = restApi.postPregnancy(patient, Protocol.HTTP)

        if (result is NetworkResult.Success) {
            // Have to set the pregnancyId on db to id returned from server to link this record
            // to what was saved on server db
            patient.pregnancyId = result.value.id
            add(patient)
        }

        return result.map { }
    }

    suspend fun pushAndSaveEndPregnancy(patient: Patient): NetworkResult<Unit> {
        val result = restApi.putPregnancy(patient, Protocol.HTTP)

        if (result is NetworkResult.Success) {
            // Ensure all info is cleared and clear end dates
            patient.pregnancyId = null
            patient.prevPregnancyEndDate = null
            patient.prevPregnancyOutcome = null
            patient.isPregnant = false
            patient.gestationalAge = null
            add(patient)
        }

        return result.map { }
    }

    /**
     * Uploads an edited patient to the server.
     *
     * @param patient the patient to upload
     * @return whether the upload succeeded or not
     */
    suspend fun updatePatientOnServerAndSave(patient: Patient): NetworkResult<Unit> {
        val result = restApi.putPatient(patient, Protocol.HTTP)
        if (result is NetworkResult.Success) {
            patient.lastServerUpdate = patient.lastEdited
        }
        add(patient)
        return result.map { }
    }

    /**
     * Uploads an new drug/medical record
     *
     * When there is internet and the request is successful, drugLastEdited/medicalLastEdited
     * field should be set to null
     *
     * When there isn't internet connection, the patient in local DB will be updated
     * with the new record along with a drugLastEdited/medicalLastEdited at created time
     *
     * @param patient the patient to be updated with new drug/medical record
     * @param isDrugRecord if it is a drug/medical record
     * @return whether the upload succeeded or not
     */
    suspend fun updatePatientMedicalRecord(
        patient: Patient,
        isDrugRecord: Boolean
    ): NetworkResult<Unit> {
        val result = restApi.postMedicalRecord(patient, isDrugRecord, Protocol.HTTP)
        if (result is NetworkResult.Success) {
            if (isDrugRecord) patient.drugLastEdited = null
            else patient.medicalLastEdited = null
            add(patient)
        }
        return result.map { }
    }

    suspend fun downloadEditedPatientInfoFromServer(patientId: String): NetworkResult<Unit> {
        val result = restApi.getPatientInfo(patientId, Protocol.HTTP)
        if (result is NetworkResult.Success) {
            add(result.value)
        }
        return result.map { }
    }

    /**
     * Downloads just the demographic information about a patient with ID of [patientId] from
     * the server. No associated readings will be downloaded.
     */
    suspend fun downloadPatientInfoFromServer(patientId: String): NetworkResult<Patient> =
        restApi.getPatientInfo(patientId, Protocol.HTTP)

    /**
     * Downloads all the information for a patient from the server.
     *
     * @param id id of the patient to download
     */
    suspend fun downloadPatientAndReading(id: String): NetworkResult<PatientAndReadings> =
        restApi.getPatient(id, Protocol.HTTP)

    /**
     * Associates a given patient to the active user.
     *
     * This tells the server that this user would like to track changes to this
     * patient and allows it to be synced using the mobile's sync system.
     *
     * @param id id of the patient to associate
     */
    suspend fun associatePatientWithUser(id: String): NetworkResult<Unit> =
        restApi.associatePatientToUser(id, Protocol.HTTP)

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
        if (downloadResult !is NetworkResult.Success) {
            return downloadResult
        }
        // Safe to cancel here since we only downloaded patients + readings
        // After this point, we shouldn't have cancellation points, because we want the
        // resulting operations to be atomic.
        yield()

        val downloadedPatient = downloadResult.value.patient
        val associateResult = associatePatientWithUser(downloadedPatient.id)
        if (associateResult !is NetworkResult.Success) {
            // If association failed, we shouldn't add the patient and readings to our database.
            return associateResult.cast()
        }

        // Otherwise, association was successful, so add to the database
        val downloadedReadings = downloadResult.value.readings
        addPatientWithReadings(downloadedPatient, downloadedReadings, areReadingsFromServer = true)

        return downloadResult
    }
}
