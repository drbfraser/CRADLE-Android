package com.cradleplatform.neptune.viewmodel

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.annotation.UiThread
import androidx.databinding.DataBindingComponent
import androidx.databinding.DataBindingUtil
import androidx.recyclerview.widget.RecyclerView
import com.cradleplatform.neptune.R
import com.cradleplatform.neptune.binding.FragmentDataBindingComponent
import com.cradleplatform.neptune.databinding.ListItemSavedFormBinding
import com.cradleplatform.neptune.model.FormResponse
import com.cradleplatform.neptune.model.Patient
import com.cradleplatform.neptune.view.FormRenderingActivity
import java.util.Date

/**
 * Based off of official Android sample at https://github.com/android/views-widgets-samples/tree/main/RecyclerViewKotlin/
 */
class SavedFormAdapter(private val formList: MutableList<FormResponse>, private val patient: Patient) :
    RecyclerView.Adapter<SavedFormAdapter.SavedFormViewHolder>() {
    class SavedFormViewHolder(
        itemView: View,
        private val binding: ListItemSavedFormBinding,
        private val patient: Patient
    ) : RecyclerView.ViewHolder(itemView) {
        init {
            itemView.setOnClickListener {
                binding.formResponse?.let { formResponse ->
                    // When the user clicks on a saved form, open the FormRenderingActivity
                    // using the saved form's questions and answers
                    val intent = FormRenderingActivity.makeIntentWithFormResponse(
                        itemView.context,
                        formResponse,
                        patient
                    )
                    itemView.context.startActivity(intent)
                }
            }
        }
        // Grab the individual views from list_item_saved_form.xml
        private val formClassNameTextView: TextView =
            itemView.findViewById(R.id.form_class_name_text)
        private val patientNameTextView: TextView =
            itemView.findViewById(R.id.patient_name_text)
        private val idTextView: TextView =
            itemView.findViewById(R.id.list_item_saved_form_id_text_view)
        private val dateLastEditedTextView: TextView =
            itemView.findViewById(R.id.list_item_saved_form_date_created_text_view)
        @UiThread
        fun bind(formResponse: FormResponse) {
            // Grab the FormResponse associated with the list item being bound
            binding.formResponse = formResponse
            // Insert custom text into each individual view
            formClassNameTextView.text = formResponse.formClassificationName
            patientNameTextView.text = patient.name
            idTextView.text = formResponse.formResponseId.toString()
            dateLastEditedTextView.text = Date(formResponse.dateEdited).toString()
            binding.executePendingBindings()
        }
    }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SavedFormViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.list_item_saved_form, parent, false)
        val binding = DataBindingUtil.inflate<ListItemSavedFormBinding>(
            LayoutInflater.from(parent.context),
            R.layout.list_item_saved_form,
            parent,
            false,
            dataBindingComponent
        )
        return SavedFormViewHolder(view, binding, patient)
    }
    override fun getItemCount(): Int {
        return formList.size
    }
    override fun onBindViewHolder(holder: SavedFormViewHolder, position: Int) {
        holder.bind(formList[position])
    }
    fun deleteItem(swipedPosition: Int) {
        formList.removeAt(swipedPosition)
        notifyItemRemoved(swipedPosition)
    }
    companion object {
        private val dataBindingComponent: DataBindingComponent = FragmentDataBindingComponent()
    }
}
