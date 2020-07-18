package com.cradle.neptune.viewmodel

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView
import com.cradle.neptune.R
import com.cradle.neptune.model.GlobalPatient

class GlobalPatientAdapter(private val patientList: List<GlobalPatient>) :
    RecyclerView.Adapter<GlobalPatientAdapter.GlobalPatientViewHolder>() {

    private val onPatientClickObserverList = ArrayList<OnGlobalPatientClickListener>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GlobalPatientViewHolder {
        val v =
            LayoutInflater.from(parent.context).inflate(R.layout.global_patient_card, parent, false)
        return GlobalPatientViewHolder(v)
    }

    override fun getItemCount(): Int {
        return patientList.size
    }

    override fun onBindViewHolder(holder: GlobalPatientViewHolder, position: Int) {
        val globalPatient = patientList[position]
        holder.village.text = globalPatient.villageNum
        holder.id.text = globalPatient.id
        holder.name.text = globalPatient.initials
        if (globalPatient.isMyPatient) {
            holder.addToMyPatientButton.background =
                holder.addToMyPatientButton.context.resources.getDrawable(R.drawable.ic_check_circle_black_24dp)
        } else {
            // There seems to be a bug in recycler view's recycling method
            // if the else statement is not here, it will recycle old views with the if
             // statement from above.
            holder.addToMyPatientButton.background =
                holder.addToMyPatientButton.context.resources.getDrawable(R.drawable.ic_add_circle_black_24dp)
        }
        holder.addToMyPatientButton.setOnClickListener {
            onAddClicked(globalPatient)
        }

        holder.cardview.setOnClickListener {
            onCardClicked(globalPatient)
        }
    }

    fun addPatientClickObserver(onGlobalPatientClickListener: OnGlobalPatientClickListener) =
        onPatientClickObserverList.add(onGlobalPatientClickListener)

    private fun onCardClicked(patient: GlobalPatient) =
        onPatientClickObserverList.forEach { it.onCardClick(patient) }

    private fun onAddClicked(patient: GlobalPatient) =
        onPatientClickObserverList.forEach { it.onAddToLocalClicked(patient) }

    interface OnGlobalPatientClickListener {

        fun onCardClick(patient: GlobalPatient)

        fun onAddToLocalClicked(patient: GlobalPatient)
    }

    inner class GlobalPatientViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        var name: TextView = itemView.findViewById(R.id.patientName)
        var id: TextView = itemView.findViewById(R.id.patientID)
        var village: TextView = itemView.findViewById(R.id.patientVillage)
        var addToMyPatientButton: ImageButton = itemView.findViewById(R.id.addToMyPatientFab)
        var cardview = itemView.findViewById<CardView>(R.id.patientCardview)
    }
}
