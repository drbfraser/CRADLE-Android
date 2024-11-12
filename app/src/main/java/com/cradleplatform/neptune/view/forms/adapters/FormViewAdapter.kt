package com.cradleplatform.neptune.view.forms.adapters

import android.annotation.SuppressLint
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.text.SpannableString
import android.text.style.StyleSpan
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.setPadding
import androidx.core.widget.doOnTextChanged
import androidx.recyclerview.widget.RecyclerView
import com.cradleplatform.neptune.R
import com.cradleplatform.neptune.databinding.CardLayoutBinding
import com.cradleplatform.neptune.model.Answer
import com.cradleplatform.neptune.model.McOption
import com.cradleplatform.neptune.model.Patient
import com.cradleplatform.neptune.model.Question
import com.cradleplatform.neptune.model.QuestionTypeEnum.*
import com.cradleplatform.neptune.utilities.DateUtil
import com.cradleplatform.neptune.viewmodel.forms.FormRenderingViewModel
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
    private var mList = viewModel.getCurrentQuestionsList()

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
        val langVersion = mList[position].languageVersions

        var questionText = langVersion?.find {
            it.language == languageSelected
        }?.questionText ?: R.string.not_available.toString()

        // add number of question in front of question
        if (questionText != R.string.not_available.toString()) {
            questionText = "${position + 1}. $questionText"
        }

        // add asterisk if field is required
        if (mList[position].required == true) {
            questionText += " *"
        }

        val boldQuestionString = SpannableString(questionText)
        boldQuestionString.setSpan(StyleSpan(Typeface.BOLD), 0, boldQuestionString.length, 0)

        holder.binding.tvQuestion.text = boldQuestionString

        //Depending on question type, we are setting one of the four possible types of inputs to visible.
        holder.binding.tvQuestion.textSize = 18f

        val questionID = mList[position].questionId!!

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
                val textView = holder.binding.etAnswer as TextView
                textView.visibility = View.VISIBLE
                setHint(textView, mList[position], context)

                //If question has answer repopulate it
                val answer = viewModel.getTextAnswer(mList[position].questionId)
                if (answer?.isNotEmpty() == true) {
                    textView.text = answer
                }
            }

            "DATETIME" -> {
                holder.binding.btnDatePicker.visibility = View.VISIBLE

                //Enabling Click on Select Date
                holder.binding.btnDateTimePicker.setOnClickListener {
                    showDateTimePicker(
                        context,
                        holder.binding.btnDateTimePicker,
                        holder,
                        mList[position].questionId
                    )
                }
            }

            "DATE" -> {
                holder.binding.btnDatePicker.visibility = View.VISIBLE

                //Enabling Click on Select Date
                holder.binding.btnDatePicker.setOnClickListener {
                    showDatePicker(
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

                //If question has answer repopulate it
                viewModel.getNumericAnswer(mList[position].questionId)?.toInt()?.let {
                    val textView = holder.binding.etNumAnswer as TextView
                    textView.text = it.toString()
                }
            }

            "MULTIPLE_CHOICE" -> {
                holder.binding.rgMultipleChoice.visibility = View.VISIBLE

                //Programmatically adding radio buttons for each option
                val langMcOptions = mList[position].languageVersions?.find {
                    it.language == languageSelected
                }?.mcOptions ?: listOf(
                    //Error Language Not Found
                    McOption(
                        -1,
                        context.resources.getString(R.string.mc_unsupported, languageSelected)
                    )
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
                                it.opt.equals(context.getString(R.string.yes), true)
                            ) {
                                autoFillMCId = radioButton.id
                            }
                            if (!hasAllergy &&
                                it.opt.equals(context.getString(R.string.no), true)
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
                } else {
                    //If question has answer repopulate it
                    viewModel.getMCAnswer(mList[position].questionId)?.getOrNull(0)?.let {
                        holder.binding.rgMultipleChoice.check(it)
                    }
                }
            }

            "MULTIPLE_SELECT" -> {
                holder.binding.checkboxContainer.visibility = View.VISIBLE

                val langMcOptions = mList[position].languageVersions?.find {
                    it.language == languageSelected
                }?.mcOptions

                if (langMcOptions?.isNotEmpty() == true) {
                    langMcOptions.forEach {
                        val checkBox = CheckBox(context)
                        checkBox.text = it.opt
                        checkBox.gravity = Gravity.LEFT
                        checkBox.layoutParams =
                            LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.WRAP_CONTENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT
                            )
                        checkBox.setPadding(8)

                        val mcAnswers = viewModel.getMCAnswer(questionID)
                        if (mcAnswers?.contains(it.mcid) == true) checkBox.isChecked = true

                        holder.binding.checkboxContainer.addView(checkBox)

                        checkBox.setOnCheckedChangeListener { _, isChecked ->
                            val currMCAnswers: ArrayList<Int> =
                                ArrayList(viewModel.getMCAnswer(questionID) ?: listOf())

                            if (isChecked) {
                                it.mcid?.let { id -> currMCAnswers.add(id) }
                            } else {
                                it.mcid?.let { id -> currMCAnswers.remove(id) }
                            }

                            viewModel.addAnswer(questionID, Answer.createMcAnswer(currMCAnswers))
                        }
                    }
                } else {
                    holder.binding.checkboxContainer.visibility = View.GONE
                }
            }
        }

        //Setting Listeners for EditTexts for Saving Answers
        setEditTextListeners(position, holder)
    }

    private fun setEditTextListeners(position: Int, holder: ViewHolder) {
        val question = mList[position]
        val questionID = question.questionId!!
        val maxLines = question.stringMaxLines

        //String Answers Listener
        val etAnswer = holder.binding.etAnswer
        etAnswer.doOnTextChanged { text, _, _, _ ->
            if (text.toString().isNotEmpty()) {
                val lines = etAnswer.lineCount
                if (maxLines != null && lines > maxLines) {
                    etAnswer.error =
                        context.getString(R.string.form_maximum_lines_allowed, maxLines.toString())
                } else {
                    etAnswer.error = null
                }

                viewModel.addAnswer(questionID, Answer.createTextAnswer(text.toString()))
            } else {
                viewModel.deleteAnswer(questionID)
            }
        }

        holder.binding.etNumAnswer.doOnTextChanged { text, _, _, _ ->
            val numText = text.toString()
            if (numText.isNotEmpty()) {
                viewModel.addAnswer(questionID, Answer.createNumericAnswer(numText.toInt()))
            } else {
                viewModel.deleteAnswer(questionID)
            }
        }

        //Time and Date Answers Listener exists in the Dialog Below // saveAnswerForDateTime(holder, questionId)

        //Multiple Choice Answers Listener
        holder.binding.rgMultipleChoice.setOnCheckedChangeListener { radioGroup, i ->
            val selectedID = radioGroup.checkedRadioButtonId
            val mcidArray = listOf(selectedID)

            viewModel.addAnswer(questionID, Answer.createMcAnswer(mcidArray))
        }
    }

    @SuppressLint("SetTextI18n")
    private fun showDatePicker(
        context: Context,
        itemDatePicker: Button,
        holder: ViewHolder,
        questionId: String?
    ) {
        val calender = Calendar.getInstance()
        val year = calender.get(Calendar.YEAR)
        val month = calender.get(Calendar.MONTH)
        val day = calender.get(Calendar.DAY_OF_MONTH)

        val dpd = DatePickerDialog(
            context,
            { view, selectedYear, selectedMonth, selectedDayOfMonth ->
                val date = "$selectedYear/${selectedMonth + 1}/$selectedDayOfMonth"
                itemDatePicker.text = date
                saveAnswerForDate(holder, questionId)
            },
            year,
            month,
            day
        )

        dpd.datePicker.maxDate = System.currentTimeMillis()
        dpd.show()
    }

    @SuppressLint("SetTextI18n")
    private fun showDateTimePicker(
        context: Context,
        itemDatePicker: Button,
        holder: ViewHolder,
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

    private fun saveAnswerForDate(holder: ViewHolder, questionId: String?) {
        val textAnswer = holder.binding.btnDatePicker.text.toString()
        viewModel.addAnswer(
            questionId!!,
            Answer.createTextAnswer(textAnswer)
        )
    }

    private fun saveAnswerForDateTime(holder: ViewHolder, questionId: String?) {
        val textAnswer = holder.binding.btnDateTimePicker.text.toString()
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

            context.getString(R.string.form_patient_medical_history) -> {
                if (!patient?.medicalHistory.isNullOrEmpty()) {
                    textView.text = patient!!.medicalHistory
                    viewModel.addAnswer(
                        questionID,
                        Answer.createTextAnswer(patient!!.medicalHistory)
                    )
                }
            }
        }
    }
}
