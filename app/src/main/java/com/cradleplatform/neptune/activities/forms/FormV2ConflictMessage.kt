package com.cradleplatform.neptune.activities.forms

import android.content.Context
import com.cradleplatform.neptune.R
import com.cradleplatform.neptune.model.AnswerV2
import com.cradleplatform.neptune.model.FormTemplateV2
import com.cradleplatform.neptune.model.FormV2SubmissionComparison
import com.cradleplatform.neptune.model.QuestionTypeEnum
import com.cradleplatform.neptune.model.getTranslation
import com.cradleplatform.neptune.viewmodel.forms.FormV2SubmissionConflict
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.TimeZone

internal object FormV2ConflictMessage {
    fun build(context: Context, conflict: FormV2SubmissionConflict, template: FormTemplateV2): String {
        val localAnswers = conflict.normalizedRequest.answers.associateBy { it.questionId }
        val serverAnswers = conflict.server.answers.orEmpty().associateBy { it.questionId }
        val questions = template.questions.orEmpty().associateBy { it.id }
        val language = conflict.draftRequest.lang
        val differences = (localAnswers.keys + serverAnswers.keys).mapNotNull { id ->
            val local = localAnswers[id]?.answer
            val server = serverAnswers[id]?.answer
            if (local != null && server != null && FormV2SubmissionComparison.sameAnswer(local, server)) {
                return@mapNotNull null
            }
            val question = questions[id]
            val label = question?.questionText?.getTranslation(language)
                ?: serverAnswers[id]?.questionText ?: id
            val options = question?.mcOptions?.map { it.translations.getTranslation(language).orEmpty() }
                ?: serverAnswers[id]?.mcOptions.orEmpty()
            val type = question?.questionType ?: serverAnswers[id]?.questionType
            context.getString(
                R.string.form_v2_conflict_answer_pair, label,
                format(context, server, options, type, conflict.timeZoneId),
                format(context, local, options, type, conflict.timeZoneId)
            )
        }
        return context.getString(R.string.form_v2_conflict_message) + "\n\n" + differences.joinToString("\n\n")
    }

    private fun format(
        context: Context,
        answer: AnswerV2?,
        options: List<String>,
        type: QuestionTypeEnum?,
        timeZoneId: String
    ): String {
        if (answer == null) return context.getString(R.string.form_v2_conflict_unanswered)
        val value = when {
            answer.textAnswer != null -> answer.textAnswer
            answer.numericAnswer != null -> answer.numericAnswer.toString()
            answer.mcIdArrayAnswer != null -> answer.mcIdArrayAnswer.joinToString(", ") {
                options.getOrNull(it) ?: it.toString()
            }
            answer.dateAnswer != null -> {
                val seconds = answer.dateAnswer.toLongOrNull()
                if (seconds == null) answer.dateAnswer else {
                    val pattern = if (type == QuestionTypeEnum.DATE) "yyyy-MM-dd" else "yyyy-MM-dd HH:mm:ss XXX"
                    DateTimeFormatter.ofPattern(pattern).withZone(TimeZone.getTimeZone(timeZoneId).toZoneId())
                        .format(Instant.ofEpochSecond(seconds))
                }
            }
            else -> ""
        }
        val text = value.ifEmpty { context.getString(R.string.form_v2_conflict_unanswered) }
        return if (answer.comment.isNullOrEmpty()) text else
            context.getString(R.string.form_v2_conflict_answer_comment, text, answer.comment)
    }
}
