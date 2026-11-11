package com.cradleplatform.neptune.manager

import android.util.Log
import com.cradleplatform.neptune.database.daos.FormTemplateV2Dao
import com.cradleplatform.neptune.http_sms_service.http.NetworkResult
import com.cradleplatform.neptune.http_sms_service.http.FormSyncResult
import com.cradleplatform.neptune.http_sms_service.http.RestApi
import com.cradleplatform.neptune.http_sms_service.http.map
import com.cradleplatform.neptune.model.FormTemplateShallowV2
import com.cradleplatform.neptune.model.FormTemplateV2
import com.cradleplatform.neptune.model.FormTemplateV2Entity
import com.cradleplatform.neptune.model.getTranslation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection.HTTP_OK
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FormTemplateV2Manager @Inject constructor(
    private val restApi: RestApi,
    private val formTemplateDao: FormTemplateV2Dao
) {
    suspend fun syncAvailableTemplates(
        reportProgress: suspend (current: Int, total: Int) -> Unit = { _, _ -> }
    ): FormSyncResult = withContext(Dispatchers.IO) {
        try {
            val summaryResult = restApi.getAllClassificationsSummaryV2()
            if (summaryResult !is NetworkResult.Success) {
                return@withContext FormSyncResult(summaryResult.cast(), 0)
            }

            val summaries = summaryResult.value.filter { it.archived != true }.distinctBy { it.id }
            val templates = mutableListOf<FormTemplateV2Entity>()
            var downloadedTemplates = 0
            for (summary in summaries) {
                val cachedTemplate = formTemplateDao.getTemplate(summary.id)?.template
                    ?.takeIf { it.questions != null && it.archived != true }
                val template = if (cachedTemplate != null && cachedTemplate.version == summary.version) {
                    cachedTemplate
                } else {
                    // The summary is shallow; download the full template only when needed.
                    val templateResult = restApi.getFormTemplateV2(summary.id)
                    if (templateResult !is NetworkResult.Success) {
                        return@withContext FormSyncResult(templateResult.cast(), 0)
                    }
                    val downloadedTemplate = templateResult.value
                    require(downloadedTemplate.id == summary.id && downloadedTemplate.questions != null) {
                        "Incomplete V2 template response for ${summary.id}"
                    }
                    downloadedTemplates++
                    downloadedTemplate
                }
                templates.add(FormTemplateV2Entity(template.id, template, availableForNewForms = true))
                reportProgress(templates.size, summaries.size)
            }

            // Refresh availability only after all required downloads have succeeded.
            formTemplateDao.replaceAvailableTemplates(templates)
            FormSyncResult(NetworkResult.Success(Unit, summaryResult.statusCode), downloadedTemplates)
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            Log.e(javaClass.simpleName, "V2 form template sync failed", exception)
            FormSyncResult(NetworkResult.NetworkException(exception), 0)
        }
    }

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
