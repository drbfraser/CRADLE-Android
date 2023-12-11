package com.cradleplatform.neptune.manager

import com.cradleplatform.neptune.http_sms_service.http.RestApi
import androidx.lifecycle.LiveData
import com.cradleplatform.neptune.database.daos.FormResponseDao
import com.cradleplatform.neptune.model.FormResponse
import com.cradleplatform.neptune.http_sms_service.http.NetworkResult
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
    private val mRestApi: RestApi,
    private val formResponseDao: FormResponseDao
) {
    suspend fun submitFormToWebAsResponse(formResponse: FormResponse): NetworkResult<Unit> {
        return mRestApi.postFormResponse(formResponse)
    }

    suspend fun searchForFormResponseById(id: Int): FormResponse? =
        formResponseDao.getFormResponseById(id)

    suspend fun searchForFormResponseByPatientId(id: String): List<FormResponse>? =
        formResponseDao.getAllFormResponseByPatientId(id)

    suspend fun updateOrInsertIfNotExistsFormResponse(formResponse: FormResponse) =
        formResponseDao.updateOrInsertIfNotExists(formResponse)

    fun getFormResponsesLiveData(): LiveData<List<FormResponse>> =
        formResponseDao.getAllFormResponsesLiveData()
}
