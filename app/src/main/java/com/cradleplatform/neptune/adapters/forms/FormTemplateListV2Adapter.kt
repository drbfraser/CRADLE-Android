package com.cradleplatform.neptune.adapters.forms

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.cradleplatform.neptune.R
import com.cradleplatform.neptune.model.FormTemplateShallowV2
import com.cradleplatform.neptune.utilities.DateUtil

/**
 * adapter for the RecyclerView in FormTemplateListV2Activity. 
 * Displays a list of form templates, each with a name and subtitle.
 * it also uses shallow model to avoid unnecessary data transfer and processing.
 * 
 */
class FormTemplateListV2Adapter(
    private val templates: List<FormTemplateShallowV2>
) : RecyclerView.Adapter<FormTemplateListV2Adapter.ViewHolder>() {

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val nameText: TextView = itemView.findViewById(R.id.template_name_text)
        val subtitleText: TextView = itemView.findViewById(R.id.template_subtitle_text)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.list_item_form_template_v2, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val template = templates[position]
        holder.nameText.text = template.name
        holder.subtitleText.text = holder.itemView.context.getString(
            R.string.form_template_v2_subtitle,
            template.version,
            DateUtil.getDateStringFromTimestamp(template.dateCreated)
        )
    }

    override fun getItemCount(): Int = templates.size
}
