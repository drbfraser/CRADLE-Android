package com.cradleplatform.neptune.view

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.os.Bundle
import android.provider.Settings
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.cradleplatform.neptune.R
import com.cradleplatform.neptune.model.Answer
import com.cradleplatform.neptune.model.FormResponse
import com.cradleplatform.neptune.model.FormTemplate
import com.cradleplatform.neptune.model.Patient
import com.cradleplatform.neptune.model.Question
import com.cradleplatform.neptune.view.adapters.FormViewAdapter
import com.cradleplatform.neptune.viewmodel.FormRenderingViewModel
import com.google.android.material.bottomsheet.BottomSheetBehavior
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.Serializable

@Suppress("LargeClass")
@AndroidEntryPoint
class FormRenderingActivity : AppCompatActivity() {
    private var layoutManager: RecyclerView.LayoutManager? = null
    private var adapter: RecyclerView.Adapter<FormViewAdapter.ViewHolder>? = null
    private var patient: Patient? = null
    private var patientId: String? = null
    private var languageSelected: String? = null
    private var categoryViewList: MutableList<View> = mutableListOf()
    private var formResponseId: Long? = null
    private lateinit var recyclerView: RecyclerView
    private lateinit var bottomSheetCurrentSection: TextView
    private lateinit var bottomSheetCategoryContainer: LinearLayout
    private lateinit var bottomSheet: LinearLayout
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
            returnToPatientProfile()
        }

        builder.setNegativeButton(R.string.no) { _, _ ->
            //Do nothing...
        }
        builder.show()
        return true
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_form_rendering)

        //Getting the formTemplate from the intent
        val formTemplateFromIntent =
            intent.getSerializableExtra(EXTRA_FORM_TEMPLATE) as FormTemplate

        viewModel.currentFormTemplate = formTemplateFromIntent
        viewModel.populateEmptyIds(applicationContext)

        //Clear previous answers in view-model
        viewModel.clearAnswers()
        viewModel.changeCategory(FIRST_CATEGORY_POSITION)

        val answers = intent.getSerializableExtra(EXTRA_ANSWERS) as Map<String, Answer>?
        answers?.forEach { (s, answer) -> viewModel.addAnswer(s, answer) }

        // If the form was rendered from a saved form response, grab the form response ID
        if (intent.getBooleanExtra(EXTRA_IS_FROM_SAVED_FORM_RESPONSE, false)) {
            formResponseId = intent.getLongExtra(EXTRA_FORM_RESPONSE_ID, 0)
        }

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

        adapter = FormViewAdapter(viewModel, languageSelected!!, patient)

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
        // The ViewModel returns "CELLULAR" or "WIFI" or ""
        val internetString = viewModel.getInternetTypeString(applicationContext)

        if (internetString.isEmpty()) {
            builder.setPositiveButton(getString(R.string.form_dialog_not_connected_internet)) { _, _ ->
                val intent = Intent(Settings.ACTION_WIRELESS_SETTINGS)
                startActivity(intent)
            }
        } else {
            builder.setPositiveButton(internetString) { _, _ ->
                formSubmission(languageSelected, "HTTP")
                returnToPatientProfile()
            }
        }

        builder.setNeutralButton("SAVE AND SEND LATER") { _, _ ->
            saveForm(languageSelected)
            Toast.makeText(applicationContext, R.string.saved_form_success_dialog, Toast.LENGTH_SHORT).show()
            returnToPatientProfile()
        }

        builder.setNegativeButton(R.string.SMS) { _, _ ->
            formSubmission(languageSelected, "SMS")
            returnToPatientProfile()
        }
        builder.show()
    }

    private fun returnToPatientProfile() {
        intent = PatientProfileActivity.makeIntentForPatientId(applicationContext, patientId!!)
        // Clear the stack above PatientProfileActivity
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        startActivity(intent)
    }

    private fun formSubmission(languageSelected: String, submissionMode: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            viewModel.submitForm(patientId!!, languageSelected, submissionMode, applicationContext, formResponseId)
        }
    }

    private fun saveForm(languageSelected: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            viewModel.saveFormResponseToDatabase(patientId!!, languageSelected, formResponseId)
        }
    }

    private fun categoryChanged(currCategory: Int) {
        this.title = viewModel.categoryList?.getOrNull(currCategory - 1)?.first ?: "Cradle"

        bottomSheetCurrentSection.text = String.format(
            getString(R.string.form_current_section),
            currCategory, viewModel.categoryList?.size ?: 1
        )

        hideBottomSheet()

        adapter = FormViewAdapter(viewModel, languageSelected!!, patient)
        recyclerView.adapter = adapter

        formNextBtn.background = viewModel.isNextButtonVisible(applicationContext)
        formPrevBtn.background = viewModel.isPrevButtonVisible(applicationContext)

        categoryViewList.forEach { categoryView ->
            categoryView.findViewById<Button>(R.id.category_row_btn)?.let { button ->
                button.background = getDrawable(R.drawable.rounded_button_grey)
            }
        }
        val button: Button? = categoryViewList.getOrNull(currCategory - 1)?.findViewById(R.id.category_row_btn)
        button?.background = getDrawable(R.drawable.rounded_button_teal)
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
        bottomSheet = findViewById(R.id.form_bottom_sheet)
        bottomSheetBehaviour = BottomSheetBehavior.from(bottomSheet)
        formStateBtn = findViewById(R.id.form_state_button)

        bottomSheetBehaviour.isDraggable = false
        formStateBtn.setOnClickListener {
            when (bottomSheetBehaviour.state) {
                BottomSheetBehavior.STATE_EXPANDED -> {
                    hideBottomSheet()
                }
                else -> {
                    showBottomSheet()
                }
            }
        }

        viewModel.setCategorizedQuestions(languageSelected ?: "English", applicationContext)
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
            button.background = getDrawable(R.drawable.rounded_button_teal)
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

    private fun hideBottomSheet() {
        recyclerView.alpha = 1F
        bottomSheetBehaviour.state = BottomSheetBehavior.STATE_COLLAPSED
        formStateBtn.background = getDrawable(R.drawable.ic_baseline_arrow_up_24)
    }

    private fun showBottomSheet() {
        recyclerView.alpha = 0.3F
        bottomSheetBehaviour.state = BottomSheetBehavior.STATE_EXPANDED
        formStateBtn.background = getDrawable(R.drawable.ic_baseline_arrow_down_24)
    }

    /**
     * Hide bottom sheet if clicked outside boundaries
     * Code taken from: https://stackoverflow.com/questions/38185902/android-bottomsheet-how-to-collapse-when-clicked-outside
     */
    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
            if (bottomSheetBehaviour.state == BottomSheetBehavior.STATE_EXPANDED) {
                val outRect = Rect()
                bottomSheet.getGlobalVisibleRect(outRect)
                if (!outRect.contains(event.rawX.toInt(), event.rawY.toInt())) {
                    hideBottomSheet()
                }
            }
        }
        return super.dispatchTouchEvent(event)
    }

    companion object {
        private const val EXTRA_FORM_TEMPLATE = "JSON string for form template"
        private const val EXTRA_PATIENT_ID = "Patient id that the form is created for"
        private const val EXTRA_LANGUAGE_SELECTED = "String of language selected for a FormTemplate"
        private const val EXTRA_PATIENT_OBJECT = "The Patient object used to start patient profile"
        private const val EXTRA_ANSWERS = "The answers in the saved form response"
        private const val EXTRA_FORM_RESPONSE_ID = "The ID of the saved form response"
        private const val EXTRA_IS_FROM_SAVED_FORM_RESPONSE = "Whether the form that is being generated is a saved form"
        const val FIRST_CATEGORY_POSITION = 1

        @JvmStatic
        fun makeIntentWithFormTemplate(
            context: Context,
            formTemplate: FormTemplate,
            formLanguage: String,
            patientId: String,
            patient: Patient?
        ): Intent {
            val bundle = Bundle()
            bundle.putSerializable(EXTRA_FORM_TEMPLATE, formTemplate)
            bundle.putSerializable(EXTRA_PATIENT_OBJECT, patient)

            return Intent(context, FormRenderingActivity::class.java).apply {
                this.putExtra(EXTRA_PATIENT_ID, patientId)
                this.putExtra(EXTRA_LANGUAGE_SELECTED, formLanguage)
                this.putExtras(bundle)
            }
        }

        @JvmStatic
        fun makeIntentWithFormResponse(
            context: Context,
            formResponse: FormResponse,
            patient: Patient?
        ): Intent {
            val bundle = Bundle()
            bundle.putSerializable(EXTRA_FORM_TEMPLATE, formResponse.formTemplate)
            bundle.putSerializable(EXTRA_PATIENT_OBJECT, patient)
            bundle.putSerializable(EXTRA_ANSWERS, formResponse.answers as Serializable)

            return Intent(context, FormRenderingActivity::class.java).apply {
                this.putExtra(EXTRA_PATIENT_ID, formResponse.patientId)
                this.putExtra(EXTRA_LANGUAGE_SELECTED, formResponse.language)
                this.putExtra(EXTRA_FORM_RESPONSE_ID, formResponse.formResponseId)
                this.putExtra(EXTRA_IS_FROM_SAVED_FORM_RESPONSE, true)
                this.putExtras(bundle)
            }
        }
    }
}
