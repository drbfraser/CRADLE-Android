package com.cradleplatform.neptune.view

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.cradleplatform.neptune.R
import com.cradleplatform.neptune.model.FormResponse
import com.cradleplatform.neptune.model.FormTemplate
import com.cradleplatform.neptune.model.Question
import com.cradleplatform.neptune.model.QuestionLangVersion
import com.cradleplatform.neptune.model.QuestionTypeEnum
import com.cradleplatform.neptune.model.VisibleCondition
import com.cradleplatform.neptune.viewmodel.LocalSearchPatientAdapter
import com.cradleplatform.neptune.viewmodel.SavedFormAdapter
import com.cradleplatform.neptune.viewmodel.SavedFormsViewModel
import com.google.android.material.textfield.TextInputLayout
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import org.checkerframework.checker.signedness.SignednessUtil.toDouble
import org.checkerframework.checker.signedness.SignednessUtil.toUnsignedInt

@AndroidEntryPoint
class SavedFormsActivity : AppCompatActivity() {

    private val viewModel: SavedFormsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_saved_forms)



        lifecycleScope.launch {
            val questions = mutableListOf<Question>()
            val visibleCondition = listOf(VisibleCondition(null, null, null));
            // Create 10 categories and questions to test
            // Note that the 10th category has no questions so it must not appear
            for (i in 1..10) {
                val stringQuestion = Question(
                    id = "Q$i",
                    visibleCondition = visibleCondition,
                    isBlank = true,
                    formTemplateId = "1",
                    questionIndex = 0,
                    numMin = toDouble(1) ,
                    numMax = toDouble(10),
                    stringMaxLength = 20,
                    questionId = "Q$i",
                    questionType = QuestionTypeEnum.STRING,
                    hasCommentAttached = false,
                    required = false,
                    languageVersions = listOf(QuestionLangVersion("test-language",
                        "test-parent","Test $i",i, emptyList()
                    ))
                )
                val categoryQuestion = Question(
                    id = "C$i",
                    visibleCondition = visibleCondition,
                    isBlank = true,
                    formTemplateId = "1",
                    questionIndex = 0,
                    numMin = toDouble(1) ,
                    numMax = toDouble(10) ,
                    stringMaxLength = 20,
                    questionId = "C$i",
                    questionType = QuestionTypeEnum.CATEGORY,
                    hasCommentAttached = false,
                    required = false,
                    languageVersions = listOf(QuestionLangVersion("test-language",
                        "test-parent","Category #$i",i, emptyList()
                    ))
                )
                questions.add(stringQuestion)
                questions.add(categoryQuestion)
            }
            Log.d("SavedFormsActivity", "adding form response to viewmodel")
            viewModel.addFormResponse(
                FormResponse(
                    "1",
                    "000",
                    FormTemplate(
                        "1",
                        false,
                        1,
                        "1",
                        "1",
                        "classname",
                        questions),
                    "test-language",
                    answers = emptyMap(),
                )
            )
            viewModel.addFormResponse(
                FormResponse(
                    "2",
                    "000",
                    FormTemplate(
                        "1",
                        false,
                        1,
                        "1",
                        "1",
                        "classname2",
                        questions),
                    "test-language",
                    answers = emptyMap(),
                )
            )
            val formList = viewModel.searchForFormResponsesByPatientId("000")
            Log.d("SavedFormsActivity", questions.toString())
            Log.d("SavedFormsActivity", formList.toString())

            val recyclerView: RecyclerView = findViewById(R.id.recycler_view)
            recyclerView.adapter = formList?.let { SavedFormAdapter(it) }
        }

    }

    companion object {
        @JvmStatic
        fun makeIntent(context: Context): Intent =
            Intent(context, SavedFormsActivity::class.java).apply {
//                putExtra(EXTRA_PATIENT_ID, patientId)
//                putExtra(FORM_SELECTION_EXTRA_PATIENT, patient)
            }
    }

}
