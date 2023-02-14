package com.cradleplatform.neptune.view

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.cradleplatform.neptune.R
import com.cradleplatform.neptune.manager.FormManager
import com.cradleplatform.neptune.model.FormTemplate
import com.cradleplatform.neptune.model.Patient
import com.cradleplatform.neptune.view.adapters.FormViewAdapter
import com.cradleplatform.neptune.viewmodel.FormRenderingViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@Suppress("LargeClass")
@AndroidEntryPoint
class FormRenderingActivity : AppCompatActivity() {

    private var layoutManager: RecyclerView.LayoutManager? = null
    private var adapter: RecyclerView.Adapter<FormViewAdapter.ViewHolder>? = null
    private var patient: Patient? = null
    private var patientId: String? = null
    val viewModel: FormRenderingViewModel by viewModels()

    @Inject
    lateinit var mFormManager: FormManager

    override fun onSupportNavigateUp(): Boolean {

        val builder = AlertDialog.Builder(this)
        builder.setTitle(R.string.are_you_sure)
        builder.setMessage(R.string.discard_form_dialog)

        builder.setPositiveButton(R.string.yes) { _, _ ->
            val intent = FormSelectionActivity.makeIntentForPatientId(
                this@FormRenderingActivity,
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
        setContentView(R.layout.activity_form_rendering)

        //Getting the formTemplate from the intent
        val formTemplateFromIntent =
            intent.getSerializableExtra(EXTRA_FORM_TEMPLATE) as FormTemplate

        viewModel.currentFormTemplate = formTemplateFromIntent
        //setting the arrow on actionbar
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        //Getting the patient from the intent (ID and Patient Object)
        patientId = intent.getStringExtra(EXTRA_PATIENT_ID)!!
        patient = intent.getSerializableExtra(EXTRA_PATIENT_OBJECT)!! as Patient

        //Getting the language selected from the intent
        val languageSelected = intent.getStringExtra(EXTRA_LANGUAGE_SELECTED)

        //Form a question list from Category to next category
        //Pass the list to recyclerView

        layoutManager = LinearLayoutManager(this)

        var recyclerView = findViewById<RecyclerView>(R.id.myRecyclerView)
        recyclerView.layoutManager = layoutManager

        recyclerView.recycledViewPool.setMaxRecycledViews(0, 0)

        //check if language selected exists in the form template
        //The language's available are already shown in the dropdown

        adapter = FormViewAdapter(viewModel, languageSelected!!, patient)

        recyclerView.adapter = adapter

        findViewById<Button>(R.id.btn_submit).setOnClickListener {
            //A toast is displayed to user if require field is not filled
            if (viewModel.isRequiredFieldsFilled(languageSelected, applicationContext)) {
                showFormSubmissionModeDialog(languageSelected)
            }
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

    /*
    private fun questionsInASingleCategory(formTemplateFromIntent: FormTemplate) {

        var listOfQuestionsInSingleCategory: MutableList<Question> = mutableListOf()
        var listOfQuestionLists: MutableList<MutableList<Question>> = mutableListOf()

        var isCategory = false // to check if the question is a category
        formTemplateFromIntent.questions?.forEach() { Q ->

            //Log.d("TEST123", Q.questionType.toString())
            // if type == category and it's the first time a question of type category has been read
            if (Q.questionType == QuestionTypeEnum.CATEGORY && !isCategory) {
                isCategory = true
            }
            //else, store the current list if it's not empty and set it to empty
            if (Q.questionType == QuestionTypeEnum.CATEGORY && isCategory) {
                if (listOfQuestionsInSingleCategory.isNotEmpty()) {
                    listOfQuestionLists.add(listOfQuestionsInSingleCategory)
                    listOfQuestionsInSingleCategory = mutableListOf()
                }
                isCategory = false
            }

            listOfQuestionsInSingleCategory.add(Q)
        }
        // listOfQuestionLists
    }

     */

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

            return Intent(context, FormRenderingActivity::class.java).apply {
                this.putExtra(EXTRA_PATIENT_ID, patientId)
                this.putExtra(EXTRA_LANGUAGE_SELECTED, formLanguage)
                this.putExtras(bundle)
            }
        }
    }
}
