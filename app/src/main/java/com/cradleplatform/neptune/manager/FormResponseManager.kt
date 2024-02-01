package com.cradleplatform.neptune.manager

import com.cradleplatform.neptune.http_sms_service.http.RestApi
import com.cradleplatform.neptune.database.daos.FormClassificationDao
import com.cradleplatform.neptune.database.daos.FormResponseDao
import com.cradleplatform.neptune.model.FormResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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

    suspend fun searchForFormResponseByPatientId(id: String): MutableList<FormResponse>? =
        formResponseDao.getAllFormResponseByPatientId(id)

    /**
     * Updates or inserts a new [FormResponse]. Checking is done through form response's id.
     *
     * @param formResponse [FormResponse] The form response that should be updated or inserted
     */
    suspend fun updateOrInsertIfNotExistsFormResponse(formResponse: FormResponse) {
        // If the formResponse has no form classification name, retrieve the name by grabbing the
        // form class itself.
        if (formResponse.formTemplate.formClassName == null) {
            formResponse.formTemplate.formClassName =
                formClassificationDao.getFormClassNameById(formResponse.formClassificationId)
        }
        // Insert the new formResponse.
        formResponseDao.updateOrInsertIfNotExists(formResponse)
    }

    /**
     * Deletes a [FormResponse] with the [formResponseId].
     *
     * @param formResponseId [Long] The id of the form response that should be deleted
     * @return The number of rows affected  in the database(i.e., 1 if a [FormResponse] with the given
     * [formResponseId] was found and deleted, 0 if not found).
     */

    /**
     * Deletes a [FormResponse].
     *
     * @param formResponse The form response to be deleted
     */
     suspend fun deleteFormResponse(formResponse: FormResponse) {
        formResponseDao.delete(formResponse)
    }

    suspend fun deleteFormResponseById(formResponseId: Long) =
        formResponseDao.deleteById(formResponseId)

    /**
     * Returns the number of form responses that are outdated, and removes them from the database.
     *
     * @return The number of outdated form responses
     */
    suspend fun purgeOutdatedFormResponses(): Int {
        var purgeCount = 0
        CoroutineScope(Dispatchers.IO).launch {
            val formResponses = formResponseDao.getAllFormResponses()
            formResponses.forEach { formResponse ->
                val upToDateFormTemplate =
                    formClassificationDao.getFormTemplateById(formResponse.formTemplate.formClassId!!)
                val outdated = formResponse.formTemplate.version != upToDateFormTemplate.version
                if (outdated) {
                    formResponseDao.deleteById(formResponse.formResponseId)
                    purgeCount += 1
                }
            }
        }
        return purgeCount
    }


}
