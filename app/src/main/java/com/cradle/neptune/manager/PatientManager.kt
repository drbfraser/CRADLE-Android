package com.cradle.neptune.manager

import com.cradle.neptune.database.PatientDaoAccess
import com.cradle.neptune.database.PatientEntity

class PatientManager(private val daoAccess: PatientDaoAccess) {

    fun add(patientEntity: PatientEntity) = daoAccess.insert(patientEntity)

    fun addAll(patientEntities: List<PatientEntity>) = daoAccess.insertAll(patientEntities)

    fun delete(patientEntity: PatientEntity) = daoAccess.delete(patientEntity)

    fun getAllPatients(): List<PatientEntity> = daoAccess.allPatients

    fun getPatientById(id: String): PatientEntity? = daoAccess.getPatientById(id)

    fun deleteAllPatients() = daoAccess.deleteAllPatients()
}
