package com.cradleplatform.neptune.adapters.forms

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.cradleplatform.neptune.R
import com.cradleplatform.neptune.model.FormV2Draft
import com.cradleplatform.neptune.model.getTranslation
import com.cradleplatform.neptune.utilities.DateUtil

class DraftFormsV2Adapter(
    private val drafts: List<FormV2Draft>
) : RecyclerView.Adapter<DraftFormsV2Adapter.DraftViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DraftViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.list_item_form_v2_draft, parent, false)
        return DraftViewHolder(view)
    }

    override fun onBindViewHolder(holder: DraftViewHolder, position: Int) {
        val draft = drafts[position]
        val context = holder.itemView.context
        holder.formName.text = draft.formTemplate.classification.name.getTranslation(draft.language)
            ?.takeIf { it.isNotBlank() } ?: context.getString(R.string.form_v2_unnamed)
        holder.updatedAt.text = context.getString(
            R.string.form_v2_draft_last_edited,
            DateUtil.getConciseDateString(draft.updatedAt / 1000, false)
        )
    }

    override fun getItemCount(): Int = drafts.size

    class DraftViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val formName: TextView = view.findViewById(R.id.form_name)
        val updatedAt: TextView = view.findViewById(R.id.form_updated_at)
    }
}
