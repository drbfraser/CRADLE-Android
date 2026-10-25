package com.cradleplatform.neptune.activities.forms

import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.CheckBox
import android.widget.CompoundButton
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.cradleplatform.neptune.R
import com.cradleplatform.neptune.model.AnswerV2
import com.cradleplatform.neptune.model.FormTemplateQuestionV2
import com.cradleplatform.neptune.model.QuestionTypeEnum
import com.cradleplatform.neptune.model.getTranslation
import com.cradleplatform.neptune.utilities.DateUtil
import com.cradleplatform.neptune.viewmodel.forms.FormSubmissionDetailV2State
import com.cradleplatform.neptune.viewmodel.forms.FormSubmissionDetailV2ViewModel
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.threeten.bp.format.DateTimeFormatter

@AndroidEntryPoint
class FormSubmissionDetailV2Activity : AppCompatActivity() {
    private val viewModel: FormSubmissionDetailV2ViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_form_submission_detail_v2)
        supportActionBar?.title = getString(R.string.form_submission_detail_v2_title)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        findViewById<View>(R.id.retry_button).setOnClickListener { viewModel.loadForm() }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collectLatest { state -> updateUIFromState(state) }
            }
        }
    }

    private fun updateUIFromState(state: FormSubmissionDetailV2State) {
        val loading = findViewById<View>(R.id.loading_indicator)
        val status = findViewById<TextView>(R.id.status_text)
        val retry = findViewById<View>(R.id.retry_button)
        val scroll = findViewById<View>(R.id.form_detail_scroll)
        loading.visibility = View.GONE
        status.visibility = View.GONE
        retry.visibility = View.GONE
        scroll.visibility = View.GONE
        when (state) {
            FormSubmissionDetailV2State.Loading -> loading.visibility = View.VISIBLE
            FormSubmissionDetailV2State.Error -> {
                status.text = getString(R.string.form_submission_detail_v2_error)
                status.visibility = View.VISIBLE
                retry.visibility = View.VISIBLE
            }
            is FormSubmissionDetailV2State.Success -> {
                renderForm(state)
                scroll.visibility = View.VISIBLE
            }
        }
    }

    private fun renderForm(state: FormSubmissionDetailV2State.Success) {
        val form = state.submission
        val content = findViewById<LinearLayout>(R.id.form_detail_content)
        content.removeAllViews()
        content.addView(TextView(this).apply {
            text = state.template.classification.name.getTranslation(form.lang)
                ?.takeIf { it.isNotBlank() } ?: getString(R.string.form_v2_unnamed)
            textSize = 22f
            setTypeface(null, Typeface.BOLD)
        })
        // Join by question ID rather than position: optional answers may be absent.
        val answers = form.answers.orEmpty().associateBy { it.questionId }
        state.template.questions.orEmpty().sortedBy { it.order }.forEach { question ->
            addQuestion(content, question, answers[question.id]?.answer, form.lang)
        }
        if (state.template.questions.isNullOrEmpty()) {
            content.addView(TextView(this).apply { text = getString(R.string.form_template_detail_v2_empty) })
        }
    }

    private fun addQuestion(
        content: LinearLayout,
        question: FormTemplateQuestionV2,
        answer: AnswerV2?,
        language: String
    ) {
        val label = question.questionText.getTranslation(language).orEmpty()
        when (question.questionType) {
            QuestionTypeEnum.CATEGORY -> content.addView(TextView(this).apply {
                text = label
                textSize = 20f
                setTypeface(null, Typeface.BOLD)
                setPadding(0, 24, 0, 12)
            })
            QuestionTypeEnum.MULTIPLE_CHOICE, QuestionTypeEnum.MULTIPLE_SELECT -> {
                content.addView(TextView(this).apply {
                    text = label
                    setPadding(0, 16, 0, 4)
                })
                content.addView(makeChoiceInput(question, answer, language))
            }
            else -> {
                val value = when (question.questionType) {
                    QuestionTypeEnum.DATE, QuestionTypeEnum.DATETIME -> formatAnswerDate(answer, question.questionType)
                    QuestionTypeEnum.INTEGER, QuestionTypeEnum.DECIMAL -> answer?.numericAnswer?.toString().orEmpty()
                    QuestionTypeEnum.TIME -> answer?.dateAnswer.orEmpty()
                    else -> answer?.textAnswer.orEmpty()
                }
                content.addView(makeReadOnlyInput(label, value, question.units))
            }
        }
    }

    private fun makeReadOnlyInput(
        label: String,
        value: String,
        units: String? = null
    ): View = TextInputLayout(this).apply {
        hint = label
        suffixText = units?.takeIf { it.isNotBlank() }
        boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = resources.getDimensionPixelSize(R.dimen.reading_flow_previous_reading_item_margin) }
        addView(TextInputEditText(context).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            setText(value)
            isEnabled = false
        })
        isEnabled = false
    }

    private fun makeChoiceInput(
        question: FormTemplateQuestionV2,
        answer: AnswerV2?,
        language: String
    ): View {
        val singleChoice = question.questionType == QuestionTypeEnum.MULTIPLE_CHOICE
        val group = if (singleChoice) RadioGroup(this) else LinearLayout(this)
        group.orientation = LinearLayout.VERTICAL
        question.mcOptions.orEmpty().forEachIndexed { index, option ->
            val button: CompoundButton = if (singleChoice) RadioButton(this) else CheckBox(this)
            button.id = View.generateViewId()
            button.text = option.translations.getTranslation(language)
            button.isChecked = answer?.mcIdArrayAnswer?.contains(index) == true
            button.isEnabled = false
            group.addView(button)
        }
        return group
    }

    private fun formatAnswerDate(answer: AnswerV2?, type: QuestionTypeEnum): String {
        val raw = answer?.dateAnswer.orEmpty()
        val seconds = raw.toLongOrNull() ?: return raw
        val pattern = if (type == QuestionTypeEnum.DATE) "yyyy-MM-dd" else "yyyy-MM-dd HH:mm:ss"
        return DateUtil.getZoneTimeFromLong(seconds)?.format(DateTimeFormatter.ofPattern(pattern)) ?: raw
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    companion object {
        fun makeIntent(context: Context, submissionId: String, patientId: String): Intent =
            Intent(context, FormSubmissionDetailV2Activity::class.java).apply {
                putExtra(FormSubmissionDetailV2ViewModel.EXTRA_SUBMISSION_ID, submissionId)
                putExtra(FormSubmissionDetailV2ViewModel.EXTRA_PATIENT_ID, patientId)
            }
    }
}
