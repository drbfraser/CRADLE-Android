package com.cradleplatform.neptune.view

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.cradleplatform.neptune.R
import com.cradleplatform.neptune.manager.FormManager
import com.cradleplatform.neptune.model.DtoData
import com.cradleplatform.neptune.model.FormTemplate
import com.cradleplatform.neptune.model.Patient
import com.cradleplatform.neptune.model.Questions
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
    private var form: FormTemplate? = null
    lateinit var viewModel: FormRenderingViewModel
    private var patientID = ""
    private lateinit var patientObject: Patient


    @Inject
    lateinit var mFormManager: FormManager

    override fun onResume() {
        super.onResume()

        val btnBack: Button = findViewById(R.id.btn_back)
        btnBack.setOnClickListener {
            finish()
        }

    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_form_rendering)

        //setting the arrow on actionbar
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        form = intent.getSerializableExtra(EXTRA_FORM_TEMPLATE) as FormTemplate
        viewModel = ViewModelProvider(this).get(FormRenderingViewModel::class.java)

        patientID = intent.getStringExtra(EXTRA_PATIENT_ID).toString()
        patientObject = intent.getSerializableExtra(EXTRA_PATIENT_OBJECT) as Patient

        //Get memory of user answers
        if (DtoData.form.isNotEmpty()) {
            for (a in DtoData.form) {
                if (!viewModel.form.contains(a)) {
                    viewModel.addAnswer(a)
                }
            }
            //Reset the memory to the latest user answers
            DtoData.form = viewModel.form
        }

        //Store the raw form template if there is not one in memory
        if (DtoData.template == null) {
            DtoData.template = form
        }

        //Check if question list contains category
        if (getNumOfCategory(form!!) <= 0) {
            val intent = FormSelectionActivity.makeIntentForPatientId(
                this@FormRenderingActivity,
                patientID,
                patientObject
            )
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            intent.putExtra(EXTRA_PATIENT_ID, patientID)
            intent.putExtra("SUBMITTED", "true")

            DtoData.template?.let { viewModel.generateForm(it) }
            DtoData.resultForm = viewModel.myFormResult

            lifecycleScope.launch {
                viewModel.submitForm(mFormManager)
            }
            startActivity(intent)
            finish()
            return
        }

        var totalPages = getNumOfCategory(form!!)

        supportActionBar?.title = "$totalPages page(s) left"

        val btnNext: Button  = findViewById(R.id.btn_submit)
        if (getNumOfCategory(getRestCategory(form!!)) == 0) {
            btnNext.text = "Submit"
        }

        val languageSelected = intent.getStringExtra(EXTRA_LANGUAGE_SELECTED)
            ?: error("language selection missing from FormRenderingActivity Intent")

        btnNext.setOnClickListener {
            val newForm: FormTemplate = getRestCategory(form!!)
            val intent = makeIntentWithFormTemplate(
                this,
                newForm,
                languageSelected,
                patientID,
                patientObject
            )
            viewModel.currentCategory = viewModel.currentCategory + 1
            startActivity(intent)
        }

        layoutManager = LinearLayoutManager(this)

        val recyclerView = findViewById<RecyclerView>(R.id.myRecyclerView)
        recyclerView.layoutManager = layoutManager

        val firstCategory: FormTemplate = getFirstCategory(form!!)

        adapter = RenderingController(firstCategory, viewModel, languageSelected)
        recyclerView.adapter = adapter
    }

    override fun onSupportNavigateUp(): Boolean {

        val builder = AlertDialog.Builder(this)
        builder.setTitle("Are you sure?")
        builder.setMessage("This will discard the form!")

        builder.setPositiveButton(R.string.yes) { _, _ ->
            val intent = FormSelectionActivity.makeIntentForPatientId(
                this@FormRenderingActivity,
                patientID,
                patientObject
            )
            startActivity(intent)
        }

        builder.setNegativeButton(R.string.no) { _, _ ->
            //Do nothing...
        }
        builder.show()
        return true
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

            return Intent(context, FormRenderingActivity::class.java).apply {
                this.putExtra(EXTRA_PATIENT_ID, patientId)
                this.putExtra(EXTRA_LANGUAGE_SELECTED, formLanguage)
                this.putExtras(bundle)
            }
        }
    }

    private fun getNumOfCategory(form: FormTemplate): Int {
        var num = 0

        for (question in form.questions!!) {
            if (question.questionType == "CATEGORY") {
                num += 1
            }
        }
        return num
    }

    private fun getFirstCategory(form: FormTemplate): FormTemplate {
        val questionList: List<Questions> = form.questions!!
        val firstQuestionList: MutableList<Questions> = mutableListOf()
        for (i in questionList.indices) {
            if (questionList[i].questionType == "CATEGORY" && i != 0) {
                break
            } else {
                firstQuestionList.add(questionList[i])
            }
        }

        return form.copy(questions = firstQuestionList.toList())
    }

    private fun getRestCategory(form: FormTemplate): FormTemplate {
        val questionList: List<Questions> = form.questions!!
        val restQuestionList: MutableList<Questions> = mutableListOf()
        var notFirstCATEGORY = false
        for (i in questionList.indices) {
            if (questionList[i].questionType == "CATEGORY" && i != 0) {
                notFirstCATEGORY = true
            }
            if (notFirstCATEGORY) {
                restQuestionList.add(questionList[i])
            }
        }

        return form.copy(questions = restQuestionList.toList())
    }
}
