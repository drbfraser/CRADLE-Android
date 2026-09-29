package com.cradleplatform.neptune.activities.forms

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.cradleplatform.neptune.R
import com.cradleplatform.neptune.model.AnswerV2
import com.cradleplatform.neptune.model.FormTemplateQuestionV2
import com.cradleplatform.neptune.model.QuestionTypeEnum
import com.cradleplatform.neptune.viewmodel.forms.FormTemplateDetailV2ViewModel
import com.cradleplatform.neptune.viewmodel.forms.FormV2RenderingState
import com.cradleplatform.neptune.viewmodel.forms.FormV2RenderingViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * First V2 filling screen. Collects basic scalar answers in memory; submission is a later step.
 */
@AndroidEntryPoint
class FormV2RenderingActivity : AppCompatActivity() {

    private val viewModel: FormV2RenderingViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_form_v2_rendering)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collectLatest { state -> render(state) }
            }
        }
    }

    private fun render(state: FormV2RenderingState) {
        val content = findViewById<LinearLayout>(R.id.form_v2_content)
        val status = findViewById<TextView>(R.id.form_v2_status)
        content.removeAllViews()

        when (state) {
            FormV2RenderingState.Loading -> status.text = getString(R.string.form_v2_rendering_loading)
            is FormV2RenderingState.Error -> status.text = state.message
            is FormV2RenderingState.Success -> {
                status.text = ""
                state.template.questions.orEmpty().sortedBy { it.order }.forEach { question ->
                    val label = TextView(this).apply {
                        text = question.questionText["english"]
                            ?: question.questionText.values.firstOrNull().orEmpty()
                        setPadding(0, 16, 0, 4)
                    }
                    content.addView(label)

                    if (question.questionType == QuestionTypeEnum.CATEGORY) {
                        label.textSize = 20f
                        label.setTextColor(getColor(R.color.colorPrimaryDark))
                        return@forEach
                    }

                    if (question.questionType == QuestionTypeEnum.MULTIPLE_CHOICE ||
                        question.questionType == QuestionTypeEnum.MULTIPLE_SELECT
                    ) {
                        content.addView(makeChoiceInput(question))
                    } else if (question.questionType == QuestionTypeEnum.TIME) {
                        content.addView(makeTimeInput(question))
                    } else if (question.questionType == QuestionTypeEnum.DATE ||
                        question.questionType == QuestionTypeEnum.DATETIME
                    ) {
                        content.addView(makeDateInput(question))
                    } else if (question.questionType == QuestionTypeEnum.STRING ||
                        question.questionType == QuestionTypeEnum.INTEGER ||
                        question.questionType == QuestionTypeEnum.DECIMAL
                    ) {
                        content.addView(EditText(this).apply {
                            hint = getString(R.string.form_v2_rendering_string_hint)
                            isSingleLine = question.questionType != QuestionTypeEnum.STRING ||
                                (question.stringMaxLines != null && question.stringMaxLines <= 1)
                            inputType = when (question.questionType) {
                                QuestionTypeEnum.INTEGER -> InputType.TYPE_CLASS_NUMBER or
                                    InputType.TYPE_NUMBER_FLAG_SIGNED
                                QuestionTypeEnum.DECIMAL -> InputType.TYPE_CLASS_NUMBER or
                                    InputType.TYPE_NUMBER_FLAG_DECIMAL or
                                    InputType.TYPE_NUMBER_FLAG_SIGNED
                                else -> InputType.TYPE_CLASS_TEXT
                            }
                            viewModel.answerState.getAnswer(question.id)?.let { answer ->
                                setText(
                                    if (question.questionType == QuestionTypeEnum.STRING) {
                                        answer.textAnswer.orEmpty()
                                    } else if (question.questionType == QuestionTypeEnum.INTEGER) {
                                        answer.numericAnswer?.toLong()?.toString().orEmpty()
                                    } else {
                                        answer.numericAnswer?.toString().orEmpty()
                                    }
                                )
                            }
                            addTextChangedListener { text ->
                                val value = text?.toString().orEmpty()
                                if (value.isEmpty()) {
                                    viewModel.answerState.removeAnswer(question.id)
                                } else if (question.questionType == QuestionTypeEnum.STRING) {
                                    viewModel.answerState.setAnswer(
                                        question.id,
                                        AnswerV2.createTextAnswer(value)
                                    )
                                } else if (question.questionType == QuestionTypeEnum.INTEGER) {
                                    val number = value.toLongOrNull()
                                    if (number == null) {
                                        viewModel.answerState.removeAnswer(question.id)
                                    } else {
                                        viewModel.answerState.setAnswer(
                                            question.id,
                                            AnswerV2.createNumericAnswer(number)
                                        )
                                    }
                                } else {
                                    val number = value.toDoubleOrNull()
                                    if (number == null) {
                                        viewModel.answerState.removeAnswer(question.id)
                                    } else {
                                        viewModel.answerState.setAnswer(
                                            question.id,
                                            AnswerV2.createNumericAnswer(number)
                                        )
                                    }
                                }
                            }
                        })
                    } else {
                        content.addView(TextView(this).apply {
                            text = getString(R.string.form_v2_rendering_unsupported, question.questionType.name)
                            alpha = 0.65f
                        })
                    }
                }
            }
        }
    }

    private fun makeChoiceInput(question: FormTemplateQuestionV2): View {
        val options = question.mcOptions.orEmpty()
        if (question.questionType == QuestionTypeEnum.MULTIPLE_CHOICE) {
            return RadioGroup(this).apply {
                options.forEach { option ->
                    addView(RadioButton(context).apply {
                        id = View.generateViewId()
                        text = option.translations["english"]
                            ?: option.translations.values.firstOrNull().orEmpty()
                    })
                }
                viewModel.answerState.getAnswer(question.id)?.mcIdArrayAnswer
                    ?.firstOrNull()
                    ?.takeIf { it in 0 until childCount }
                    ?.let { check(getChildAt(it).id) }
                setOnCheckedChangeListener { group, checkedId ->
                    val selectedIndex = (0 until group.childCount)
                        .firstOrNull { group.getChildAt(it).id == checkedId }
                    if (selectedIndex == null) {
                        viewModel.answerState.removeAnswer(question.id)
                    } else {
                        viewModel.answerState.setAnswer(
                            question.id,
                            AnswerV2.createMcAnswer(listOf(selectedIndex))
                        )
                    }
                }
            }
        }

        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val selectedIndices = viewModel.answerState.getAnswer(question.id)
                ?.mcIdArrayAnswer
                ?.toMutableSet()
                ?: mutableSetOf()
            options.forEachIndexed { index, option ->
                addView(CheckBox(context).apply {
                    text = option.translations["english"]
                        ?: option.translations.values.firstOrNull().orEmpty()
                    isChecked = index in selectedIndices
                    setOnCheckedChangeListener { _, isChecked ->
                        if (isChecked) selectedIndices.add(index) else selectedIndices.remove(index)
                        if (selectedIndices.isEmpty()) {
                            viewModel.answerState.removeAnswer(question.id)
                        } else {
                            viewModel.answerState.setAnswer(
                                question.id,
                                AnswerV2.createMcAnswer(selectedIndices.sorted())
                            )
                        }
                    }
                })
            }
        }
    }

    private fun makeDateInput(question: FormTemplateQuestionV2): Button {
        val button = Button(this).apply {
            text = viewModel.answerState.getAnswer(question.id)?.dateAnswer
                ?: getString(R.string.form_v2_rendering_date_hint)
        }
        button.setOnClickListener {
            val now = Calendar.getInstance()
            val datePicker = DatePickerDialog(
                this,
                { _, year, month, day ->
                    val date = "%04d-%02d-%02d".format(year, month + 1, day)
                    if (question.questionType == QuestionTypeEnum.DATE) {
                        button.text = date
                        viewModel.answerState.setAnswer(question.id, AnswerV2.createDateAnswer(date))
                    } else {
                        TimePickerDialog(
                            this,
                            { _, hour, minute ->
                                if (isDateTimeAllowed(question, year, month, day, hour, minute)) {
                                    val value = "%s %02d:%02d".format(date, hour, minute)
                                    button.text = value
                                    button.error = null
                                    viewModel.answerState.setAnswer(
                                        question.id,
                                        AnswerV2.createDateAnswer(value)
                                    )
                                } else {
                                    button.error = getString(R.string.form_v2_rendering_datetime_not_allowed)
                                    viewModel.answerState.removeAnswer(question.id)
                                }
                            },
                            now.get(Calendar.HOUR_OF_DAY),
                            now.get(Calendar.MINUTE),
                            true,
                        ).show()
                    }
                },
                now.get(Calendar.YEAR),
                now.get(Calendar.MONTH),
                now.get(Calendar.DAY_OF_MONTH),
            )
            if (question.allowPastDates == false) {
                datePicker.datePicker.minDate = System.currentTimeMillis()
            }
            if (question.allowFutureDates == false) {
                datePicker.datePicker.maxDate = System.currentTimeMillis()
            }
            datePicker.show()
        }
        return button
    }

    private fun isDateTimeAllowed(
        question: FormTemplateQuestionV2,
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int,
    ): Boolean {
        val selected = Calendar.getInstance().apply {
            set(year, month, day, hour, minute, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val now = Calendar.getInstance()

        return when {
            question.allowPastDates == false && selected.before(now) -> false
            question.allowFutureDates == false && selected.after(now) -> false
            else -> true
        }
    }

    private fun makeTimeInput(question: FormTemplateQuestionV2): Button = Button(this).apply {
        text = viewModel.answerState.getAnswer(question.id)?.dateAnswer
            ?: getString(R.string.form_v2_rendering_time_hint)
        setOnClickListener {
            val now = Calendar.getInstance()
            TimePickerDialog(
                this@FormV2RenderingActivity,
                { _, hour, minute ->
                    val value = "%02d:%02d".format(hour, minute)
                    text = value
                    viewModel.answerState.setAnswer(question.id, AnswerV2.createDateAnswer(value))
                },
                now.get(Calendar.HOUR_OF_DAY),
                now.get(Calendar.MINUTE),
                true,
            ).show()
        }
    }

    @Suppress("DEPRECATION")
    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }

    companion object {
        fun makeIntent(context: Context, templateId: String, patientId: String? = null): Intent =
            Intent(context, FormV2RenderingActivity::class.java).apply {
                putExtra(FormTemplateDetailV2ViewModel.EXTRA_TEMPLATE_ID, templateId)
                putExtra(FormTemplateListV2Activity.EXTRA_PATIENT_ID, patientId)
            }
    }
}
