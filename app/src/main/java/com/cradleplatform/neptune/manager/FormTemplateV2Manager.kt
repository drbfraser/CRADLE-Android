package com.cradleplatform.neptune.manager

import com.cradleplatform.neptune.database.daos.FormTemplateV2Dao
import com.cradleplatform.neptune.http_sms_service.http.NetworkResult
import com.cradleplatform.neptune.http_sms_service.http.RestApi
import com.cradleplatform.neptune.http_sms_service.http.map
import com.cradleplatform.neptune.model.FormTemplateShallowV2
import com.cradleplatform.neptune.model.FormTemplateV2
import com.cradleplatform.neptune.model.FormTemplateV2Entity
import com.cradleplatform.neptune.model.getTranslation
import kotlinx.coroutines.CancellationException
import java.net.HttpURLConnection.HTTP_OK
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FormTemplateV2Manager @Inject constructor(
    private val restApi: RestApi,
    private val formTemplateDao: FormTemplateV2Dao
) {
    suspend fun getAvailableTemplates(): NetworkResult<List<FormTemplateShallowV2>> = try {
        val cachedTemplates = formTemplateDao.getAvailableTemplates()
        if (cachedTemplates.isNotEmpty()) {
            NetworkResult.Success(cachedTemplates.map { cached ->
                val template = cached.template
                FormTemplateShallowV2(
                    id = template.id,
                    formClassificationId = template.classification.id.orEmpty(),
                    version = template.version,
                    archived = template.archived == true,
                    name = template.classification.name.getTranslation("English") ?: template.id,
                    dateCreated = template.dateCreated
                )
            }.sortedBy { it.name }, HTTP_OK)
        } else {
            // Before the first sync, keep the existing online selection flow.
            restApi.getAllFormTemplatesV2().map { it.templates }
        }
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: Exception) {
        NetworkResult.NetworkException(exception)
    }

    suspend fun getTemplate(id: String): NetworkResult<FormTemplateV2> = try {
        val cachedTemplate = formTemplateDao.getTemplate(id)
        if (cachedTemplate != null) {
            NetworkResult.Success(cachedTemplate.template, HTTP_OK)
        } else {
            val result = restApi.getFormTemplateV2(id)
            if (result is NetworkResult.Success) {
                require(result.value.id == id && result.value.questions != null) {
                    "Incomplete V2 template response for $id"
                }
                // Only Sync replaces the catalog of templates available for new forms.
                formTemplateDao.saveTemplate(FormTemplateV2Entity(id, result.value))
            }
            result
        }
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: Exception) {
        NetworkResult.NetworkException(exception)
    }
}
