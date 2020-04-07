package com.cradle.neptune.viewmodel;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.recyclerview.widget.RecyclerView;

import com.cradle.neptune.R;
import com.cradle.neptune.database.HealthFacilityEntity;
import com.cradle.neptune.view.ui.settings.ui.healthFacility.HealthFacilitiesActivity;

import java.util.ArrayList;
import java.util.List;


public class HealthFacilitiesAdapter extends RecyclerView.Adapter<HealthFacilitiesAdapter.HealthFacilityViewHolder> implements Filterable {

    List<HealthFacilityEntity> healthFacilityEntities;
    List<HealthFacilityEntity> filteredList;
    HealthFacilitiesActivity.onClick onClick;

    public HealthFacilitiesAdapter(List<HealthFacilityEntity> healthFacilityEntities) {
        this.healthFacilityEntities = healthFacilityEntities;
        this.filteredList=healthFacilityEntities;
    }

    public void setOnClick(HealthFacilitiesActivity.onClick onClick) {
        this.onClick = onClick;
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

        holder.layout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                onClick.onClick(healthFacilityEntity);
            }
        });

    }

    @Override
    public int getItemCount() {
        return filteredList.size();
    }

    @Override
    public Filter getFilter() {
        return new Filter() {
            @Override
            protected FilterResults performFiltering(CharSequence charSequence) {
                String charString = charSequence.toString().toLowerCase();
                if (charString.isEmpty()) {
                    filteredList= healthFacilityEntities;
                } else {
                    List<HealthFacilityEntity> newFilteredList = new ArrayList<>();
                    for (HealthFacilityEntity hf: healthFacilityEntities){
                        if (hf.getLocation().toLowerCase().contains(charSequence.toString())||
                        hf.getName().toLowerCase().contains(charSequence.toString())){
                            newFilteredList.add(hf);
                        }
                    }
                    filteredList=newFilteredList;
                }
                FilterResults filterResults = new FilterResults();
                filterResults.values = filterResults;
                return filterResults;
            }

            @Override
            protected void publishResults(CharSequence charSequence, FilterResults filterResults) {
                filteredList = (List<HealthFacilityEntity>) filterResults.values;
                notifyDataSetChanged();
            }
        };
    }

    class HealthFacilityViewHolder extends RecyclerView.ViewHolder{

        TextView phoneTxt, nameTxt, locationTxt;
        ConstraintLayout layout;

        public HealthFacilityViewHolder(@NonNull View itemView) {
            super(itemView);
            phoneTxt = itemView.findViewById(R.id.hfNumberTxt);
            nameTxt = itemView.findViewById(R.id.hfNameTxt);
            locationTxt = itemView.findViewById(R.id.hfLocationTxt);
            layout = itemView.findViewById(R.id.hfLayout);
        }
    }
}
