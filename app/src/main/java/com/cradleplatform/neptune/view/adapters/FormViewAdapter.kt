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
import androidx.core.content.ContextCompat
import androidx.core.view.get
import androidx.recyclerview.widget.RecyclerView
import com.cradleplatform.neptune.R
import com.cradleplatform.neptune.databinding.CardLayoutBinding
import com.cradleplatform.neptune.model.Answer
import com.cradleplatform.neptune.model.QuestionTypeEnum.*
import com.cradleplatform.neptune.viewmodel.FormRenderingViewModel
import java.util.Calendar
import kotlin.reflect.typeOf

/**
 * A custom adapter for rendering a list of questions
 * @param context The context of the application
 * @param questions The list of questions to render
 * @param viewModel The view model for the form rendering (Question Class serves as the information holder)
 */
class FormViewAdapter(
    //private val mList: MutableList<Question>,
    private var viewModel: FormRenderingViewModel,
    private var languageSelected: String
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

        //TODO(have to replace 0 with language)

        //Setting the question text
        holder.binding.tvQuestion.text =
            mList[position].languageVersions?.get(0)?.questionText ?: "No Question Text"

        //Log.d("TEST123", mList[position].questionType.toString() + " " + mList[position].languageVersions?.get(0)?.questionText.toString())
        //Log.d("TEST123",
        //  (mList[position].questionType.toString() == "CATEGORY").toString() + " " + mList[position].languageVersions?.get(0)?.questionText.toString())

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
            }

            "MULTIPLE_CHOICE" -> {
                holder.binding.rgMultipleChoice.visibility = View.VISIBLE

                //Programmatically adding radio buttons for each option
                mList[position].languageVersions?.get(0)?.mcOptions?.forEach {
                    val radioButton = RadioButton(context)
                    radioButton.text = it.opt
                    radioButton.id = it.mcid!!
                    holder.binding.rgMultipleChoice.addView(radioButton)
                }
            }

            "MULTIPLE_SELECT" -> {
                //
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
                val numAnswer = holder.binding.etAnswer.text.toString().toInt()
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
        Log.d("TAG123" , textAnswer)
        viewModel.addAnswer(
            questionId!!,
            Answer.createTextAnswer(textAnswer)
        )
    }
}

/*
class RenderingController(myForm: FormTemplate, myViewModel: FormRenderingViewModel, selectedLanguage: String) :
   RecyclerView.Adapter<RenderingController.
   ViewHolder>() {
   private var form: FormTemplate = myForm
   private val formLanguage: String = selectedLanguage
   private var selectedDate: String? = null
   private var viewModel = myViewModel


   object Utility {
       fun setListViewHeightBasedOnChildren(listView: ListView) {

           val listAdapter = listView.adapter
               ?: // pre-condition
               return
           var totalHeight = 0
           var i = 0
           val len = listAdapter.count
           while (i < len) {

               val listItem = listAdapter.getView(i, null, listView)
               listItem.measure(0, 0)
               totalHeight += listItem.measuredHeight
               i++
           }
           val params = listView.layoutParams
           params.height = totalHeight + listView.dividerHeight * (listAdapter.count - 1)
           listView.layoutParams = params
       }
   }



    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        var itemQuestion: TextView
        var itemTextAnswer: TextView
        var itemNumberAnswer: TextView
        var itemDatePicker: Button
        var itemMultipleChoice: ListView
        var context: Context = itemView.context

        init {
            itemQuestion = itemView.findViewById(R.id.tv_question)
            itemTextAnswer = itemView.findViewById(R.id.et_answer)
            itemDatePicker = itemView.findViewById(R.id.btn_date_picker)
            itemMultipleChoice = itemView.findViewById(R.id.lv_multiple_choice)
            itemMultipleChoice.choiceMode = ListView.CHOICE_MODE_MULTIPLE
            itemMultipleChoice.setItemChecked(position, true)
            itemNumberAnswer = itemView.findViewById(R.id.et_num_answer)
        }
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): RenderingController.ViewHolder {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.card_layout, parent, false)
        return ViewHolder(v)
    }

    override fun onBindViewHolder(holder: RenderingController.ViewHolder, position: Int) {

        val questionId = form.questions!![position].questionId

        // Hide keyboard if lost focus
        holder.itemNumberAnswer.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                holder.context.hideKeyboard(holder.itemNumberAnswer)
            }
        }

        //Store user input of type int
        holder.itemNumberAnswer.doAfterTextChanged {
            // can be Double or Long
            holder.itemNumberAnswer.text.toString().toDoubleOrNull()?.let {
                viewModel.addAnswer(
                    questionId!!,
                    Answer.createNumericAnswer(it)
                )
            }
        }

        //Store user input of type string
        holder.itemTextAnswer.doAfterTextChanged {
            val textAnswer = holder.itemTextAnswer.text.toString()
            viewModel.addAnswer(
                questionId!!,
                Answer.createTextAnswer(textAnswer)
            )
        }

        //Store user input of type Date
        holder.itemDatePicker.doAfterTextChanged {
            clickDataPicker(holder.context, holder.itemDatePicker, position, holder)
        }

        //Store user input of type multiple choice
        holder.itemMultipleChoice.setOnItemClickListener { parent, view, myPosition, id ->
            Toast.makeText(
                holder.context,
                holder.itemMultipleChoice.getItemAtPosition(myPosition).toString(),
                Toast.LENGTH_SHORT
            ).show()

            if ((view.background as? ColorDrawable)?.color == ContextCompat.getColor(
                    holder.context,
                    R.color.button_selected_gray
                )
            ) {
                view.setBackgroundColor(Color.parseColor("#d9d9d9"))
            } else {
                view.setBackgroundColor(Color.parseColor("#8d99ae"))
            }

            val mcAnswer = mcidArrayFromListView(holder.itemMultipleChoice)
            viewModel.addAnswer(
                questionId!!,
                Answer.createMcAnswer(mcAnswer)
            )
        }

        holder.itemQuestion.text = form.questions!![position]
            .languageVersions!!.find { it.language == formLanguage }
            ?.questionText
            ?: "Question Language Error: Does not support selected language($formLanguage)"

        //Rendering the question card
        when (form.questions!![position].questionType) {
            CATEGORY -> {
                holder.itemDatePicker.visibility = View.GONE
                holder.itemTextAnswer.visibility = View.GONE
                holder.itemNumberAnswer.visibility = View.GONE
                holder.itemMultipleChoice.visibility = View.GONE
                holder.itemQuestion.textSize = 25F
            }
            DATE -> {
                holder.itemDatePicker.visibility = View.VISIBLE
                holder.itemTextAnswer.visibility = View.GONE
                holder.itemNumberAnswer.visibility = View.GONE
                holder.itemMultipleChoice.visibility = View.GONE
            }
            STRING -> {
                holder.itemTextAnswer.visibility = View.VISIBLE
                holder.itemNumberAnswer.visibility = View.GONE
                holder.itemDatePicker.visibility = View.GONE
                holder.itemMultipleChoice.visibility = View.GONE
                setHint(holder.itemTextAnswer, form.questions!![position], holder.context)
            }
            INTEGER -> {
                holder.itemNumberAnswer.visibility = View.VISIBLE
                holder.itemTextAnswer.visibility = View.GONE
                holder.itemDatePicker.visibility = View.GONE
                holder.itemMultipleChoice.visibility = View.GONE
                setHint(holder.itemNumberAnswer, form.questions!![position], holder.context)
            }

            MULTIPLE_CHOICE -> {
                holder.itemNumberAnswer.visibility = View.GONE
                holder.itemDatePicker.visibility = View.GONE
                holder.itemTextAnswer.visibility = View.GONE

                val questionList: MutableList<String> = mutableListOf()
                for (mcOption in form.questions!![position].mcOptions!!) {
                    questionList.add(mcOption.opt!!)
                }
                val adapter = ArrayAdapter<String>(
                    holder.context,
                    android.R.layout.simple_list_item_1,
                    questionList
                )

                holder.itemMultipleChoice.adapter = adapter
                Utility.setListViewHeightBasedOnChildren(holder.itemMultipleChoice)
                holder.itemMultipleChoice.visibility = View.VISIBLE
            }
        }
    }

    override fun getItemCount(): Int {
        return form.questions!!.size
    }

    private fun clickDataPicker(
        context: Context,
        itemDatePicker: Button,
        position: Int,
        holder: RenderingController.ViewHolder
    ) {
        val calender = Calendar.getInstance()
        val year = calender.get(Calendar.YEAR)
        val month = calender.get(Calendar.MONTH)
        val day = calender.get(Calendar.DAY_OF_MONTH)
        val dpd = DatePickerDialog(
            context,
            { view, selectedYear, selectedMonth, selectedDayOfMonth ->
                val date = "$selectedYear/${selectedMonth + 1}/$selectedDayOfMonth"
                selectedDate = date
                itemDatePicker.text = selectedDate

                okClick(position, holder)
            },
            year,
            month,
            day
        )
        dpd.datePicker.maxDate = System.currentTimeMillis()
        dpd.show()
    }

    private fun okClick(position: Int, holder: RenderingController.ViewHolder) {
        val questionId = form.questions!![position].questionId!!
        val textAnswer = holder.itemDatePicker.text.toString()
        viewModel.addAnswer(
            questionId,
            Answer.createTextAnswer(textAnswer)
        )
    }

    private fun setHint(hint: TextView, theQuestion: Question, context: Context) {
        val type = theQuestion.questionType
        val numMin: Double? = theQuestion.numMin
        val numMax: Double? = theQuestion.numMax
        val isRequired = theQuestion.required!!

        if (type == STRING) {
            if (isRequired) {
                hint.hint = context.getString(R.string.is_required)
            } else {
                hint.hint = context.getString(R.string.is_optional)
            }
        } else if (type == INTEGER) {
            if (isRequired) {
                hint.hint = context.getString(R.string.is_required) + ": " +
                    context.getString(R.string.data_range) + "($numMin, $numMax)"
            } else {
                hint.hint = context.getString(R.string.is_optional)
            }
        }
    }

    private fun mcidArrayFromListView(listView: ListView): List<Int> {

        val checkedItemList = mutableListOf<Int>()
        listView.checkedItemPositions.forEach { key, selected ->
            if (selected) {
                checkedItemList.add(key)
            }
        }
        return checkedItemList
    }
}

 */
