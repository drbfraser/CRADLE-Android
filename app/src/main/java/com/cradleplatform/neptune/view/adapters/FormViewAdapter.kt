package com.cradleplatform.neptune.view.adapters

import android.annotation.SuppressLint
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.graphics.Color
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.RadioButton
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.cradleplatform.neptune.R
import com.cradleplatform.neptune.databinding.CardLayoutBinding
import com.cradleplatform.neptune.model.Answer
import com.cradleplatform.neptune.model.McOption
import com.cradleplatform.neptune.model.Patient
import com.cradleplatform.neptune.model.Question
import com.cradleplatform.neptune.model.QuestionTypeEnum.*
import com.cradleplatform.neptune.utilities.DateUtil
import com.cradleplatform.neptune.viewmodel.FormRenderingViewModel
import java.util.Calendar

/**
 * A custom adapter for rendering a list of questions
 * @param context The context of the application
 * @param questions The list of questions to render
 * @param viewModel The view model for the form rendering (Question Class serves as the information holder)
 */
class FormViewAdapter(
    private var viewModel: FormRenderingViewModel,
    private var languageSelected: String,
    private var patient: Patient?
) : RecyclerView.Adapter<FormViewAdapter.ViewHolder>() {

    lateinit var context: Context

    //Getting the Question List
    private var mList = viewModel.fullQuestionList()

    inner class ViewHolder(val binding: CardLayoutBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        context = parent.context
        return ViewHolder(
            CardLayoutBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )
    }

    override fun getItemCount(): Int {
        return mList.size
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {

        //Setting the question text
        var langVersion = mList[position].languageVersions

        var questionText = langVersion?.find {
            it.language == languageSelected
        }?.questionText ?: R.string.not_available.toString()

        holder.binding.tvQuestion.text = questionText

        //Depending on question type, we are setting one of the four possible types of inputs to visible.
        holder.binding.tvQuestion.textSize = 18f

        //Using Enum caused problems
        when (mList[position].questionType.toString()) {
            "CATEGORY" -> {
                //Setting Text Size for Categories (Headings)
                holder.binding.tvQuestion.textSize = 24f

                //Setting Colors
                holder.binding.tvQuestion.setTextColor(Color.parseColor("#FFFFFF"))
                holder.binding.cardView.setCardBackgroundColor(
                    ContextCompat.getColor(
                        holder.binding.root.context,
                        R.color.colorPrimaryDark
                    )
                )
                holder.binding.linearLayout.background =
                    ContextCompat.getDrawable(holder.binding.root.context, R.color.colorPrimaryDark)
            }

            "STRING" -> {
                holder.binding.etAnswer.visibility = View.VISIBLE
                setHint(holder.binding.etAnswer, mList[position], context)
            }

            "DATETIME" -> {
                holder.binding.btnDatePicker.visibility = View.VISIBLE

                //Enabling Click on Select Date
                holder.binding.btnDatePicker.setOnClickListener {
                    showDateTimePicker(
                        context,
                        holder.binding.btnDatePicker,
                        holder,
                        mList[position].questionId
                    )
                }
            }

            "INTEGER" -> {
                holder.binding.etNumAnswer.visibility = View.VISIBLE
                setHint(holder.binding.etNumAnswer, mList[position], context)
            }

            "MULTIPLE_CHOICE" -> {
                holder.binding.rgMultipleChoice.visibility = View.VISIBLE

                //Programmatically adding radio buttons for each option
                val langMcOptions = mList[position].languageVersions?.find {
                    it.language == languageSelected
                }?.mcOptions ?: listOf(
                    //Error Language Not Found
                    McOption(-1, context.resources.getString(R.string.mc_unsupported, languageSelected))
                )

                //Keep track of radio button id to select
                var autoFillMCId = -1
                langMcOptions.forEach {
                    val radioButton = RadioButton(context)
                    radioButton.text = it.opt
                    radioButton.id = it.mcid!!
                    holder.binding.rgMultipleChoice.addView(radioButton)

                    //logic for pre population
                    when (mList[position].questionId) {

                        context.getString(R.string.form_patient_sex) -> {
                            if (patient?.sex?.name?.equals(it.opt, true) == true) {
                                autoFillMCId = radioButton.id
                            }
                        }

                        context.getString(R.string.form_patient_has_allergy) -> {
                            val hasAllergy = !patient?.allergy.isNullOrEmpty()
                            if (hasAllergy &&
                                it.opt.equals(context.getString(R.string.form_allergy_yes), true)
                            ) {
                                autoFillMCId = radioButton.id
                            }
                            if (!hasAllergy &&
                                it.opt.equals(context.getString(R.string.form_allergy_no), true)
                            ) {
                                autoFillMCId = radioButton.id
                            }
                        }
                    }
                }
                if (autoFillMCId != -1) {
                    holder.binding.rgMultipleChoice.check(autoFillMCId)
                    //add answer to viewmodel
                    mList[position].questionId?.let {
                        viewModel.addAnswer(it, Answer.createMcAnswer(listOf(autoFillMCId)))
                    }
                }
            }

            "MULTIPLE_SELECT" -> {
                //Needs a form to test with multiple selections available (Currently only one selection available)
            }
        }

        //Setting Listeners for EditTexts for Saving Answers
        setEditTextListeners(position, holder)
    }

    private fun setEditTextListeners(position: Int, holder: ViewHolder) {
        var questionID = mList[position].questionId!!

        //String Answers Listener
        holder.binding.etAnswer.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                //does not have focus
                val textAnswer = holder.binding.etAnswer.text.toString()
                viewModel.addAnswer(questionID, Answer.createTextAnswer(textAnswer))
            }
        }

        //Integer Answers Listener
        holder.binding.etNumAnswer.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val numAnswer = holder.binding.etNumAnswer.text.toString().toInt()
                viewModel.addAnswer(questionID, Answer.createNumericAnswer(numAnswer))
            }
        }

        //Time and Date Answers Listener exists in the Dialog Below // saveAnswerForDateTime(holder, questionId)

        //Multiple Choice Answers Listener
        holder.binding.rgMultipleChoice.setOnCheckedChangeListener { radioGroup, i ->
            var selectedID = radioGroup.checkedRadioButtonId
            var selectedButton = radioGroup.findViewById<RadioButton>(selectedID)
            //var selectedText = selectedButton.text.toString()
            var mcidArray = listOf(selectedID)

            viewModel.addAnswer(questionID, Answer.createMcAnswer(mcidArray))
            //Log.d("TAG123", selectedButton.id.toString())
        }
    }

    @SuppressLint("SetTextI18n")
    private fun showDateTimePicker(
        context: Context,
        itemDatePicker: Button,
        holder: FormViewAdapter.ViewHolder,
        questionId: String?
    ) {
        val calender = Calendar.getInstance()
        val year = calender.get(Calendar.YEAR)
        val month = calender.get(Calendar.MONTH)
        val day = calender.get(Calendar.DAY_OF_MONTH)

        val hr = calender.get(Calendar.HOUR)
        val min = calender.get(Calendar.MINUTE)

        val tpd = TimePickerDialog(
            context,
            { view, selectedHour, selectedMinute ->
                val time = "$selectedHour:$selectedMinute"
                itemDatePicker.text = itemDatePicker.text.toString() + " " + time

                saveAnswerForDateTime(holder, questionId)
            },
            hr,
            min,
            true
        )

        val dpd = DatePickerDialog(
            context,
            { view, selectedYear, selectedMonth, selectedDayOfMonth ->
                val date = "$selectedYear/${selectedMonth + 1}/$selectedDayOfMonth"
                itemDatePicker.text = date

                tpd.show()
            },
            year,
            month,
            day
        )

        dpd.datePicker.maxDate = System.currentTimeMillis()
        dpd.show()
    }

    private fun saveAnswerForDateTime(holder: FormViewAdapter.ViewHolder, questionId: String?) {
        val textAnswer = holder.binding.btnDatePicker.text.toString()
        Log.d("TAG123", textAnswer)
        viewModel.addAnswer(
            questionId!!,
            Answer.createTextAnswer(textAnswer)
        )
    }

    private fun setHint(hint: TextView, question: Question, context: Context) {
        val type = question.questionType
        val numMin: Double? = question.numMin
        val numMax: Double? = question.numMax
        val isRequired = question.required!!

        prePopulateText(hint, question.questionId)

        if (type == STRING) {
            if (hint.text.isEmpty()) {
                if (isRequired) {
                    hint.hint = context.getString(R.string.is_required)
                } else {
                    hint.hint = context.getString(R.string.is_optional)
                }
            }
        } else if (type == INTEGER) {
            if (hint.text.isEmpty()) {
                if (isRequired) {
                    hint.hint = context.getString(R.string.is_required) + ": " +
                        context.getString(R.string.data_range) + "($numMin, $numMax)"
                } else {
                    hint.hint = context.getString(R.string.is_optional)
                }
            }
        }
    }

    private fun prePopulateText(textView: TextView, questionID: String?) {
        when (questionID) {
            context.getString(R.string.form_patient_name) -> {
                if (!patient?.name.isNullOrEmpty()) {
                    textView.text = patient!!.name
                    // Focus is not on edit text during pre population
                    viewModel.addAnswer(questionID, Answer.createTextAnswer(patient!!.name))
                }
            }
            context.getString(R.string.form_patient_age) -> {
                val age = DateUtil.getAgeFromDOB(patient?.dob)
                if (age.isNotEmpty()) {
                    textView.text = age
                    viewModel.addAnswer(questionID, Answer.createNumericAnswer(age.toInt()))
                }
            }
            context.getString(R.string.form_patient_allergies) -> {
                if (!patient?.allergy.isNullOrEmpty()) {
                    textView.text = patient!!.allergy
                    viewModel.addAnswer(questionID, Answer.createTextAnswer(patient!!.allergy))
                }
            }
        }
    }
}
