package com.cradleplatform.neptune.viewmodel

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.annotation.UiThread
import androidx.databinding.DataBindingUtil
import androidx.recyclerview.widget.RecyclerView
import com.cradleplatform.neptune.R
import com.cradleplatform.neptune.binding.FragmentDataBindingComponent
import com.cradleplatform.neptune.database.views.LocalSearchPatient
import com.cradleplatform.neptune.databinding.ListItemPatientBinding
import com.cradleplatform.neptune.view.PatientProfileActivity

class LocalSearchPatientViewHolder(
    private val binding: ListItemPatientBinding
) : RecyclerView.ViewHolder(binding.root) {

    init {
        itemView.setOnClickListener {
            binding.localSearchPatient?.id?.let { id ->
                val intent = PatientProfileActivity.makeIntentForPatientId(itemView.context, id)
                itemView.context.startActivity(intent)
            }
        }
    }

    @UiThread
    fun bind(localSearchPatient: LocalSearchPatient?) {
        binding.localSearchPatient = localSearchPatient
        binding.executePendingBindings()
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
