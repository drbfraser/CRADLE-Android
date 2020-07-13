package com.cradle.neptune.viewmodel

import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView
import com.cradle.neptune.R
import com.cradle.neptune.model.GlobalPatient
import com.cradle.neptune.model.Patient
import com.cradle.neptune.model.Sex
import com.cradle.neptune.view.GlobalPatientProfileActivity

class GlobalPatientAdapter(private val patientList:List<GlobalPatient>, private val context: Context):
    RecyclerView.Adapter<GlobalPatientAdapter.GlobalPatientViewHolder>() {

    inner class GlobalPatientViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        var name: TextView = itemView.findViewById(R.id.patientName)
        var id: TextView = itemView.findViewById(R.id.patientID)
        var village: TextView = itemView.findViewById(R.id.patientVillage)
        var addToMyPatientButton:ImageButton = itemView.findViewById(R.id.addToMyPatientFab)
        var cardview = itemView.findViewById<CardView>(R.id.patientCardview)
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
        holder.village.text = globalPatient.villageNum
        holder.id.text = globalPatient.id
        holder.name.text = globalPatient.initials

        holder.addToMyPatientButton.setOnClickListener(View.OnClickListener {
            val alertDialog = AlertDialog.Builder(context).setTitle("Are you sure?")
                .setMessage("Are you sure you want to add this patient as your own? ")
                .setPositiveButton("OK") { _: DialogInterface, i: Int ->
                    holder.addToMyPatientButton.background = context.resources.getDrawable(R.drawable.ic_check_circle_black_24dp)
                    holder.addToMyPatientButton.isEnabled = false
                }
                .setNegativeButton("NO") { _: DialogInterface, _: Int -> }
                .setIcon(R.drawable.ic_sync)
            alertDialog.show()
        })

        holder.cardview.setOnClickListener(View.OnClickListener {
            //todo replace it with an actual api call
            val patient = Patient(globalPatient.id,globalPatient.initials,null,33,null,Sex.FEMALE,false,"ZONE123",globalPatient.villageNum,
                emptyList(), emptyList())
            val intent = Intent(context, GlobalPatientProfileActivity::class.java)
            intent.putExtra("patient", patient)
            context.startActivity(intent)
        })

    }
}