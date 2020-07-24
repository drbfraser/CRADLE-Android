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
import com.cradle.neptune.model.HealthFacility;
import com.cradle.neptune.view.ui.settings.ui.healthFacility.HealthFacilitiesActivity;

import java.util.ArrayList;
import java.util.List;


public class HealthFacilitiesAdapter extends RecyclerView.Adapter<HealthFacilitiesAdapter.HealthFacilityViewHolder> implements Filterable {

    private List<HealthFacility> healthFacilityEntities;
    private List<HealthFacility> filteredList;
    private HealthFacilitiesActivity.AdapterClicker AdapterClicker;

    public HealthFacilitiesAdapter(List<HealthFacility> healthFacilityEntities) {
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

        HealthFacility healthFacility = filteredList.get(position);

        holder.locationTxt.setText(healthFacility.getLocation());
        holder.nameTxt.setText(healthFacility.getName());
        holder.phoneTxt.setText(healthFacility.getPhoneNumber());
        holder.typeTxt.setText(healthFacility.getType());
        holder.aboutTxt.setText(healthFacility.getAbout());
        if (healthFacility.isUserSelected()) {
            holder.statusImg.setVisibility(View.VISIBLE);
        }
        holder.layout.setOnClickListener(view ->
                AdapterClicker.onClick(healthFacility));

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
                    List<HealthFacility> newFilteredList = new ArrayList<>();
                    for (HealthFacility hf : healthFacilityEntities) {
                        if (hf.getLocation().toLowerCase().contains(charSequence.toString()) ||
                                hf.getName().toLowerCase().contains(charSequence.toString()) ||
                                hf.getType().toLowerCase().contains(charSequence.toString()) ||
                                hf.getAbout().toLowerCase().contains(charSequence.toString())) {
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
                filteredList = (ArrayList<HealthFacility>) filterResults.values;
                notifyDataSetChanged();
            }
        };
    }

    static class HealthFacilityViewHolder extends RecyclerView.ViewHolder {

        TextView phoneTxt, nameTxt, locationTxt, aboutTxt, typeTxt;
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
