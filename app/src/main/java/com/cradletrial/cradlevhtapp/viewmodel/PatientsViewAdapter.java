package com.cradletrial.cradlevhtapp.viewmodel;

import android.content.Context;
import android.content.Intent;
import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.cradletrial.cradlevhtapp.R;
import com.cradletrial.cradlevhtapp.model.Patient.Patient;
import com.cradletrial.cradlevhtapp.view.PatientProfileActivity;

import org.w3c.dom.Text;

import java.io.Serializable;
import java.util.List;

public class PatientsViewAdapter extends RecyclerView.Adapter<PatientsViewAdapter.PatientViewHolder> implements Serializable{
    private List<Patient> patientList;
    private Context context;

    public PatientsViewAdapter(List<Patient> patientList, Context context) {
        this.patientList = patientList;
        this.context = context;
    }

    @NonNull
    @Override
    public PatientViewHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, int i) {
        View v = LayoutInflater.from(viewGroup.getContext())
                .inflate(R.layout.patient_view_layout, viewGroup, false);
        return new PatientViewHolder(v);

    }

    @Override
    public void onBindViewHolder(@NonNull PatientViewHolder patientViewHolder, int i) {
        Patient patient = patientList.get(i);

        patientViewHolder.patientVillage.setText("Village: "+patient.villageNumber);
        patientViewHolder.patientSex.setText(patient.patientSex+ " ");
        patientViewHolder.patientName.setText(patient.patientName);
        patientViewHolder.patientDOB.setText("Age: "+patient.ageYears+"");
        patientViewHolder.patientId.setText("ID: "+ patient.patientId);

        patientViewHolder.itemView.setOnClickListener(view -> {

            Intent intent = new Intent(context, PatientProfileActivity.class);
            Patient p = patientList.get(i);
            intent.putExtra("key", p);
            context.startActivity(intent);
        });
    }

    @Override
    public int getItemCount() {
        return patientList.size();
    }

    static class PatientViewHolder extends RecyclerView.ViewHolder {
        TextView patientName, patientDOB, patientSex, patientVillage, patientId;
        public PatientViewHolder(@NonNull View itemView) {
            super(itemView);
            patientId = itemView.findViewById(R.id.patientID);
            patientDOB = itemView.findViewById(R.id.patientDOB);
            patientName = itemView.findViewById(R.id.patientName);
            patientSex = itemView.findViewById(R.id.patientSex);
            patientVillage = itemView.findViewById(R.id.patientVillage);
        }
    }
}
