package com.cradleplatform.neptune.view

import android.content.Context
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import com.cradleplatform.neptune.R
import com.cradleplatform.neptune.model.FormTemplate
import com.cradleplatform.neptune.model.Patient
import dagger.hilt.android.AndroidEntryPoint

@Suppress("LargeClass")
@AndroidEntryPoint
class FormRenderingActivityUplift : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_form_rendering_uplift)
    }

    companion object {
        private const val EXTRA_FORM_TEMPLATE = "JSON string for form template"
        private const val EXTRA_PATIENT_ID = "Patient id that the form is created for"
        private const val EXTRA_LANGUAGE_SELECTED = "String of language selected for a FormTemplate"
        private const val EXTRA_PATIENT_OBJECT = "The Patient object used to start patient profile"

        @JvmStatic
        fun makeIntentWithFormTemplate(
            context: Context,
            formTemplate: FormTemplate,
            formLanguage: String,
            patientId: String,
            patient: Patient
        ): Intent {
            val bundle = Bundle()
            bundle.putSerializable(EXTRA_FORM_TEMPLATE, formTemplate)
            bundle.putSerializable(EXTRA_PATIENT_OBJECT, patient)

            return Intent(context, FormRenderingActivityUplift::class.java).apply {
                this.putExtra(EXTRA_PATIENT_ID, patientId)
                this.putExtra(EXTRA_LANGUAGE_SELECTED, formLanguage)
                this.putExtras(bundle)
            }
        }
    }
}