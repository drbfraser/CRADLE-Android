package com.cradleplatform.neptune.manager

import android.util.Log
import com.cradleplatform.neptune.database.daos.FormSubmissionV2Dao
import com.cradleplatform.neptune.http_sms_service.http.NetworkResult
import com.cradleplatform.neptune.http_sms_service.http.RestApi
import com.cradleplatform.neptune.model.FormSubmissionV2
import com.cradleplatform.neptune.model.FormSubmissionV2Entity
import com.cradleplatform.neptune.model.FormSubmissionWithAnswersV2
import com.cradleplatform.neptune.model.AnswerWithQuestionV2
import com.cradleplatform.neptune.model.CreateFormSubmissionRequestV2
import com.cradleplatform.neptune.model.FormTemplateV2
import com.cradleplatform.neptune.model.getTranslation
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
    private val cacheMutex = Mutex()
    private var cacheRevision = 0L

    suspend fun cacheSubmittedForm(
        submission: FormSubmissionV2,
        request: CreateFormSubmissionRequestV2,
        template: FormTemplateV2
    ): Unit = cacheMutex.withLock {
        require(submission.id == request.id && submission.patientId == request.patientId &&
            submission.formTemplateId == template.id) { "Unexpected V2 submission response" }
        val questions = template.questions.orEmpty().associateBy { it.id }
        val answers = request.answers.map { answer ->
            val question = requireNotNull(questions[answer.questionId])
            AnswerWithQuestionV2(
                id = null, questionId = answer.questionId, formSubmissionId = submission.id,
                answer = answer.answer, questionType = question.questionType,
                questionText = question.questionText.getTranslation(request.lang).orEmpty(),
                mcOptions = question.mcOptions.orEmpty().map {
                    it.translations.getTranslation(request.lang).orEmpty()
                },
                order = question.order
            )
        }
        // The POST response supplies server timestamps; answers are the exact submitted payload.
        templateManager.cacheTemplate(template)
        submissionDao.upsert(FormSubmissionV2Entity(
            submission.copy(
                name = submission.name ?: template.classification.name.getTranslation(request.lang)
            ), answers
        ))
        cacheRevision++
    }

    /** Caches a verified submission returned while recovering from a lost POST response. */
    suspend fun cacheVerifiedSubmission(
        submission: FormSubmissionWithAnswersV2,
        request: CreateFormSubmissionRequestV2,
        template: FormTemplateV2
    ): Unit = cacheMutex.withLock {
        require(submission.id == request.id && submission.patientId == request.patientId &&
            submission.formTemplateId == template.id && submission.lang == request.lang &&
            !submission.archived) { "Unexpected V2 submission response" }
        val cachedSubmission = FormSubmissionV2(
            id = requireNotNull(submission.id),
            formTemplateId = submission.formTemplateId,
            patientId = submission.patientId,
            userId = submission.userId,
            dateSubmitted = requireNotNull(submission.dateSubmitted),
            lastEdited = requireNotNull(submission.lastEdited),
            lang = submission.lang,
            archived = submission.archived,
            name = template.classification.name.getTranslation(submission.lang),
            revision = submission.revision
        )
        templateManager.cacheTemplate(template)
        submissionDao.upsert(FormSubmissionV2Entity(
            cachedSubmission, requireNotNull(submission.answers)
        ))
        cacheRevision++
    }

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
        val revisionBeforeDownload = cacheMutex.withLock { cacheRevision }
        val result = restApi.getPatientFormSubmissionsV2(patientId)
        if (result is NetworkResult.Failure && result.statusCode == 403) {
            cacheMutex.withLock {
                // Only the patient list endpoint confirms denial of all of this patient's records.
                submissionDao.replacePatientSubmissions(patientId, emptyList())
                cacheRevision++
            }
        }
        if (result !is NetworkResult.Success) return result.cast()
        val cached = submissionDao.observeSubmissionsByPatientId(patientId).first()
            .associateBy { it.submission.id }
        val submissions = mutableListOf<FormSubmissionV2Entity>()
        for (summary in result.value.distinctBy { it.id }) {
            require(summary.patientId == patientId) { "Submission belongs to another patient" }
            if (summary.archived) continue
            val existing = cached[summary.id]
            // Timestamps alone cannot distinguish two edits within the same second.
            if (summary.revision != null && summary.revision > 0 && existing?.submission == summary) {
                val template = templateManager.getTemplate(summary.formTemplateId)
                if (template !is NetworkResult.Success) return template.cast()
                submissions.add(requireNotNull(existing))
                continue
            }
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
                    lang = detail.value.lang,
                    revision = detail.value.revision
                ), answers))
            }
        }
        cacheMutex.withLock {
            // A POST cached during this download makes its snapshot stale. Refresh on the next sync.
            if (cacheRevision == revisionBeforeDownload) {
                submissionDao.replacePatientSubmissions(patientId, submissions)
            }
        }
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
            it.dateSubmitted, it.lastEdited, it.lang, it.archived, answers, it.revision
        )
    }
}
