package com.cradleplatform.neptune.view

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.cradleplatform.neptune.R
import com.cradleplatform.neptune.model.FormTemplate
import com.cradleplatform.neptune.model.Patient
import com.cradleplatform.neptune.model.Question
import com.cradleplatform.neptune.view.adapters.FormViewAdapterUplift
import com.cradleplatform.neptune.viewmodel.FormRenderingViewModel
import com.google.android.material.bottomsheet.BottomSheetBehavior
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Suppress("LargeClass")
@AndroidEntryPoint
class FormRenderingActivityUplift : AppCompatActivity() {
    private var layoutManager: RecyclerView.LayoutManager? = null
    private var adapter: RecyclerView.Adapter<FormViewAdapterUplift.ViewHolder>? = null
    private var patient: Patient? = null
    private var patientId: String? = null
    private var languageSelected: String? = null
    private var categoryViewList: MutableList<View> = mutableListOf()
    private lateinit var recyclerView: RecyclerView
    private lateinit var bottomSheetCurrentSection: TextView
    private lateinit var bottomSheetCategoryContainer: LinearLayout
    private lateinit var bottomSheetBehaviour: BottomSheetBehavior<View>
    private lateinit var formStateBtn: ImageButton
    private lateinit var formNextBtn: ImageButton
    private lateinit var formPrevBtn: ImageButton
    val viewModel: FormRenderingViewModel by viewModels()

    override fun onSupportNavigateUp(): Boolean {

        val builder = AlertDialog.Builder(this)
        builder.setTitle(R.string.are_you_sure)
        builder.setMessage(R.string.discard_form_dialog)

        builder.setPositiveButton(R.string.yes) { _, _ ->
            val intent = FormSelectionActivity.makeIntentForPatientId(
                this@FormRenderingActivityUplift,
                patientId!!,
                patient!!
            )
            startActivity(intent)
        }

        builder.setNegativeButton(R.string.no) { _, _ ->
            //Do nothing...
        }
        builder.show()
        return true
    }

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

        //observe changes to current answers
        viewModel.currentAnswers().observe(this) {
            updateQuestionsTotalText()
        }

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        //Getting the patient from the intent (ID and Patient Object)
        patientId = intent.getStringExtra(EXTRA_PATIENT_ID)!!
        patient = intent.getSerializableExtra(EXTRA_PATIENT_OBJECT)!! as Patient

        //Getting the language selected from the intent
        languageSelected = intent.getStringExtra(EXTRA_LANGUAGE_SELECTED)

        //Form a question list from Category to next category
        //Pass the list to recyclerView

        layoutManager = LinearLayoutManager(this)

        recyclerView = findViewById<RecyclerView>(R.id.form_recycler_view)
        recyclerView.layoutManager = layoutManager

        recyclerView.recycledViewPool.setMaxRecycledViews(0, 0)

        //check if language selected exists in the form template
        //The language's available are already shown in the dropdown

        adapter = FormViewAdapterUplift(viewModel, languageSelected!!, patient)

        recyclerView.adapter = adapter
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_forms, menu)
        return true
    }

    fun onSubmitFormAction(menuItem: MenuItem) {
        //A toast is displayed to user if require field is not filled
        if (viewModel.isRequiredFieldsFilled(languageSelected!!, applicationContext)) {
            showFormSubmissionModeDialog(languageSelected!!)
        }
    }

    private fun showFormSubmissionModeDialog(languageSelected: String) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle(R.string.how_to_submit)
        builder.setMessage(R.string.choose_an_option)

        builder.setPositiveButton(R.string.http) { _, _ ->
            formSubmission(languageSelected, "HTTP")
            finish()
        }

        builder.setNegativeButton(R.string.SMS) { _, _ ->
            formSubmission(languageSelected, "SMS")
            finish()
        }
        builder.show()
    }

    private fun formSubmission(languageSelected: String, submissionMode: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            viewModel.submitForm(patientId!!, languageSelected, submissionMode, applicationContext)
        }
    }

    private fun categoryChanged(currCategory: Int) {
        this.title = viewModel.categoryList?.getOrNull(currCategory - 1)?.first ?: "Cradle"

        bottomSheetCurrentSection.text = String.format(
            getString(R.string.form_current_section),
            currCategory, viewModel.categoryList?.size ?: 1
        )

        bottomSheetBehaviour.state = BottomSheetBehavior.STATE_COLLAPSED
        formStateBtn.background = getDrawable(R.drawable.ic_baseline_arrow_up_24)

        adapter = FormViewAdapterUplift(viewModel, languageSelected!!, patient)
        recyclerView.adapter = adapter

        formNextBtn.background = viewModel.isNextButtonVisible(applicationContext)
        formPrevBtn.background = viewModel.isPrevButtonVisible(applicationContext)

        categoryViewList.forEach { categoryView ->
            categoryView.findViewById<Button>(R.id.category_row_btn)?.let { button ->
                button.background = getDrawable(R.drawable.rounded_button_grey)
            }
        }
        val button: Button? = categoryViewList.getOrNull(currCategory - 1)?.findViewById(R.id.category_row_btn)
        button?.background = getDrawable(R.drawable.rounded_button_green)
    }

    private fun updateQuestionsTotalText() {
        viewModel.categoryList?.forEachIndexed { index, pair ->
            val categoryView = categoryViewList.getOrNull(index)
            if (categoryView != null) {
                val requiredTextView: TextView = categoryView.findViewById(R.id.category_row_required_tv)
                val requiredIcon: ImageView = categoryView.findViewById(R.id.category_row_indicator_icon)
                val optionalTextView: TextView = categoryView.findViewById(R.id.category_row_optional_tv)
                val requiredTextIconPair = viewModel.getRequiredFieldsTextAndIcon(pair.second, applicationContext)

                requiredTextView.text = requiredTextIconPair.first
                optionalTextView.text = viewModel.getOptionalFieldsText(pair.second)

                requiredTextIconPair.second?.let {
                    requiredIcon.setImageDrawable(it)
                }
            }
        }
    }

    private fun setUpBottomSheet(languageSelected: String?) {
        bottomSheetCategoryContainer = findViewById(R.id.form_category_container)
        bottomSheetCurrentSection = findViewById(R.id.bottomSheetCurrentSection)
        bottomSheetBehaviour = BottomSheetBehavior.from(findViewById(R.id.form_bottom_sheet))
        formStateBtn = findViewById(R.id.form_state_button)

        bottomSheetBehaviour.isDraggable = false
        formStateBtn.setOnClickListener {
            when (bottomSheetBehaviour.state) {
                BottomSheetBehavior.STATE_EXPANDED -> {
                    bottomSheetBehaviour.state = BottomSheetBehavior.STATE_COLLAPSED
                    formStateBtn.background = getDrawable(R.drawable.ic_baseline_arrow_up_24)
                }
                else -> {
                    bottomSheetBehaviour.state = BottomSheetBehavior.STATE_EXPANDED
                    formStateBtn.background = getDrawable(R.drawable.ic_baseline_arrow_down_24)
                }
            }
        }

        viewModel.setCategorizedQuestions(languageSelected ?: "English")
        viewModel.categoryList?.forEachIndexed { index, pair ->
            val category = getCategoryRow(pair, index + 1)
            bottomSheetCategoryContainer.addView(category)
            categoryViewList.add(category)
        }

        formNextBtn = findViewById(R.id.form_next_category_button)
        formPrevBtn = findViewById(R.id.form_prev_category_button)

        formNextBtn.background = viewModel.isNextButtonVisible(applicationContext)
        formPrevBtn.background = viewModel.isPrevButtonVisible(applicationContext)

        formNextBtn.setOnClickListener {
            viewModel.goNextCategory()
        }
        formPrevBtn.setOnClickListener {
            viewModel.goPrevCategory()
        }
    }

    private fun getCategoryRow(categoryPair: Pair<String, List<Question>?>, categoryNumber: Int): View {
        val category = this.layoutInflater.inflate(R.layout.category_row_item, null)
        val requiredTextView: TextView = category.findViewById(R.id.category_row_required_tv)
        val optionalTextView: TextView = category.findViewById(R.id.category_row_optional_tv)
        val button: Button = category.findViewById(R.id.category_row_btn)
        val requiredIcon: ImageView = category.findViewById(R.id.category_row_indicator_icon)

        val requiredTextIconPair = viewModel.getRequiredFieldsTextAndIcon(categoryPair.second, applicationContext)

        button.text = categoryPair.first
        if (categoryNumber == FIRST_CATEGORY_POSITION) {
            // set the first button as selected
            button.background = getDrawable(R.drawable.rounded_button_green)
        }
        button.setOnClickListener {
            viewModel.changeCategory(categoryNumber)
        }
        requiredTextView.text = requiredTextIconPair.first
        optionalTextView.text = viewModel.getOptionalFieldsText(categoryPair.second)

        requiredTextIconPair.second?.let {
            requiredIcon.setImageDrawable(it)
        }

        return category
    }

    companion object {
        private const val EXTRA_FORM_TEMPLATE = "JSON string for form template"
        private const val EXTRA_PATIENT_ID = "Patient id that the form is created for"
        private const val EXTRA_LANGUAGE_SELECTED = "String of language selected for a FormTemplate"
        private const val EXTRA_PATIENT_OBJECT = "The Patient object used to start patient profile"
        const val FIRST_CATEGORY_POSITION = 1

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