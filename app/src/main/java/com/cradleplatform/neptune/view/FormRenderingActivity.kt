package com.cradleplatform.neptune.view

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
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
import com.cradleplatform.neptune.model.RenderingController
import com.cradleplatform.neptune.viewmodel.FormRenderingViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@Suppress("LargeClass")
@AndroidEntryPoint
class FormRenderingActivity : AppCompatActivity() {

    private var layoutManager: RecyclerView.LayoutManager? = null
    private var adapter: RecyclerView.Adapter<RenderingController.ViewHolder>? = null
    private var btnNext: Button? = null
    val viewModel: FormRenderingViewModel by viewModels()

    @Inject
    lateinit var mFormManager: FormManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_form_rendering)

        val formTemplateFromIntent = intent.getSerializableExtra(EXTRA_FORM_TEMPLATE) as FormTemplate
        viewModel.currentFormTemplate = formTemplateFromIntent
        //viewModel = ViewModelProvider(this).get(FormRenderingViewModel::class.java)

        val patientId = intent.getStringExtra(EXTRA_PATIENT_ID)
        val patient = intent.getSerializableExtra(EXTRA_PATIENT_OBJECT) as Patient
        val languageSelected = intent.getStringExtra(EXTRA_LANGUAGE_SELECTED)
            ?: error("language selection missing from FormRenderingActivity Intent")
        val isCalledFromSelf = intent.getBooleanExtra(FLAG_IS_RECURSIVE_CALL, false)

        //Store the raw form template if there is not one in memory
        if (!isCalledFromSelf) {
            viewModel.resetRenderingFormAndAnswers()
            viewModel.setRenderingFormIfNull(viewModel.currentFormTemplate!!)
        }

        //Check if question list contains category
        if (getNumOfCategory(viewModel.currentFormTemplate!!) <= 0) {
            val intent = FormSelectionActivity.makeIntentForPatientId(
                this@FormRenderingActivity,
                patientId!!,
                patient
            )
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            intent.putExtra(EXTRA_PATIENT_ID, patientId)
            intent.putExtra("SUBMITTED", "true")

            lifecycleScope.launch {
                viewModel.submitForm(patientId = patientId, selectedLanguage = languageSelected)
            }
            startActivity(intent)
            finish()
            return
        }

        layoutManager = LinearLayoutManager(this)

        var recyclerView = findViewById<RecyclerView>(R.id.myRecyclerView)
        recyclerView.layoutManager = layoutManager

        btnNext = findViewById(R.id.btn_submit)
        if (getNumOfCategory(getRestCategory(formTemplateFromIntent)) == 0) {
            btnNext?.text = "Submit"
        }

        btnNext?.setOnClickListener {
            val newForm: FormTemplate = getRestCategory(formTemplateFromIntent)
            val intent = makeIntentFromSelf(
                this,
                newForm,
                languageSelected,
                patientId!!,
                patient
            )
            startActivity(intent)
        }

        val firstCategory: FormTemplate = getFirstCategory(formTemplateFromIntent)

        adapter = RenderingController(firstCategory, viewModel, languageSelected)
        recyclerView.adapter = adapter

        var i = getNumOfCategory(formTemplateFromIntent)
        Toast.makeText(this, "$i pages remaining", Toast.LENGTH_SHORT).show()
    }

    private fun getNumOfCategory(form: FormTemplate): Int {
        var num = 0
        if (form.questions!!.isEmpty()) {
            return num
        }
        for (question in form.questions) {
            if (question.questionType == QuestionTypeEnum.CATEGORY) {
                num += 1
            }
        }
        return num
    }

    private fun getFirstCategory(form: FormTemplate): FormTemplate {
        var questionList: List<Question> = form.questions!!
        var firstQuestionList: MutableList<Question> = mutableListOf()
        for (i in questionList.indices) {
            if (questionList[i].questionType == QuestionTypeEnum.CATEGORY && i != 0) {
                break
            } else {
                firstQuestionList.add(questionList[i])
            }
        }

        return form.copy(questions = firstQuestionList.toList())
    }

    private fun getRestCategory(form: FormTemplate): FormTemplate {
        val questionList: List<Question> = form.questions!!
        val restQuestionList: MutableList<Question> = mutableListOf()
        var notFirstCATEGORY: Boolean = false
        for (i in questionList.indices) {
            if (questionList[i].questionType == QuestionTypeEnum.CATEGORY && i != 0) {
                notFirstCATEGORY = true
            }
            if (notFirstCATEGORY) {
                restQuestionList.add(questionList[i])
            }
        }

        return form.copy(questions = restQuestionList.toList())
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

        @JvmStatic
        private fun makeIntentFromSelf(
            context: Context,
            formTemplate: FormTemplate,
            formLanguage: String,
            patientId: String,
            patient: Patient
        ): Intent {
            return makeIntentWithFormTemplate(
                context,
                formTemplate,
                formLanguage,
                patientId,
                patient
            ).putExtra(FLAG_IS_RECURSIVE_CALL, true)
        }
    }
}
