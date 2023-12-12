package com.cradleplatform.neptune.viewmodel

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.annotation.UiThread
import androidx.databinding.DataBindingUtil
import androidx.recyclerview.widget.RecyclerView
import com.cradleplatform.neptune.R
import com.cradleplatform.neptune.binding.FragmentDataBindingComponent
import com.cradleplatform.neptune.databinding.ContentSavedFormsBinding
import com.cradleplatform.neptune.databinding.ListItemPatientBinding
import com.cradleplatform.neptune.databinding.ListItemSavedFormBinding
import com.cradleplatform.neptune.model.FormResponse
import com.cradleplatform.neptune.view.FormRenderingActivity
import java.util.Date

/**
 * Based off of official Android sample at https://github.com/android/views-widgets-samples/tree/main/RecyclerViewKotlin/
 */
class SavedFormAdapter (private val formList: List<FormResponse>) :
    RecyclerView.Adapter<SavedFormAdapter.SavedFormViewHolder>() {
        class SavedFormViewHolder(
            itemView: View,
            private val binding: ListItemSavedFormBinding
        ) : RecyclerView.ViewHolder(itemView) {

            init {
                itemView.setOnClickListener {
                    Log.d("SavedFormAdapter", "clicked itemview")
                    Log.d("SavedFormAdapter", "binding is "+binding.toString())
                    Log.d("SavedFormAdapter", "formresponse is "+binding.formResponse)
                    binding.formResponse?.let { formResponse ->
                        val intent = FormRenderingActivity.makeIntentWithFormTemplate(
                            itemView.context,
                            formResponse.formTemplate,
                            formResponse.language,
                            formResponse.patientId,
                            null
                        )
                        Log.d("SavedFormAdapter", "intent is"+ intent.toString())
                        itemView.context.startActivity(intent)
                    }
                }
            }

            private val formClassNameTextView: TextView = itemView.findViewById(R.id.form_class_name_text)
            private val versionTextView: TextView = itemView.findViewById(R.id.list_item_saved_form_version_text_view)
            private val dateCreatedTextView: TextView = itemView.findViewById(R.id.list_item_saved_form_date_created_text_view)

            @UiThread
            fun bind(formResponse: FormResponse) {
                binding.formResponse = formResponse
                binding.executePendingBindings()
                formClassNameTextView.text = formResponse.formClassificationName
                versionTextView.text = formResponse.formTemplate.version
                dateCreatedTextView.text =  Date(formResponse.dateCreated).toString()
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

            return SavedFormViewHolder(view, binding)
        }

        override fun getItemCount(): Int {
            return formList.size
        }

        override fun onBindViewHolder(holder: SavedFormViewHolder, position: Int) {
            holder.bind(formList[position])
        }

        companion object {
            private val dataBindingComponent = FragmentDataBindingComponent()
        }
    }
