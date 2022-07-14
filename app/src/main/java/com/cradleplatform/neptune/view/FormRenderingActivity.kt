package com.cradleplatform.neptune.view

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.cradleplatform.neptune.R
import com.cradleplatform.neptune.model.FormTemplate
import com.cradleplatform.neptune.model.Questions
import com.cradleplatform.neptune.model.RecyclerAdapter

class FormRenderingActivity : AppCompatActivity() {

    private var layoutManager: RecyclerView.LayoutManager? = null
    private var adapter: RecyclerView.Adapter<RecyclerAdapter.ViewHolder>? = null
    private var form: FormTemplate? = null
    private var btnNext: Button? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_form_rendering)

        form = intent.getSerializableExtra(EXTRA_FORM_TEMPLATE) as FormTemplate
        var id = intent.getStringExtra(EXTRA_PATIENT_ID)

        //Check if question list contains category
        if (getNumOfCategory(form!!) <= 0) {
            val intent = FormSelectionActivity.makeIntentForPatientId(this, id!!)
            startActivity(intent)
            finish()
        }

        var i = getNumOfCategory(form!!)
        Toast.makeText(this, "$i pages remaining", Toast.LENGTH_SHORT).show()

        layoutManager = LinearLayoutManager(this)

        var recyclerView = findViewById<RecyclerView>(R.id.myRecyclerView)
        recyclerView.layoutManager = layoutManager

        btnNext = findViewById(R.id.btn_submit)
        if (getNumOfCategory(getRestCategory(form!!)) == 0) {
            btnNext?.text = "Submit"
        }

        btnNext?.setOnClickListener {
            val newForm: FormTemplate = getRestCategory(form!!)
            val intent = makeIntentWithFormTemplate(this, newForm, id!!)
            startActivity(intent)
        }

        val firstCategory: FormTemplate = getFirstCategory(form!!)

        adapter = RecyclerAdapter(firstCategory)
        recyclerView.adapter = adapter
    }

    companion object {
        private const val EXTRA_FORM_TEMPLATE = "JSON string for form template"
        private const val EXTRA_PATIENT_ID = "Patient id that the form is created for"

        @JvmStatic
        fun makeIntentWithFormTemplate(
            context: Context,
            formTemplate: FormTemplate,
            patientId: String
        ): Intent {
            val bundle = Bundle()
            bundle.putSerializable(EXTRA_FORM_TEMPLATE, formTemplate)
            return Intent(context, FormRenderingActivity::class.java).apply {
                this.putExtra(EXTRA_PATIENT_ID, patientId)
                this.putExtras(bundle)
            }
        }
    }

    fun getNumOfCategory(form: FormTemplate): Int {
        var num = 0
        if (form.questions.isEmpty()) {
            return num
        }
        for (question in form.questions) {
            if (question.questionType == "CATEGORY") {
                num += 1
            }
        }
        return num
    }

    fun getFirstCategory(form: FormTemplate): FormTemplate {
        var questionList: List<Questions> = form.questions
        var firstQuestionList: MutableList<Questions> = mutableListOf()
        for (i in questionList.indices) {
            if (questionList[i].questionType == "CATEGORY" && i != 0) {
                break
            } else {
                firstQuestionList.add(questionList[i])
            }
        }

        var newForm: FormTemplate = FormTemplate(
            form.version, form.name, form.dateCreated,
            form.id, form.lastEdited, form.lang, firstQuestionList.toList()
        )

        return newForm
    }

    fun getRestCategory(form: FormTemplate): FormTemplate {
        var questionList: List<Questions> = form.questions
        var restQuestionList: MutableList<Questions> = mutableListOf()
        var notFirstCATEGORY: Boolean = false
        for (i in questionList.indices) {
            if (questionList[i].questionType == "CATEGORY" && i != 0) {
                notFirstCATEGORY = true
            }
            if (notFirstCATEGORY) {
                restQuestionList.add(questionList[i])
            }
        }

        var newForm: FormTemplate = FormTemplate(
            form.version, form.name, form.dateCreated,
            form.id, form.lastEdited, form.lang, restQuestionList.toList()
        )

        return newForm
    }
}
