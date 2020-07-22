package com.cradle.neptune.manager

import com.cradle.neptune.database.PatientDaoAccess
import com.cradle.neptune.model.Patient
import java.util.ArrayList

class PatientManager(private val daoAccess: PatientDaoAccess) {

    suspend fun add(patient: Patient) = daoAccess.insert(patient)

    suspend fun addAll(patients: ArrayList<Patient>) = daoAccess.insertAll(patients)

    suspend fun delete(patient: Patient) = daoAccess.delete(patient)

    suspend fun getAllPatients(): List<Patient> = daoAccess.allPatients

    suspend fun getPatientById(id: String): Patient? = daoAccess.getPatientById(id)
}
