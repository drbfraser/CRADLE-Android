package com.cradleplatform.neptune.viewmodel

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.cradleplatform.neptune.R
import com.cradleplatform.neptune.model.FormResponse
import java.util.Date

/**
 * Based off of official Android sample at https://github.com/android/views-widgets-samples/tree/main/RecyclerViewKotlin/
 */
class SavedFormAdapter (private val formList: List<FormResponse>) :
    RecyclerView.Adapter<SavedFormAdapter.SavedFormViewHolder>() {
        class SavedFormViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val formClassNameTextView: TextView = itemView.findViewById(R.id.form_class_name_text)
            private val versionTextView: TextView = itemView.findViewById(R.id.list_item_saved_form_version_text_view)
            private val dateCreatedTextView: TextView = itemView.findViewById(R.id.list_item_saved_form_date_created_text_view)

            fun bind(formResponse: FormResponse) {
                formClassNameTextView.text = formResponse.formClassificationName
                versionTextView.text = formResponse.formTemplate.version
                dateCreatedTextView.text =  Date(formResponse.dateCreated).toString()
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SavedFormViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.content_saved_forms, parent, false)

            return SavedFormViewHolder(view)
        }

        override fun getItemCount(): Int {
            return formList.size
        }

        override fun onBindViewHolder(holder: SavedFormViewHolder, position: Int) {
            holder.bind(formList[position])
        }
    }
