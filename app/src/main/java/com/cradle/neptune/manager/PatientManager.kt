package com.cradle.neptune.manager

import com.cradle.neptune.database.CradleDatabase
import com.cradle.neptune.database.PatientDaoAccess
import com.cradle.neptune.database.ReadingDaoAccess
import com.cradle.neptune.model.Patient
import com.cradle.neptune.model.PatientAndReadings
import com.cradle.neptune.model.Reading
import com.cradle.neptune.net.NetworkResult
import com.cradle.neptune.net.RestApi
import com.cradle.neptune.net.Success
import java.lang.IllegalArgumentException
import java.util.ArrayList
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.withContext

/**
 * Manager to interact with the [Patient] table in the database.
 */
class PatientManager @Inject constructor(
    private val database: CradleDatabase,
    private val patientDao: PatientDaoAccess,
    private val readingDao: ReadingDaoAccess,
    private val restApi: RestApi
) {

    /**
     * add a single patient
     */
    suspend fun add(patient: Patient) = withContext(IO) { patientDao.insert(patient) }

    /**
     * add all patients
     */
    suspend fun addAll(patients: ArrayList<Patient>) = withContext(IO) {
        patientDao.insertAll(patients)
    }

    /**
     * Adds a patient and its reading in a single transaction. The [reading] should be for the
     * given [patient].
     *
     * @throws IllegalArgumentException if the [reading] given has a different patient ID from the
     * given [patient]'s ID
     */
    suspend fun addPatientWithReading(patient: Patient, reading: Reading) = withContext(IO) {
        if (patient.id != reading.patientId) {
            throw IllegalArgumentException(
                "reading's patient ID doesn't match with given patient's ID"
            )
        }
        database.runInTransaction {
            patientDao.insert(patient)
            readingDao.insert(reading)
        }
    }

    /**
     * delete a patient by id
     */
    suspend fun delete(id: String) {
        withContext(IO) {
            getPatientById(id)?.let { patientDao.delete(it) }
        }
    }

    /**
     * delete all the patients
     */
    suspend fun deleteAll() = withContext(IO) { patientDao.deleteAllPatients() }

    /**
     * get all the patients
     */
    suspend fun getAllPatients(): List<Patient> = withContext(IO) {
        patientDao.allPatients
    }

    /**
     * get a list of patient ids for all patients.
     */
    suspend fun getPatientIdsOnly(): List<String> = withContext(IO) {
        patientDao.patientIdsList
    }

    /**
     * get individual patient by id if exists
     */
    suspend fun getPatientById(id: String): Patient? = withContext(IO) {
        patientDao.getPatientById(id)
    }

    /**
     * returns all the  patients which dont exists on server and their readings
     */
    suspend fun getUnUploadedPatients(): List<PatientAndReadings> =
        withContext(IO) { patientDao.unUploadedPatientAndReadings }

    /**
     * get edited Patients that also exists on the server
     */
    suspend fun getEditedPatients(timeStamp: Long): List<Patient> =
        withContext(IO) { patientDao.getEditedPatients(timeStamp) }

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
    suspend fun uploadNewPatient(patientAndReadings: PatientAndReadings): NetworkResult<Unit> =
        withContext(IO) {
            val result = restApi.postPatient(patientAndReadings)
            if (result is Success) {
                // Update the patient's `base` field if successfully uploaded
                val patient = patientAndReadings.patient
                patient.base = patient.lastEdited
                add(patient)
            }

            result.map { Unit }
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
    suspend fun updatePatientOnServer(patient: Patient): NetworkResult<Unit> =
        withContext(IO) {
            val result = restApi.putPatient(patient)
            if (result is Success) {
                // Update the patient's `base` field if successfully uploaded
                patient.base = patient.lastEdited
                add(patient)
            }

            result.map { Unit }
        }

    suspend fun downloadEditedPatientInfoFromServer(patientId: String): NetworkResult<Unit> =
        withContext(IO) {
            val result = restApi.getPatientInfo(patientId)
            if (result is Success) {
                add(result.value)
            }

            result.map { Unit }
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
     * Associates a given patient to the active user and then downloads all
     * said patient's information. The act of associating a patient to a user
     * means that the patient will be tracked when syncing with the server.
     *
     * @param id id of the patient to associate and download
     */
    @Suppress("RemoveExplicitTypeArguments") // fails to compile without type argument
    suspend fun associatePatientAndDownload(id: String): NetworkResult<PatientAndReadings> =
        withContext<NetworkResult<PatientAndReadings>>(IO) {
            val associateResult = associatePatientWithUser(id)
            if (associateResult.failed) {
                return@withContext associateResult.cast()
            }

            downloadPatientAndReading(id)
        }
}
