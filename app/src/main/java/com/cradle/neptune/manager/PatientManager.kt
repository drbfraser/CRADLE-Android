package com.cradle.neptune.manager

import com.cradle.neptune.database.PatientDaoAccess
import com.cradle.neptune.model.Patient
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.ArrayList

class PatientManager(private val daoAccess: PatientDaoAccess) {

    fun add(patient: Patient) = GlobalScope.launch {daoAccess.insert(patient) }

    suspend fun addAll(patients: ArrayList<Patient>) = GlobalScope.launch {daoAccess.insertAll(patients)}

    suspend fun delete(patient: Patient) = daoAccess.delete(patient)

    suspend fun deleteAll() = daoAccess.deleteAllPatients()

    suspend fun getAllPatients(): List<Patient> = daoAccess.allPatients

    /**
     * TODO: once all the java classes calling this method are turned into Kotlin,
     * remove this function and call the corressponding method.
     * This is only for legacy java code still calling this function.
     */
    @Deprecated("Please avoid using this function in Kotlin files.")
    fun getAllPatientsBlocking() = runBlocking { getAllPatients() }

    suspend fun getPatientById(id: String): Patient? = daoAccess.getPatientById(id)

    /**
     * TODO: once all the java classes calling this method are turned into Kotlin,
     * remove this function and call the corressponding method.
     * This is only for legacy java code still calling this function.
     */
    @Deprecated("Please avoid using this function in Kotlin files.")
    fun getPatientByIdBlocking(id: String): Patient? = runBlocking { getPatientById(id) }
}
