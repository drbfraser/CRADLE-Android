package com.cradleplatform.neptune.view

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doOnTextChanged
import androidx.databinding.DataBindingUtil
import com.cradleplatform.neptune.R
import com.cradleplatform.neptune.databinding.ActivityFormSelectionBinding
import com.cradleplatform.neptune.viewmodel.FormSelectionViewModel
import com.google.android.material.textfield.TextInputLayout
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class FormSelectionActivity : AppCompatActivity() {

    private var binding: ActivityFormSelectionBinding? = null

    private val viewModel: FormSelectionViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_form_selection)

        binding = DataBindingUtil.setContentView(this, R.layout.activity_form_selection)
        binding?.apply {
            viewModel = this@FormSelectionActivity.viewModel
            lifecycleOwner = this@FormSelectionActivity
            executePendingBindings()
        }

        setUpFetchFormButton()
        setUpFormVersionOnChange()
        setUpActionBar()
    }

    private fun setUpFormVersionOnChange() {
        val formSelection = findViewById<TextInputLayout>(R.id.form_selection_text_input)
        val formLanguageInput = findViewById<TextInputLayout>(R.id.form_language_text_input)
        formSelection.editText!!.doOnTextChanged { text, _, _, _ ->
            viewModel.formTemplateChanged(text.toString())
            formLanguageInput.editText!!.text.clear()
        }
    }

    private fun setUpFetchFormButton() {
        val fetchFormButton = findViewById<Button>(R.id.fetchFormButton)
        val formSelectionInput = findViewById<TextInputLayout>(R.id.form_selection_text_input)
        val formLanguageInput = findViewById<TextInputLayout>(R.id.form_language_text_input)

        check(intent.hasExtra(EXTRA_PATIENT_ID))

        fetchFormButton.setOnClickListener {

            // editTexts should not be null in normal circumstances
            val formTemplateName = formSelectionInput.editText!!.text.toString()
            val formLanguage = formLanguageInput.editText!!.text.toString()

            if (formTemplateName.isEmpty()) {
                Toast.makeText(
                    this@FormSelectionActivity,
                    getString(R.string.warn_no_form_template_selected),
                    Toast.LENGTH_SHORT
                ).show()
            } else if (formLanguage.isEmpty()) {
                Toast.makeText(
                    this@FormSelectionActivity,
                    getString(R.string.war_no_form_language_selected),
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                val formTemplate = viewModel.getFormTemplateFromNameAndVersion(formTemplateName, formLanguage)

                val intent = FormRenderingActivity.makeIntentWithFormTemplate(
                    this@FormSelectionActivity,
                    formTemplate,
                    intent.getStringExtra(EXTRA_PATIENT_ID)!!
                )
                startActivity(intent)
            }
        }
    }

    private fun setUpActionBar() {
        supportActionBar?.title = getString(R.string.form_selection_activity_title)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }

    companion object {
        private const val EXTRA_PATIENT_ID = "PatientID that a new form will be created for"

        @JvmStatic
        fun makeIntentForPatientId(context: Context, patientId: String): Intent =
            Intent(context, FormSelectionActivity::class.java).apply {
                putExtra(EXTRA_PATIENT_ID, patientId)
            }
    }
}
