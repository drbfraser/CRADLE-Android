package com.cradle.neptune.viewmodel;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.ImageView;
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

    private List<HealthFacilityEntity> healthFacilityEntities;
    private List<HealthFacilityEntity> filteredList;
    private HealthFacilitiesActivity.AdapterClicker AdapterClicker;

    public HealthFacilitiesAdapter(List<HealthFacilityEntity> healthFacilityEntities) {
        this.healthFacilityEntities = healthFacilityEntities;
        this.filteredList = healthFacilityEntities;
    }

    public void setAdapterClicker(HealthFacilitiesActivity.AdapterClicker adapterClicker) {
        this.AdapterClicker = adapterClicker;
    }

    @NonNull
    @Override
    public HealthFacilityViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.health_facility_layout, parent, false);
        return new HealthFacilityViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull HealthFacilityViewHolder holder, int position) {

        HealthFacilityEntity healthFacilityEntity = filteredList.get(position);

        holder.locationTxt.setText(healthFacilityEntity.getLocation());
        holder.nameTxt.setText(healthFacilityEntity.getName());
        holder.phoneTxt.setText(healthFacilityEntity.getPhoneNumber());
        holder.typeTxt.setText(healthFacilityEntity.getType());
        holder.aboutTxt.setText(healthFacilityEntity.getAbout());
        if (healthFacilityEntity.isUserSelected()) {
            holder.statusImg.setVisibility(View.VISIBLE);
        }
        holder.layout.setOnClickListener(view ->
                AdapterClicker.onClick(healthFacilityEntity));

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
                    filteredList = healthFacilityEntities;
                } else {
                    List<HealthFacilityEntity> newFilteredList = new ArrayList<>();
                    for (HealthFacilityEntity hf : healthFacilityEntities) {
                        if (hf.getLocation().toLowerCase().contains(charSequence.toString()) ||
                                hf.getName().toLowerCase().contains(charSequence.toString())) {
                            newFilteredList.add(hf);
                        }
                    }
                    filteredList = newFilteredList;
                }
                FilterResults filterResults = new FilterResults();
                filterResults.values = filteredList;
                return filterResults;
            }

            @Override
            protected void publishResults(CharSequence charSequence, FilterResults filterResults) {
                filteredList = (ArrayList<HealthFacilityEntity>) filterResults.values;
                notifyDataSetChanged();
            }
        };
    }

    static class HealthFacilityViewHolder extends RecyclerView.ViewHolder {

        TextView phoneTxt, nameTxt, locationTxt, aboutTxt,typeTxt;
        ConstraintLayout layout;
        ImageView statusImg;

        HealthFacilityViewHolder(@NonNull View itemView) {
            super(itemView);
            phoneTxt = itemView.findViewById(R.id.hfNumberTxt);
            nameTxt = itemView.findViewById(R.id.hfNameTxt);
            locationTxt = itemView.findViewById(R.id.hfLocationTxt);
            layout = itemView.findViewById(R.id.hfLayout);
            statusImg = itemView.findViewById(R.id.hfImageview);
            aboutTxt = itemView.findViewById(R.id.hfAbouttxt);
            typeTxt = itemView.findViewById(R.id.hfTypeTxt);
        }
    }
}
