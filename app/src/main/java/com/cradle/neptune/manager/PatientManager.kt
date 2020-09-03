package com.cradle.neptune.manager

import com.cradle.neptune.database.PatientDaoAccess
import com.cradle.neptune.model.Patient
import com.cradle.neptune.model.PatientAndReadings
import com.cradle.neptune.net.NetworkResult
import com.cradle.neptune.net.RestApi
import com.cradle.neptune.net.Success
import java.util.ArrayList
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

/**
 * manager to interact with the [Patient] table
 *Added [suspend] function so that there is compile time error when inserting on DB through
 * main thread rather than run time error
 */
@Suppress("RedundantSuspendModifier")
class PatientManager @Inject constructor(
    private val daoAccess: PatientDaoAccess,
    private val urlManager: UrlManager,
    private val restApi: RestApi
) {

    /**
     * add a single patient
     */
    fun add(patient: Patient) = GlobalScope.launch { daoAccess.insert(patient) }

    /**
     * add all patients
     */
    fun addAll(patients: ArrayList<Patient>) = GlobalScope.launch { daoAccess.insertAll(patients) }

    /**
     * delete a patient by id
     */
    suspend fun delete(id: String) {
        getPatientById(id)?.let { daoAccess.delete(it) }
    }

    /**
     * delete all the patients
     */
    suspend fun deleteAll() = daoAccess.deleteAllPatients()

    /**
     * get all the patients
     */
    suspend fun getAllPatients(): List<Patient> =
        withContext(IO) { daoAccess.allPatients }

    /**
     * get a list of patient ids for all patients.
     */
    suspend fun getPatientIdsOnly(): List<String> =
        withContext(IO) { daoAccess.patientIdsList }

    /**
     * get individual patient by id if exists
     */
    suspend fun getPatientById(id: String): Patient? = daoAccess.getPatientById(id)

    /**
     * TODO: once all the java classes calling this method are turned into Kotlin,
     * remove this function and call the corressponding method.
     * This is only for legacy java code still calling this function.
     */
    @Deprecated("Please avoid using this function in Kotlin files.")
    fun getPatientByIdBlocking(id: String): Patient? = runBlocking {
        withContext(IO) { getPatientById(id) }
    }

    /**
     * returns all the  patients which dont exists on server and their readings
     */
    suspend fun getUnUploadedPatients(): List<PatientAndReadings> =
        withContext(IO) { daoAccess.unUploadedPatientAndReadings }

    /**
     * get edited Patients that also exists on the server
     */
    suspend fun getEditedPatients(timeStamp: Long): List<Patient> =
        withContext(IO) { daoAccess.getEditedPatients(timeStamp) }

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
    suspend fun uploadPatient(patientAndReadings: PatientAndReadings): NetworkResult<Unit> =
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

    /**
     * Downloads all the information for a patient from the server.
     *
     * @param id id of the patient to download
     */
    suspend fun downloadPatient(id: String): NetworkResult<PatientAndReadings> =
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
        restApi.associatePatientToUser(id).map { Unit }

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

            downloadPatient(id)
        }
}
