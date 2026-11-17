package com.cradleplatform.neptune.manager

import android.util.Log
import com.cradleplatform.neptune.database.daos.FormSubmissionV2Dao
import com.cradleplatform.neptune.http_sms_service.http.NetworkResult
import com.cradleplatform.neptune.http_sms_service.http.RestApi
import com.cradleplatform.neptune.model.FormSubmissionV2
import com.cradleplatform.neptune.model.FormSubmissionV2Entity
import com.cradleplatform.neptune.model.FormSubmissionWithAnswersV2
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FormSubmissionV2Manager @Inject constructor(
    private val restApi: RestApi,
    private val submissionDao: FormSubmissionV2Dao,
    private val templateManager: FormTemplateV2Manager
) {
    private val syncMutex = Mutex()
    suspend fun getCachedSubmissions(patientId: String): List<FormSubmissionV2> =
        submissionDao.observeSubmissionsByPatientId(patientId).first().map { it.submission }

    suspend fun syncPatientSubmissions(patientId: String): NetworkResult<Unit> = try {
        syncMutex.withLock { downloadPatientSubmissions(patientId) }
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: Exception) {
        Log.e(javaClass.simpleName, "V2 submission sync failed", exception)
        NetworkResult.NetworkException(exception)
    }

    private suspend fun downloadPatientSubmissions(patientId: String): NetworkResult<Unit> {
        val result = restApi.getPatientFormSubmissionsV2(patientId)
        if (result !is NetworkResult.Success) return result.cast()
        val submissions = mutableListOf<FormSubmissionV2Entity>()
        for (summary in result.value.distinctBy { it.id }) {
            require(summary.patientId == patientId) { "Submission belongs to another patient" }
            if (summary.archived) continue
            val detail = restApi.getFormSubmissionV2(summary.id)
            if (detail !is NetworkResult.Success) return detail.cast()
            require(detail.value.id == summary.id && detail.value.patientId == patientId &&
                detail.value.formTemplateId == summary.formTemplateId) { "Unexpected V2 submission response" }
            val template = templateManager.getTemplate(summary.formTemplateId)
            if (template !is NetworkResult.Success) return template.cast()
            val answers = requireNotNull(detail.value.answers) { "Missing V2 submission answers" }
            if (!detail.value.archived) {
                submissions.add(FormSubmissionV2Entity(summary.copy(
                    userId = detail.value.userId,
                    dateSubmitted = requireNotNull(detail.value.dateSubmitted),
                    lastEdited = requireNotNull(detail.value.lastEdited),
                    lang = detail.value.lang
                ), answers))
            }
        }
        // Keep the previous records if any detail or template download fails.
        submissionDao.replacePatientSubmissions(patientId, submissions)
        return NetworkResult.Success(Unit, result.statusCode)
    }

    suspend fun getSubmission(id: String, patientId: String): NetworkResult<FormSubmissionWithAnswersV2> {
        val cached = submissionDao.getSubmission(id, patientId)
        if (cached != null) {
            return NetworkResult.Success(cached.toDetail(), 200)
        }
        val result = syncPatientSubmissions(patientId)
        return if (result is NetworkResult.Success) {
            val downloaded = submissionDao.getSubmission(id, patientId)
            if (downloaded == null) NetworkResult.Failure(ByteArray(0), 404)
            else NetworkResult.Success(downloaded.toDetail(), 200)
        } else {
            result.cast()
        }
    }

    private fun FormSubmissionV2Entity.toDetail(): FormSubmissionWithAnswersV2 = submission.let {
        FormSubmissionWithAnswersV2(
            it.id, it.formTemplateId, it.patientId, it.userId,
            it.dateSubmitted, it.lastEdited, it.lang, it.archived, answers
        )
    }
}
