package com.cradle.neptune.manager

import com.cradle.neptune.database.PatientDaoAccess
import com.cradle.neptune.model.Patient
import java.util.ArrayList

class PatientManager(private val daoAccess: PatientDaoAccess) {

    fun add(patient: Patient) = daoAccess.insert(patient)

    fun addAll(patients: ArrayList<Patient>) = daoAccess.insertAll(patients)

    fun delete(patient: Patient) = daoAccess.delete(patient)

    fun getAllPatients(): List<Patient> = daoAccess.allPatients

    fun getPatientById(id: String): Patient? = daoAccess.getPatientById(id)

    fun deleteAllPatients() = daoAccess.deleteAllPatients()
}
