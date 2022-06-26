package com.cradleplatform.neptune.view

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import com.cradleplatform.neptune.R
import com.cradleplatform.neptune.databinding.ActivityFormSelectionBinding
import com.cradleplatform.neptune.viewmodel.FormSelectionViewModel
import com.google.android.material.textfield.TextInputLayout

class FormSelectionActivity : AppCompatActivity() {

    private var binding: ActivityFormSelectionBinding? = null

    private val viewModel: FormSelectionViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_form_selection)

        binding = DataBindingUtil.setContentView(this, R.layout.activity_form_selection)
        //binding.formSelectionAutoCompleteText.setAdapter()
        binding?.apply {
            viewModel = this@FormSelectionActivity.viewModel
            lifecycleOwner = this@FormSelectionActivity
            executePendingBindings()
        }

        check(intent.hasExtra(EXTRA_PATIENT_ID))
        setUpFetchFormButton()
    }

    private fun setUpFetchFormButton(){
        val fetchFormButton = findViewById<Button>(R.id.fetchFormButton)
        val formSelection = findViewById<TextInputLayout>(R.id.form_selection_text_input)

        fetchFormButton.setOnClickListener {

            val selectedFormName: String? = formSelection.editText?.text.toString()
            selectedFormName?.let {
                FormRenderingActivity.makeIntentWithFormTemplate(
                    this@FormSelectionActivity,
                    viewModel.getFormTemplateFromName(it)
                )
            }

        }
    }

    companion object {
        private const val EXTRA_PATIENT_ID = "PatientID that a new form will be created for"

        @JvmStatic
        fun makeIntentForPatientId(context: Context, patientId: String): Intent =
            Intent(context,FormSelectionActivity::class.java).apply {
                putExtra(EXTRA_PATIENT_ID, patientId)
            }
    }

}
