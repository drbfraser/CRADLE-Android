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
import com.cradleplatform.neptune.model.Question
import com.cradleplatform.neptune.model.QuestionTypeEnum
import com.cradleplatform.neptune.net.NetworkResult
import com.cradleplatform.neptune.utilities.CustomToast
import com.cradleplatform.neptune.view.adapters.FormViewAdapter
import com.cradleplatform.neptune.viewmodel.FormRenderingViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
        builder.setTitle("Are you sure?")
        builder.setMessage("This will discard the form!")

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

        adapter = FormViewAdapter(viewModel, languageSelected!!)
        recyclerView.adapter = adapter

        findViewById<Button>(R.id.btn_submit).setOnClickListener {
            formSubmission(languageSelected)
            finish()
        }
    }

    private fun formSubmission(languageSelected: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val result = viewModel.submitForm(patientId!!, languageSelected)
                if (result is NetworkResult.Success) {
                    withContext(Dispatchers.Main) {
                        CustomToast.shortToast(
                            applicationContext,
                            "Form Response Submitted"
                        )
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        CustomToast.shortToast(
                            applicationContext,
                            "Form Response Submission failed with network error:\n " +
                                "${result.getStatusMessage(applicationContext)}"
                        )
                    }
                }
            } catch (exception: IllegalArgumentException) {
                withContext(Dispatchers.Main) {
                    CustomToast.shortToast(
                        applicationContext,
                        "Form Response Failed to Create(Malformed):\n" +
                            "${exception.message}"
                    )
                    exception.printStackTrace()
                }
            }
        }
    }

    private fun questionsInASingleCategory(formTemplateFromIntent: FormTemplate) {

        var listOfQuestionsInSingleCategory: MutableList<Question> = mutableListOf()
        var listOfQuestionLists: MutableList<MutableList<Question>> = mutableListOf()

        var flag = false // to check if the question is a category
        formTemplateFromIntent.questions?.forEach() { Q ->

            //Log.d("TEST123", Q.questionType.toString())
            // if type == category and it's the first time a question of type category has been read
            if (Q.questionType == QuestionTypeEnum.CATEGORY && !flag) {
                flag = true
            }
            //else, store the current list if it's not empty and set it to empty
            if (Q.questionType == QuestionTypeEnum.CATEGORY && flag) {
                if (listOfQuestionsInSingleCategory.isNotEmpty()) {
                    listOfQuestionLists.add(listOfQuestionsInSingleCategory)
                    listOfQuestionsInSingleCategory = mutableListOf()
                }
                flag = false
            }

            listOfQuestionsInSingleCategory.add(Q)
        }
        // listOfQuestionLists
    }

    private fun fullQuestionList(formTemplateFromIntent: FormTemplate): MutableList<Question> {
        var listOfQuestions: MutableList<Question> = mutableListOf()
        formTemplateFromIntent.questions?.forEach() { Q ->
            listOfQuestions.add(Q)
        }
        return listOfQuestions
    }

    companion object {
        private const val EXTRA_FORM_TEMPLATE = "JSON string for form template"
        private const val EXTRA_PATIENT_ID = "Patient id that the form is created for"
        private const val EXTRA_LANGUAGE_SELECTED = "String of language selected for a FormTemplate"
        private const val EXTRA_PATIENT_OBJECT = "The Patient object used to start patient profile"
        private const val FLAG_IS_RECURSIVE_CALL = "Intent make from self"

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
/* Question List Test Loop

         //Log formTemplateFromIntent
       /*
       formTemplateFromIntent.questions?.forEach(){ Q ->
           Q.languageVersions?.get(0)?.questionText?.let { Log.d("TEST123", it); Log.d("TEST123",
               Q.questionType.toString()
           ) }
       }

        */

       var i = 0;
       listOfQuestionLists.forEach {
           Log.d("TEST123", "Category $i")

           for(question in it){
               question.languageVersions?.get(0)?.questionText?.let { it1 ->
                   Log.d("TEST123",
                       it1 + "inside loop"
                   )
               }
           }
           i++
       }

        */
