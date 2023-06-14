package com.cradleplatform.neptune.view

import android.content.Context
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.viewModels
import com.cradleplatform.neptune.R
import com.cradleplatform.neptune.model.FormTemplate
import com.cradleplatform.neptune.model.Patient
import com.cradleplatform.neptune.model.Question
import com.cradleplatform.neptune.viewmodel.FormRenderingViewModel
import com.google.android.material.bottomsheet.BottomSheetBehavior
import dagger.hilt.android.AndroidEntryPoint

@Suppress("LargeClass")
@AndroidEntryPoint
class FormRenderingActivityUplift : AppCompatActivity() {
    private lateinit var bottomSheetCurrentSection: TextView
    private lateinit var bottomSheetCategoryContainer: LinearLayout
    private lateinit var bottomSheetBehaviour: BottomSheetBehavior<View>
    val viewModel: FormRenderingViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_form_rendering_uplift)

        //Getting the formTemplate from the intent
        val formTemplateFromIntent =
            intent.getSerializableExtra(EXTRA_FORM_TEMPLATE) as FormTemplate

        viewModel.currentFormTemplate = formTemplateFromIntent
        viewModel.populateEmptyIds(applicationContext)

        //Clear previous answers in view-model
        viewModel.clearAnswers()
        viewModel.changeCategory(1)

        setUpBottomSheet(intent.getStringExtra(EXTRA_LANGUAGE_SELECTED))

        //observe changes to current category
        viewModel.currentCategory().observe(this) {
            categoryChanged(it)
        }
    }

    private fun categoryChanged(currCategory: Int) {
        bottomSheetCurrentSection.text = String.format(
            getString(R.string.form_current_section),
            currCategory, viewModel.categoryList?.size ?: 1
        )

        bottomSheetBehaviour.state = BottomSheetBehavior.STATE_COLLAPSED

        //TODO will add logic for changing questions here.
    }

    private fun setUpBottomSheet(languageSelected: String?) {
        bottomSheetCategoryContainer = findViewById(R.id.form_category_container)
        bottomSheetCurrentSection = findViewById(R.id.bottomSheetCurrentSection)
        bottomSheetBehaviour = BottomSheetBehavior.from(findViewById(R.id.form_bottom_sheet))

        val bottomSheetCallback = object : BottomSheetBehavior.BottomSheetCallback() {

            override fun onStateChanged(bottomSheet: View, newState: Int) {
                when (newState) {
                    BottomSheetBehavior.STATE_EXPANDED -> println("Inder expanded") //TO DO switch icons here
                    BottomSheetBehavior.STATE_COLLAPSED -> println("Inder collapsed")
                }
            }

            override fun onSlide(bottomSheet: View, slideOffset: Float) {
                // Do nothing
            }
        }
        bottomSheetBehaviour.addBottomSheetCallback(bottomSheetCallback)

        val categoryViewList: MutableList<View> = mutableListOf()
        viewModel.setCategorizedQuestions(languageSelected ?: "English")
        viewModel.categoryList?.forEachIndexed { index, pair ->
            val category = getCategoryRow(pair, index + 1)
            bottomSheetCategoryContainer.addView(category)
            categoryViewList.add(category)
        }
    }

    private fun getCategoryRow(categoryPair: Pair<String, List<Question>?>, categoryNumber: Int): View {
        val category = this.layoutInflater.inflate(R.layout.category_row_item, null)
        val requiredTextView: TextView = category.findViewById(R.id.category_row_required_tv)
        val optionalTextView: TextView = category.findViewById(R.id.category_row_optional_tv)
        val button: Button = category.findViewById(R.id.category_row_btn)

        button.text = categoryPair.first
        button.setOnClickListener {
            viewModel.changeCategory(categoryNumber)
        }
        requiredTextView.text = viewModel.getRequiredFieldsText(categoryPair.second)
        optionalTextView.text = viewModel.getOptionalFieldsText(categoryPair.second)
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
