package com.cradletrial.cradlevhtapp.viewmodel;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.cradletrial.cradlevhtapp.R;
import com.cradletrial.cradlevhtapp.model.Reading;

import java.util.List;

public class ReadingRecyclerViewAdapter extends RecyclerView.Adapter<ReadingRecyclerViewAdapter.MyViewHolder> {
    private List<Reading> readings;
    private Context context;


    public ReadingRecyclerViewAdapter(List<Reading> readings, Context context) {
        this.readings = readings;
        this.context = context;
    }



    @NonNull
    @Override
    public MyViewHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, int i) {
        return null;
    }

    @Override
    public void onBindViewHolder(@NonNull MyViewHolder myViewHolder, int i) {

    }

    @Override
    public int getItemCount() {
        return 0;
    }

    public static class MyViewHolder extends RecyclerView.ViewHolder {
        // each data item is just a string in this case
        TextView readingDate, assessmentDate, sysBP, diaBP,heartRate,diagnosis,treatment,other;
        ImageView trafficLight, arrow;
        public MyViewHolder(View v) {
            super(v);
            readingDate = v.findViewById(R.id.readingDate);
            assessmentDate =v.findViewById(R.id.assessmentDate);
            sysBP = v.findViewById(R.id.sysBP);
            diaBP = v.findViewById(R.id.diaBP);
            heartRate = v.findViewById(R.id.readingHeartRate);
            diagnosis = v.findViewById(R.id.readingdiagnosis);
            treatment = v.findViewById(R.id.readingTreatment);
            other = v.findViewById(R.id.readingOther);
            trafficLight = v.findViewById(R.id.readingTrafficLight);
            arrow = v.findViewById(R.id.readingArrow);
        }
    }
}
