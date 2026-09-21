package com.cradleplatform.neptune.activities.forms

import android.content.Context
import android.content.Intent
import android.os.Bundle
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
import com.cradleplatform.neptune.model.QuestionTypeEnum
import com.cradleplatform.neptune.viewmodel.forms.FormTemplateDetailV2State
import com.cradleplatform.neptune.viewmodel.forms.FormTemplateDetailV2ViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

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

                    if (question.questionType == QuestionTypeEnum.STRING) {
                        content.addView(EditText(this).apply {
                            hint = getString(R.string.form_v2_rendering_string_hint)
                            isSingleLine = question.stringMaxLines != null && question.stringMaxLines <= 1
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
