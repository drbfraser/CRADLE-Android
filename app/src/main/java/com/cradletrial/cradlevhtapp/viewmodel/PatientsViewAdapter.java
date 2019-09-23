package com.cradletrial.cradlevhtapp.viewmodel;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.cradletrial.cradlevhtapp.R;
import com.cradletrial.cradlevhtapp.model.Patient.Patient;

import org.w3c.dom.Text;

import java.util.List;

public class PatientsViewAdapter extends RecyclerView.Adapter<PatientsViewAdapter.PatientViewHolder> {
    private List<Patient> patientList;
    private Context context;

    public PatientsViewAdapter(List<Patient> patientList, Context context) {
        this.patientList = patientList;
        this.context = context;
    }

    @NonNull
    @Override
    public PatientViewHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, int i) {
        return null;
    }

    @Override
    public void onBindViewHolder(@NonNull PatientViewHolder patientViewHolder, int i) {

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
