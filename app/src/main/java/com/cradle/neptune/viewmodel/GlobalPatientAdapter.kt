package com.cradle.neptune.viewmodel

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.cradle.neptune.R
import com.cradle.neptune.model.GlobalPatient

class GlobalPatientAdapter(private val patientList:List<GlobalPatient>):
    RecyclerView.Adapter<GlobalPatientAdapter.GlobalPatientViewHolder>() {

    inner class GlobalPatientViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        var name: TextView = itemView.findViewById(R.id.patientName)
        var id: TextView = itemView.findViewById(R.id.patientID)
        var village: TextView = itemView.findViewById(R.id.patientVillage)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GlobalPatientViewHolder {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.global_patient_card,parent,false)
        return GlobalPatientViewHolder(v)
    }

    override fun getItemCount(): Int {
        return patientList.size
    }

    override fun onBindViewHolder(holder: GlobalPatientViewHolder, position: Int) {
        val globalPatient = patientList[position]
        holder.village.setText(globalPatient.villageNum)
        holder.id.setText(globalPatient.id)
        holder.name.setText(globalPatient.initials)
    }
}