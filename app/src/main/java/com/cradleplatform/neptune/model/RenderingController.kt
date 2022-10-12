package com.cradleplatform.neptune.model

import android.app.DatePickerDialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.cradleplatform.neptune.R
import com.cradleplatform.neptune.ext.hideKeyboard
import com.cradleplatform.neptune.viewmodel.FormRenderingViewModel
import java.util.Calendar

class RenderingController(myForm: FormTemplate, myViewModel: FormRenderingViewModel, selectedLanguage: String) :
    RecyclerView.Adapter<RenderingController.ViewHolder>() {
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
        val v = LayoutInflater.from(parent.context).inflate(R.layout.form_question_card_layout, parent, false)
        return ViewHolder(v)
    }

    override fun onBindViewHolder(holder: RenderingController.ViewHolder, position: Int) {
        // Hide keyboard if lost focus
        holder.itemNumberAnswer.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {

                holder.context.hideKeyboard(holder.itemNumberAnswer)
            }
        }

        //Store user input of type int
        holder.itemNumberAnswer.setOnClickListener {
            val questionIndex = form.questions!![position].questionIndex!!
            val textAnswer = holder.itemNumberAnswer.text.toString()
            val answer = Pair(questionIndex, textAnswer)
            viewModel.addAnswer(answer)
            viewModel.currentAnswer.value = textAnswer
            DtoData.form.add(answer)
        }

        //Store user input of type string
        holder.itemTextAnswer.setOnClickListener {
            val questionIndex = form.questions!![position].questionIndex!!
            val textAnswer = holder.itemTextAnswer.text.toString()
            val answer = Pair(questionIndex, textAnswer)
            viewModel.addAnswer(answer)
            viewModel.currentAnswer.value = textAnswer
            DtoData.form.add(answer)
        }

        //Store user input of type Date
        holder.itemDatePicker.setOnClickListener {
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

            val questionIndex = form.questions!![position].questionIndex!!
            val textAnswer = holder.itemMultipleChoice.getItemAtPosition(myPosition).toString()
            val answer = Pair(questionIndex, textAnswer)
            viewModel.addAnswer(answer)
            viewModel.currentAnswer.value = textAnswer
            DtoData.form.add(answer)
        }

        holder.itemQuestion.text = form.questions!![position]
            .languageVersions!!.find { it.language == formLanguage }
            ?.questionText
            ?: "Question Language Error: Does not support selected language($formLanguage)"

        //Rendering the question card
        when (form.questions!![position].questionType) {
            "CATEGORY" -> {
                holder.itemDatePicker.visibility = View.GONE
                holder.itemTextAnswer.visibility = View.GONE
                holder.itemNumberAnswer.visibility = View.GONE
                holder.itemMultipleChoice.visibility = View.GONE
                holder.itemQuestion.textSize = 25F
            }
            "DATETIME" -> {
                holder.itemDatePicker.visibility = View.VISIBLE
                holder.itemTextAnswer.visibility = View.GONE
                holder.itemNumberAnswer.visibility = View.GONE
                holder.itemMultipleChoice.visibility = View.GONE
            }
            "STRING" -> {
                holder.itemTextAnswer.visibility = View.VISIBLE
                holder.itemNumberAnswer.visibility = View.GONE
                holder.itemDatePicker.visibility = View.GONE
                holder.itemMultipleChoice.visibility = View.GONE
                setHint(holder.itemTextAnswer, form.questions!![position], holder.context)
            }
            "INTEGER" -> {
                holder.itemNumberAnswer.visibility = View.VISIBLE
                holder.itemTextAnswer.visibility = View.GONE
                holder.itemDatePicker.visibility = View.GONE
                holder.itemMultipleChoice.visibility = View.GONE
                setHint(holder.itemNumberAnswer, form.questions!![position], holder.context)
            }

            "MULTIPLE_CHOICE" -> {
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
        val questionIndex = form.questions!![position].questionIndex!!
        val textAnswer = holder.itemDatePicker.text.toString()
        val answer = Pair(questionIndex, textAnswer)
        viewModel.addAnswer(answer)
        viewModel.currentAnswer.value = textAnswer
        DtoData.form.add(answer)
    }

    private fun setHint(hint: TextView, theQuestion: Questions, context: Context) {
        val type = theQuestion.questionType
        val numMin: Double? = theQuestion.numMin
        val numMax: Double? = theQuestion.numMax
        val isRequired = theQuestion.required!!

        if (type == "STRING") {
            if (isRequired) {
                hint.hint = context.getString(R.string.is_required)
            } else {
                hint.hint = context.getString(R.string.is_optional)
            }
        } else if (type == "INTEGER") {
            if (isRequired) {
                hint.hint = context.getString(R.string.is_required) + ": " +
                    context.getString(R.string.data_range) + "($numMin, $numMax)"
            } else {
                hint.hint = context.getString(R.string.is_optional)
            }
        }
    }
}
