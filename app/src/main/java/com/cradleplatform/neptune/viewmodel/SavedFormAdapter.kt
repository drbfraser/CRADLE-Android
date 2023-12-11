package com.cradleplatform.neptune.viewmodel

import android.view.ViewGroup
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import com.cradleplatform.neptune.database.views.LocalSearchPatient
import com.cradleplatform.neptune.model.FormResponse

class SavedFormAdapter :
    PagingDataAdapter<FormResponse, SavedFormViewHolder>(diffCallback) {

    override fun onBindViewHolder(holder: SavedFormViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): SavedFormViewHolder = SavedFormViewHolder.create(parent)

    companion object {
        private val diffCallback = object : DiffUtil.ItemCallback<FormResponse>() {
            override fun areContentsTheSame(
                oldItem: FormResponse,
                newItem: FormResponse
            ): Boolean = newItem == oldItem

            override fun areItemsTheSame(
                oldItem: FormResponse,
                newItem: FormResponse
            ): Boolean = oldItem.formResponseId == newItem.formResponseId
        }
    }
}
