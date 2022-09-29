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
import com.cradleplatform.neptune.model.Patient
import com.cradleplatform.neptune.viewmodel.FormSelectionViewModel
import com.google.android.material.textfield.TextInputLayout
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class FormSelectionActivity : AppCompatActivity() {

    private var binding: ActivityFormSelectionBinding? = null

    private val viewModel: FormSelectionViewModel by viewModels()

    private var currentID: String? = null

    private var currentPatient: Patient? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_form_selection)

        binding = DataBindingUtil.setContentView(this, R.layout.activity_form_selection)
        binding?.apply {
            viewModel = this@FormSelectionActivity.viewModel
            lifecycleOwner = this@FormSelectionActivity
            executePendingBindings()
        }

        currentID = intent.getStringExtra(EXTRA_PATIENT_ID)!!
        currentPatient = intent.getSerializableExtra(FORM_SELECTION_EXTRA_PATIENT) as Patient?

        setUpFetchFormButton()
        setUpFormVersionOnChange()
        setUpActionBar()
    }

    private fun setUpFormVersionOnChange() {
        val formSelectionInput = findViewById<TextInputLayout>(R.id.form_selection_text_input)
        val formLanguageInput = findViewById<TextInputLayout>(R.id.form_language_text_input)

        //Crashing on Typing -> Commented
        formSelectionInput.editText!!.doOnTextChanged { text, _, _, _ ->
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
                val formTemplate = viewModel.getFormTemplateFromName(formTemplateName)

                val intent = FormRenderingActivity.makeIntentWithFormTemplate(
                    this@FormSelectionActivity,
                    formTemplate,
                    formLanguage,
                    intent.getStringExtra(EXTRA_PATIENT_ID)!!,
                    intent.getSerializableExtra(FORM_SELECTION_EXTRA_PATIENT) as Patient
                )

                startActivity(intent)
            }
        }
    }

    private fun setUpActionBar() {
        supportActionBar?.title = getString(R.string.create_new_form)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }

    companion object {
        private const val EXTRA_PATIENT_ID = "PatientID that a new form will be created for"
        private const val FORM_SELECTION_EXTRA_PATIENT = "Patient"

        @JvmStatic
        fun makeIntentForPatientId(context: Context, patientId: String, patient: Patient): Intent =
            Intent(context, FormSelectionActivity::class.java).apply {
                putExtra(EXTRA_PATIENT_ID, patientId)
                putExtra(FORM_SELECTION_EXTRA_PATIENT, patient)
            }
    }

    override fun onBackPressed() {
        //super.onBackPressed()

        val intent = currentID?.let {
            PatientProfileActivity.makeIntentForPatientId(
                this@FormSelectionActivity,
                it
            )
        }

        if (intent != null) {
            startActivity(intent)
        }
    }
}
