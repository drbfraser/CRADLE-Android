package com.cradleplatform.neptune.manager

import android.util.Log
import com.cradleplatform.neptune.model.FormTemplate
import com.cradleplatform.neptune.net.RestApi
import androidx.lifecycle.LiveData
import com.cradleplatform.neptune.database.daos.FormClassificationDao
import com.cradleplatform.neptune.model.FormClassification
import com.cradleplatform.neptune.model.FormResponse
import com.cradleplatform.neptune.net.NetworkResult
import com.google.gson.GsonBuilder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manager for FormTemplate and FormClassifications and
 * to submit the form with user's answers.
 *
 * Interacts with the [FormClassification] table in the database.
 */
@Singleton
class FormManager @Inject constructor(
    private val mRestApi: RestApi,
    private val formClassDao: FormClassificationDao
) {
    suspend fun submitFormToWebAsResponse(formResponse: FormResponse): NetworkResult<Unit> {

        val formStr = GsonBuilder().setPrettyPrinting().create().toJson(formResponse)
        val formChunks = formStr.chunked(2048)
        formChunks.forEach {
            Log.d("SendingForm", it)
        }

        return mRestApi.putFormResponse(formResponse)
    }

    suspend fun searchForFormTemplateWithName(formClassName: String): List<FormTemplate> =
        formClassDao.getFormTemplateByName(formClassName)

    suspend fun addFormByClassification(formClass: FormClassification) =
        formClassDao.addOrUpdateFormClassification(formClass)

    fun getLiveDataFormTemplates(): LiveData<List<FormTemplate>> =
        formClassDao.getAllFormTemplates()

    fun getLiveDataFormClassifications(): LiveData<List<FormClassification>> =
        formClassDao.getAllFormClassifications()
}
