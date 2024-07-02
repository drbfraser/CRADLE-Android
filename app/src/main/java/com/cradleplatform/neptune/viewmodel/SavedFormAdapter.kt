package com.cradleplatform.neptune.viewmodel

import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
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
class SavedFormAdapter(formMap: MutableMap<Patient, MutableList<FormResponse>>) :
    RecyclerView.Adapter<SavedFormAdapter.SavedFormViewHolder>() {
    class SavedFormViewHolder(
        private val binding: ListItemSavedFormBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        init {
            itemView.setOnClickListener {
                binding.formResponse?.let { formResponse ->
                    binding.patient?.let { patient ->
                        val intent = FormRenderingActivity.makeIntentWithFormResponse(
                            itemView.context,
                            formResponse,
                            patient
                        )
                        itemView.context.startActivity(intent)
                    }
                    // When the user clicks on a saved form, open the FormRenderingActivity
                    // using the saved form's questions and answers
                }
            }
            Log.d("look", "building in the adapter")
        }

        @UiThread
        fun bind(patient: Patient, formResponse: FormResponse) {
            // Grab the FormResponse associated with the list item being bound
            Log.d("look", "binding ${formResponse.formClassificationId} ${patient.name}")
            binding.formResponse = formResponse
            binding.patient = patient
            binding.listItemSavedFormDateCreatedTextView.text = Date(formResponse.dateEdited).toString()
            binding.executePendingBindings()
            Log.d("look", "TextView content: ${binding.formClassNameText.text}")
        }
    }

    private val flattenedList = flattenMap(formMap)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SavedFormViewHolder {
        val binding = DataBindingUtil.inflate<ListItemSavedFormBinding>(
            LayoutInflater.from(parent.context),
            R.layout.list_item_saved_form,
            parent,
            false,
            dataBindingComponent
        )
        Log.d("look", "creating view holder")
        return SavedFormViewHolder(binding)
    }
    override fun getItemCount(): Int {
        return flattenedList.size
    }
    override fun onBindViewHolder(holder: SavedFormViewHolder, position: Int) {
        val (patient, formResponse) = flattenedList[position]
        holder.bind(patient, formResponse)
    }
    fun deleteItem(swipedPosition: Int) {
        flattenedList.removeAt(swipedPosition)
        notifyItemRemoved(swipedPosition)
        return
    }

    private fun flattenMap(map: Map<Patient, List<FormResponse>>): MutableList<Pair<Patient, FormResponse>> {
        val list = mutableListOf<Pair<Patient, FormResponse>>()
        map.forEach { (patient, formResponses) ->
            formResponses.forEach { formResponse ->
                list.add(Pair(patient, formResponse))
            }
        }
        return list
    }
    companion object {
        private val dataBindingComponent: DataBindingComponent = FragmentDataBindingComponent()
    }
}
