package com.cradleplatform.neptune.manager

import com.cradleplatform.neptune.http_sms_service.http.RestApi
import com.cradleplatform.neptune.database.daos.FormClassificationDao
import com.cradleplatform.neptune.database.daos.FormResponseDao
import com.cradleplatform.neptune.model.FormResponse
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manager for FormTemplate and FormClassifications and
 * to submit the form with user's answers.
 *
 * Interacts with the [FormResponse] table in the database
 *  and uses [RestApi] for submitting [FormResponse]s
 */
@Singleton
class FormResponseManager @Inject constructor(
    private val formResponseDao: FormResponseDao,
    private val formClassificationDao: FormClassificationDao
) {
    suspend fun searchForFormResponseById(id: Long): FormResponse? =
        formResponseDao.getFormResponseById(id)

    suspend fun searchForFormResponseByPatientId(id: String): List<FormResponse>? =
        formResponseDao.getAllFormResponseByPatientId(id)

    suspend fun updateOrInsertIfNotExistsFormResponse(formResponse: FormResponse) {
        if (formResponse.formTemplate.formClassName == null) {
            formResponse.formTemplate.formClassName = formClassificationDao.getFormClassNameById(formResponse.formClassificationId)
        }
        formResponseDao.deleteById(formResponse.formResponseId)
        formResponseDao.updateOrInsertIfNotExists(formResponse)
    }

    suspend fun deleteFormResponseById(formResponseId: Long) =
        formResponseDao.deleteById(formResponseId)
}
