package com.cradleplatform.neptune.view.settings.adapters;

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

import com.cradleplatform.neptune.R;
import com.cradleplatform.neptune.model.HealthFacility;
import com.cradleplatform.neptune.view.settings.activities.HealthFacilitiesActivity;

import java.util.ArrayList;
import java.util.List;


public class HealthFacilitiesAdapter extends RecyclerView.Adapter<HealthFacilitiesAdapter.HealthFacilityViewHolder> implements Filterable {

    private List<HealthFacility> healthFacilityList;
    private List<HealthFacility> filteredList;
    private HealthFacilitiesActivity.AdapterClicker AdapterClicker;

    public HealthFacilitiesAdapter() {
        healthFacilityList = new ArrayList<>();
        filteredList = new ArrayList<>();
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
        } else {
            holder.statusImg.setVisibility(View.GONE);

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
                    filteredList = healthFacilityList;
                } else {
                    List<HealthFacility> newFilteredList = new ArrayList<>();
                    for (HealthFacility hf : healthFacilityList) {
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

    public void setData(List<HealthFacility> newData) {
        this.filteredList = newData;
        this.healthFacilityList = newData;
        notifyDataSetChanged();
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
