package com.cradleplatform.neptune.manager

import androidx.lifecycle.LiveData
import androidx.lifecycle.map
import com.cradleplatform.neptune.database.daos.FormClassificationDao
import com.cradleplatform.neptune.model.FormClassification
import com.cradleplatform.neptune.model.FormTemplate
import com.cradleplatform.neptune.model.Patient
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manager for FormTemplate and FormClassifications
 *
 * Interacts with the [FormClassification] table in the database.
 */
@Singleton
class FormManager @Inject constructor (
    private val formClassDao: FormClassificationDao
    ){

    fun getLiveDataFormTemplates():LiveData<List<FormTemplate>> = formClassDao.getFormTemplates()
}