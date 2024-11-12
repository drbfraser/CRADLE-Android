package com.cradleplatform.neptune.adapters.patients

import android.view.ViewGroup
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import com.cradleplatform.neptune.database.views.LocalSearchPatient
import com.cradleplatform.neptune.viewmodel.patients.LocalSearchPatientViewHolder

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
