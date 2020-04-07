package com.cradle.neptune.viewmodel;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.cradle.neptune.R;
import com.cradle.neptune.database.HealthFacilityEntity;

import java.util.List;


public class HealthFacilitiesAdapter extends RecyclerView.Adapter<HealthFacilitiesAdapter.HealthFacilityViewHolder>  {

    List<HealthFacilityEntity> healthFacilityEntities;

    public HealthFacilitiesAdapter(List<HealthFacilityEntity> healthFacilityEntities) {
        this.healthFacilityEntities = healthFacilityEntities;
    }

    @NonNull
    @Override
    public HealthFacilityViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.health_facility_layout, parent, false);
        return new HealthFacilitiesAdapter.HealthFacilityViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull HealthFacilityViewHolder holder, int position) {

        HealthFacilityEntity healthFacilityEntity = healthFacilityEntities.get(position);

        holder.locationTxt.setText(healthFacilityEntity.getLocation());
        holder.nameTxt.setText(healthFacilityEntity.getName());
        holder.phoneTxt.setText(healthFacilityEntity.getPhoneNumber());
    }

    @Override
    public int getItemCount() {
        return healthFacilityEntities.size();
    }

    class HealthFacilityViewHolder extends RecyclerView.ViewHolder{
        TextView phoneTxt, nameTxt, locationTxt;

        public HealthFacilityViewHolder(@NonNull View itemView) {
            super(itemView);
            phoneTxt = itemView.findViewById(R.id.hfNumberTxt);
            nameTxt = itemView.findViewById(R.id.hfNameTxt);
            locationTxt = itemView.findViewById(R.id.hfLocationTxt);
        }
    }
}
