package com.cradleplatform.neptune.view

import android.content.Context
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.cradleplatform.neptune.R
import com.cradleplatform.neptune.model.FormTemplate
import com.cradleplatform.neptune.model.Patient
import dagger.hilt.android.AndroidEntryPoint

@Suppress("LargeClass")
@AndroidEntryPoint
class FormRenderingActivityUplift : AppCompatActivity() {
    private lateinit var bottomSheetCurrentSection: TextView
    private lateinit var bottomSheetCategoryContainer: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_form_rendering_uplift)
        setUpBottomSheet()
    }

    private fun setUpBottomSheet() {
        bottomSheetCurrentSection = findViewById(R.id.bottomSheetCurrentSection)
        bottomSheetCategoryContainer = findViewById(R.id.form_category_container)
        bottomSheetCurrentSection.text = "Section 1/4" //TODO add attachment to Viewmodel here

        //add categories TODO Here we will simply populate and put up each category
        val category = getCategoryRow()
        bottomSheetCategoryContainer.addView(category)

        //Keep a list of the views while inflating so we know how to reference
    }

    private fun getCategoryRow(): View {
        val category = this.layoutInflater.inflate(R.layout.category_row_item, null)
        val requiredTextView: TextView = category.findViewById(R.id.category_row_required_tv)
        val optionalTextView: TextView = category.findViewById(R.id.category_row_optional_tv)
        val button: Button = category.findViewById(R.id.category_row_btn)

        button.text = "Referred By"
        requiredTextView.text = "Required 2/4"
        optionalTextView.text = "Optional 1/12"
        return category
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
