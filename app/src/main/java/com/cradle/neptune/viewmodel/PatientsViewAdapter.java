package com.cradle.neptune.viewmodel;

import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;

import com.cradle.neptune.R;
import com.cradle.neptune.model.Patient;
import com.cradle.neptune.model.Reading;
import com.cradle.neptune.view.PatientProfileActivity;

import java.util.ArrayList;
import java.util.List;

public class PatientsViewAdapter extends RecyclerView.Adapter<PatientsViewAdapter.PatientViewHolder> implements Filterable {
    private List<Pair<Patient, Reading>> patientList;
    private List<Pair<Patient, Reading>> filteredpatientList;

    private Context context;

    public PatientsViewAdapter(List<Pair<Patient, Reading>> patientList, Context context) {
        this.patientList = patientList;
        this.filteredpatientList = patientList;
        this.context = context;
    }

    @NonNull
    @Override
    public PatientViewHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, int i) {
        View v = LayoutInflater.from(viewGroup.getContext())
                .inflate(R.layout.patient_card, viewGroup, false);
        return new PatientViewHolder(v);

    }

    @Override
    public void onBindViewHolder(@NonNull PatientViewHolder patientViewHolder, int i) {
        Pair<Patient, Reading> pair = patientList.get(i);
        Patient patient = pair.first;
        Reading reading = pair.second;
        patientViewHolder.patientVillage.setText(patient.villageNumber);
        patientViewHolder.patientName.setText(patient.patientName);
        patientViewHolder.patientId.setText(patient.patientId);

        if (reading.readingFollowUp != null) {
            patientViewHolder.referralImg.setImageResource(R.drawable.ic_pending_referral_black_24dp);
            patientViewHolder.referralImg.setVisibility(View.VISIBLE);
        } else if (reading.isReferredToHealthCentre()) {
            patientViewHolder.referralImg.setImageResource(R.drawable.ic_pending_referral_black_24dp);
            patientViewHolder.referralImg.setVisibility(View.VISIBLE);
        }

        patientViewHolder.patientCardview.setOnClickListener(view -> {

            Intent intent = new Intent(context, PatientProfileActivity.class);
            intent.putExtra("key", patient);
            context.startActivity(intent);
        });
    }

    @Override
    public int getItemCount() {
        return filteredpatientList.size();
    }

    @Override
    public Filter getFilter() {
        return new Filter() {
            @Override
            protected FilterResults performFiltering(CharSequence charSequence) {
                String charString = charSequence.toString();
                if (charString.isEmpty()) {
                     filteredpatientList= patientList;
                } else {
                    List<Pair<Patient, Reading>> filteredList = new ArrayList<>();
                    for (Pair<Patient,Reading> pair : patientList) {
                        if (pair.first.patientId.contains(charString) ||pair.first.patientName.contains(charString)){
                            Log.d("bugg","matching: search: "+ charString+ " matchID: "+pair.first.patientId+ " NAME: "+pair.first.patientName);
                            filteredList.add(pair);
                        }
                    }

                    filteredpatientList = filteredList;
                }

                FilterResults filterResults = new FilterResults();
                filterResults.values = filteredpatientList;
                return filterResults;
            }

            @Override
            protected void publishResults(CharSequence charSequence, FilterResults filterResults) {

                filteredpatientList = (ArrayList<Pair<Patient, Reading>>) filterResults.values;
                notifyDataSetChanged();
            }
        };
    }

    static class PatientViewHolder extends RecyclerView.ViewHolder {
        TextView patientName, patientVillage, patientId;
        CardView patientCardview;
        ImageButton referralImg;

        public PatientViewHolder(@NonNull View itemView) {
            super(itemView);
            patientCardview = itemView.findViewById(R.id.patientCardview);
            patientId = itemView.findViewById(R.id.patientID);
            patientName = itemView.findViewById(R.id.patientName);
            patientVillage = itemView.findViewById(R.id.patientVillage);
            referralImg = itemView.findViewById(R.id.referralStatus);
        }
    }
}
