package com.cradle.neptune.viewmodel

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import androidx.annotation.UiThread
import androidx.databinding.DataBindingUtil
import androidx.recyclerview.widget.RecyclerView
import com.cradle.neptune.R
import com.cradle.neptune.binding.FragmentDataBindingComponent
import com.cradle.neptune.database.views.LocalSearchPatient
import com.cradle.neptune.databinding.ListItemPatientBinding

class LocalSearchPatientViewHolder(
    private val binding: ListItemPatientBinding
) : RecyclerView.ViewHolder(binding.root) {

    init {
        itemView.setOnClickListener {
            binding.localSearchPatient?.id?.let {
                Toast.makeText(
                    itemView.context,
                    "TODO: Open patient profile activity",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    @UiThread
    fun bind(localSearchPatient: LocalSearchPatient?) {
        localSearchPatient?.let {
            binding.localSearchPatient = it
            binding.executePendingBindings()
        }
    }

    companion object {
        private val dataBindingComponent = FragmentDataBindingComponent()

        fun create(parent: ViewGroup): LocalSearchPatientViewHolder {
            val binding = DataBindingUtil.inflate<ListItemPatientBinding>(
                LayoutInflater.from(parent.context),
                R.layout.list_item_patient,
                parent,
                false,
                dataBindingComponent
            )
            return LocalSearchPatientViewHolder(binding)
        }
    }
}
