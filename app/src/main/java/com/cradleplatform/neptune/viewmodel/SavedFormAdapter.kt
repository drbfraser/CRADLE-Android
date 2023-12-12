package com.cradleplatform.neptune.viewmodel

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.cradleplatform.neptune.R
import com.cradleplatform.neptune.model.FormResponse
import com.cradleplatform.neptune.utilities.DateUtil
import org.threeten.bp.DateTimeUtils

class SavedFormAdapter (private val formList: List<FormResponse>) :
    RecyclerView.Adapter<SavedFormAdapter.SavedFormViewHolder>() {
    // Describes an item view and its place within the RecyclerView
        class SavedFormViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val flowerTextView: TextView = itemView.findViewById(R.id.flower_text)
            private val versionTextView: TextView = itemView.findViewById(R.id.list_item_saved_form_version_text_view)
            private val dateCreatedTextView: TextView = itemView.findViewById(R.id.list_item_saved_form_date_created_text_view)

            fun bind(formResponse: FormResponse) {
                flowerTextView.text = formResponse.formClassificationName
                versionTextView.text = "Form template version: " + formResponse.formTemplate.version
                dateCreatedTextView.text = formResponse.dateCreated.toString()
            }
        }

        // Returns a new ViewHolder
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SavedFormViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.content_saved_forms, parent, false)

            return SavedFormViewHolder(view)
        }

        // Returns size of data list
        override fun getItemCount(): Int {
            return formList.size
        }

        // Displays data at a certain position
        override fun onBindViewHolder(holder: SavedFormViewHolder, position: Int) {
            holder.bind(formList[position])
        }
    }
