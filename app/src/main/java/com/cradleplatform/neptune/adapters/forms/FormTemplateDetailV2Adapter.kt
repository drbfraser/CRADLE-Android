package com.cradleplatform.neptune.adapters.forms

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.cradleplatform.neptune.R
import com.cradleplatform.neptune.model.FormTemplateQuestionV2
import com.cradleplatform.neptune.model.QuestionTypeEnum

/**
 * Adapter for the RecyclerView in FormTemplateDetailV2Activity. Displays a template's
 * questions read-only: text, type, required/optional, and MCQ options if present.
 */
class FormTemplateDetailV2Adapter(
    private val questions: List<FormTemplateQuestionV2>
) : RecyclerView.Adapter<FormTemplateDetailV2Adapter.ViewHolder>() {

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val questionText: TextView = itemView.findViewById(R.id.question_text)
        val subtitleText: TextView = itemView.findViewById(R.id.question_subtitle_text)
        val optionsText: TextView = itemView.findViewById(R.id.question_options_text)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.list_item_form_question_v2, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val question = questions[position]
        val context = holder.itemView.context

        holder.questionText.text = question.questionText["english"] ?: question.questionText.values.firstOrNull()

        val requiredText = if (question.required) {
            context.getString(R.string.form_question_v2_required)
        } else {
            context.getString(R.string.form_question_v2_optional)
        }
        holder.subtitleText.text = "${question.questionType.displayName()} · $requiredText"

        val optionNames = question.mcOptions?.map { it.translations["english"] ?: it.translations.values.first() }
        if (optionNames.isNullOrEmpty()) {
            holder.optionsText.visibility = View.GONE
        } else {
            holder.optionsText.visibility = View.VISIBLE
            holder.optionsText.text = context.getString(
                R.string.form_question_v2_options,
                optionNames.joinToString(", ")
            )
        }
    }

    override fun getItemCount(): Int = questions.size

    private fun QuestionTypeEnum.displayName(): String = name.lowercase()
        .split("_")
        .joinToString(" ") { it.replaceFirstChar(Char::uppercase) }
}
