package com.cradleplatform.neptune.manager

import androidx.lifecycle.LiveData
import com.cradleplatform.neptune.database.daos.FormClassificationDao
import com.cradleplatform.neptune.http_sms_service.http.NetworkResult
import com.cradleplatform.neptune.http_sms_service.http.RestApi
import com.cradleplatform.neptune.model.FormClassification
import com.cradleplatform.neptune.model.FormResponse
import com.cradleplatform.neptune.model.FormTemplate
import com.cradleplatform.neptune.utilities.Protocol
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manager for FormTemplate and FormClassifications and
 * to submit the form with user's answers.
 *
 * Interacts with the [FormClassification] table in the database
 *  and uses [RestApi] for submitting [FormResponse]s
 */
@Singleton
class FormManager @Inject constructor(
    private val mRestApi: RestApi,
    private val formClassDao: FormClassificationDao
) {
    suspend fun submitFormToWebAsResponse(
        formResponse: FormResponse,
        protocol: Protocol
    ): NetworkResult<Unit> {
        return mRestApi.postFormResponse(formResponse, protocol)
    }

    suspend fun searchForFormTemplateWithName(formClassName: String): List<FormTemplate> =
        formClassDao.getFormTemplateByName(formClassName)

    suspend fun searchForFormClassNameWithFormClassId(formClassId: String): String =
        formClassDao.getFormClassNameById(formClassId)

    suspend fun addFormByClassification(formClass: FormClassification) =
        formClassDao.addOrUpdateFormClassification(formClass)

    fun getLiveDataFormTemplates(): LiveData<List<FormTemplate>> =
        formClassDao.getAllFormTemplates()

    fun getLiveDataFormClassifications(): LiveData<List<FormClassification>> =
        formClassDao.getAllFormClassifications()
}
