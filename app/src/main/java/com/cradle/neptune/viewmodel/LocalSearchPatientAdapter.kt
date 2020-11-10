package com.cradle.neptune.viewmodel

import android.view.ViewGroup
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import com.cradle.neptune.database.views.LocalSearchPatient

class LocalSearchPatientAdapter :
    PagingDataAdapter<LocalSearchPatient, LocalSearchPatientViewHolder>(diffCallback) {

    override fun onBindViewHolder(holder: LocalSearchPatientViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): LocalSearchPatientViewHolder = LocalSearchPatientViewHolder.create(parent)

    companion object {
        private val diffCallback = object : DiffUtil.ItemCallback<LocalSearchPatient>() {
            override fun areContentsTheSame(
                oldItem: LocalSearchPatient,
                newItem: LocalSearchPatient
            ): Boolean = oldItem == newItem

            override fun areItemsTheSame(
                oldItem: LocalSearchPatient,
                newItem: LocalSearchPatient
            ): Boolean = oldItem.id == newItem.id
        }
    }
}
