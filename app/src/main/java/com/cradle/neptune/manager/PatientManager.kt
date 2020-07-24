package com.cradle.neptune.manager

import com.cradle.neptune.database.PatientDaoAccess
import com.cradle.neptune.model.Patient
import java.util.ArrayList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

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
    suspend fun getAllPatients(): List<Patient> = daoAccess.allPatients

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
}
