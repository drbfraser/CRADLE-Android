package com.cradle.neptune.manager

import com.cradle.neptune.database.PatientDaoAccess
import com.cradle.neptune.model.Patient
import com.cradle.neptune.model.PatientAndReadings
import java.util.ArrayList
import kotlinx.coroutines.Dispatchers
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
class PatientManager(private val daoAccess: PatientDaoAccess) {

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
    suspend fun getAllPatients(): List<Patient> = withContext(Dispatchers.IO) { daoAccess.allPatients }

    /**
     * get a list of patient ids for all patients.
     */
    suspend fun getPatientIdsOnly(): List<String> = withContext(Dispatchers.IO) { daoAccess.patientIdsList }

    /**
     * TODO: once all the java classes calling this method are turned into Kotlin,
     * remove this function and call the corressponding method.
     * This is only for legacy java code still calling this function.
     */
    @Deprecated("Please avoid using this function in Kotlin files.")
    fun getAllPatientsBlocking() = runBlocking {
        withContext(Dispatchers.IO) { getAllPatients() }
    }

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
        withContext(Dispatchers.IO) { getPatientById(id) }
    }

    /**
     * returns all the  patients which dont exists on server and their readings
     */
    suspend fun getUnUploadedPatients(): List<PatientAndReadings> =
        withContext(Dispatchers.IO) { daoAccess.unUploadedPatientAndReadings }

    /**
     * get edited Patients that also exists on the server
     */
    suspend fun getEditedPatients(timeStamp: Long): List<Patient> = withContext(Dispatchers.IO) { daoAccess.getEditedPatients(timeStamp) }
}
