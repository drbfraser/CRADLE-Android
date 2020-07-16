package com.cradle.neptune.viewmodel

import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Filter
import android.widget.Filterable
import android.widget.ImageButton
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView
import com.cradle.neptune.R
import com.cradle.neptune.model.Patient
import com.cradle.neptune.model.Reading
import com.cradle.neptune.view.PatientProfileActivity
import com.cradle.neptune.viewmodel.PatientsViewAdapter.PatientViewHolder
import java.util.ArrayList
import java.util.Locale

class PatientsViewAdapter(
    private val patientList: List<Pair<Patient, Reading>>,
    private val context: Context
) : RecyclerView.Adapter<PatientViewHolder>(), Filterable {

    private var filteredList: List<Pair<Patient, Reading>>

    init {
        filteredList = patientList
    }

    override fun onCreateViewHolder(viewGroup: ViewGroup, i: Int): PatientViewHolder {
        val v = LayoutInflater.from(viewGroup.context)
            .inflate(R.layout.patient_card, viewGroup, false)
        return PatientViewHolder(v)
    }

    override fun onBindViewHolder(
        patientViewHolder: PatientViewHolder,
        i: Int
    ) {
        val (patient, reading) = filteredList[i]
        patientViewHolder.patientVillage.text = patient.villageNumber
        patientViewHolder.patientName.text = patient.name
        patientViewHolder.patientId.text = patient.id
        if (reading.followUp != null) {
            patientViewHolder.referralImg.setImageResource(R.drawable.ic_check_circle_black_24dp)
            patientViewHolder.referralImg.visibility = View.VISIBLE
        } else if (reading.isReferredToHealthCentre) {
            patientViewHolder.referralImg.setImageResource(R.drawable.ic_pending_referral_black_24dp)
            patientViewHolder.referralImg.visibility = View.VISIBLE
        }
        patientViewHolder.patientCardview.setOnClickListener {
            val intent = Intent(context, PatientProfileActivity::class.java)
            intent.putExtra("patient", patient)
            context.startActivity(intent)
        }
    }

    override fun getItemCount(): Int {
        return filteredList.size
    }

    override fun getFilter(): Filter {
        return object : Filter() {
            override fun performFiltering(charSequence: CharSequence): FilterResults {
                val charString = charSequence.toString()
                filteredList = if (charString.isEmpty()) {
                    patientList
                } else {
                    val filteredList: MutableList<Pair<Patient, Reading>> =
                        ArrayList()
                    for (pair in patientList) {
                        if (pair.first.id.contains(charString) ||
                            pair.first.name.toLowerCase(Locale.ROOT).contains(
                                charString.toLowerCase(
                                    Locale.ROOT
                                )
                            )
                        ) {
                            filteredList.add(pair)
                        }
                    }
                    filteredList
                }
                val filterResults = FilterResults()
                filterResults.values = filteredList
                return filterResults
            }

            override fun publishResults(
                charSequence: CharSequence,
                filterResults: FilterResults
            ) {
                filteredList =
                    filterResults.values as ArrayList<Pair<Patient, Reading>>
                notifyDataSetChanged()
            }
        }
    }

    class PatientViewHolder(itemView: View) :
        RecyclerView.ViewHolder(itemView) {
        var patientName: TextView = itemView.findViewById(R.id.patientName)
        var patientVillage: TextView = itemView.findViewById(R.id.patientVillage)
        var patientId: TextView = itemView.findViewById(R.id.patientID)
        var patientCardview: CardView = itemView.findViewById(R.id.patientCardview)
        var referralImg: ImageButton = itemView.findViewById(R.id.referralStatus)
    }
}