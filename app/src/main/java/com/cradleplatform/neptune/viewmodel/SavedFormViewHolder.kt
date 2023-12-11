package com.cradleplatform.neptune.viewmodel

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.annotation.UiThread
import androidx.databinding.DataBindingUtil
import androidx.recyclerview.widget.RecyclerView
import com.cradleplatform.neptune.R
import com.cradleplatform.neptune.binding.FragmentDataBindingComponent
import com.cradleplatform.neptune.databinding.ListItemSavedFormBinding
import com.cradleplatform.neptune.model.FormResponse
import com.cradleplatform.neptune.view.SavedFormsActivity

class SavedFormViewHolder(
    private val binding: ListItemSavedFormBinding
) : RecyclerView.ViewHolder(binding.root) {

    init {
        itemView.setOnClickListener {
            binding.formResponse?.formResponseId?.let { formResponseId ->
                val intent = SavedFormsActivity.makeIntentForPatientId(itemView.context, formResponseId)
                itemView.context.startActivity(intent)
            }
        }
    }

    @UiThread
    fun bind(formResponse: FormResponse?) {
        binding.formResponse = formResponse
        binding.executePendingBindings()
    }

    companion object {
        private val dataBindingComponent = FragmentDataBindingComponent()

        fun create(parent: ViewGroup): SavedFormViewHolder {
            val binding = DataBindingUtil.inflate<ListItemSavedFormBinding>(
                LayoutInflater.from(parent.context),
                R.layout.list_item_saved_form,
                parent,
                false,
                dataBindingComponent
            )
            return SavedFormViewHolder(binding)
        }
    }
}
