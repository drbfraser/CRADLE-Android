package com.cradleplatform.neptune.activities.forms

import android.content.Context
import android.content.Intent
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.text.InputType
import android.widget.Button
import android.widget.CheckBox
import android.widget.RadioButton
import android.widget.RadioGroup
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.cradleplatform.neptune.R
import com.cradleplatform.neptune.model.FormTemplateQuestionV2
import com.cradleplatform.neptune.model.QuestionTypeEnum
import com.cradleplatform.neptune.viewmodel.forms.FormTemplateDetailV2State
import com.cradleplatform.neptune.viewmodel.forms.FormTemplateDetailV2ViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * First V2 filling screen. Supports only STRING questions for now.
 */
@AndroidEntryPoint
class FormV2RenderingActivity : AppCompatActivity() {

    private val viewModel: FormTemplateDetailV2ViewModel by viewModels()

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

    private fun render(state: FormTemplateDetailV2State) {
        val content = findViewById<LinearLayout>(R.id.form_v2_content)
        val status = findViewById<TextView>(R.id.form_v2_status)
        content.removeAllViews()

        when (state) {
            FormTemplateDetailV2State.Loading -> status.text = getString(R.string.form_v2_rendering_loading)
            is FormTemplateDetailV2State.Error -> status.text = state.message
            is FormTemplateDetailV2State.Success -> {
                status.text = ""
                state.template.questions.orEmpty().sortedBy { it.order }.forEach { question ->
                    val label = TextView(this).apply {
                        text = question.questionText["english"] ?: question.questionText.values.firstOrNull().orEmpty()
                        setPadding(0, 16, 0, 4)
                    }
                    content.addView(label)

                    if (question.questionType == QuestionTypeEnum.MULTIPLE_CHOICE ||
                        question.questionType == QuestionTypeEnum.MULTIPLE_SELECT
                    ) {
                        content.addView(makeChoiceInput(question))
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
                                    InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED
                                else -> InputType.TYPE_CLASS_TEXT
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
                        text = option.translations["english"] ?: option.translations.values.firstOrNull().orEmpty()
                    })
                }
            }
        }

        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            options.forEach { option ->
                addView(CheckBox(context).apply {
                    text = option.translations["english"] ?: option.translations.values.firstOrNull().orEmpty()
                })
            }
        }
    }

    private fun makeDateInput(question: FormTemplateQuestionV2): Button {
        val button = Button(this).apply {
            text = getString(R.string.form_v2_rendering_date_hint)
        }
        button.setOnClickListener {
            val now = Calendar.getInstance()
            val datePicker = DatePickerDialog(
                this,
                { _, year, month, day ->
                    val date = "%04d-%02d-%02d".format(year, month + 1, day)
                    if (question.questionType == QuestionTypeEnum.DATE) {
                        button.text = date
                    } else {
                        TimePickerDialog(
                            this,
                            { _, hour, minute -> button.text = "%s %02d:%02d".format(date, hour, minute) },
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
            if (question.allowPastDates == false) datePicker.datePicker.minDate = System.currentTimeMillis()
            if (question.allowFutureDates == false) datePicker.datePicker.maxDate = System.currentTimeMillis()
            datePicker.show()
        }
        return button
    }

    @Suppress("DEPRECATION")
    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }

    companion object {
        fun makeIntent(context: Context, templateId: String): Intent =
            Intent(context, FormV2RenderingActivity::class.java).apply {
                putExtra(FormTemplateDetailV2ViewModel.EXTRA_TEMPLATE_ID, templateId)
            }
    }
}
