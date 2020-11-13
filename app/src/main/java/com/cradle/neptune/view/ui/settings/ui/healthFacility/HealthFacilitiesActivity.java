package com.cradle.neptune.view.ui.settings.ui.healthFacility;

import android.app.SearchManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.cradle.neptune.R;
import com.cradle.neptune.model.HealthFacility;
import com.cradle.neptune.viewmodel.HealthFacilitiesAdapter;
import com.cradle.neptune.viewmodel.HealthFacilityViewModel;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class HealthFacilitiesActivity extends AppCompatActivity {

    private SearchView searchView;
    private HealthFacilitiesAdapter healthFacilitiesAdapter;
    private HealthFacilityViewModel healthFacilityViewModel;

    public static Intent makeIntent(Context context) {
        return new Intent(context, HealthFacilitiesActivity.class);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        healthFacilityViewModel = new ViewModelProvider(this)
                .get(HealthFacilityViewModel.class);
        setContentView(R.layout.activity_health_facilities);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.health_facilities_activity_title);
        }
        setupRecyclerview();
    }

    private void setupRecyclerview() {
        RecyclerView recyclerView = findViewById(R.id.hfRecyclerView);
        RecyclerView.LayoutManager layoutManager = new LinearLayoutManager(this);
        recyclerView.setLayoutManager(layoutManager);

        healthFacilitiesAdapter = new HealthFacilitiesAdapter();
        // observe the database for any changes and report back to the adapter
        healthFacilityViewModel.getAllFacilities().observe(this, healthFacilities -> {
            healthFacilitiesAdapter.setData(healthFacilities);
            healthFacilitiesAdapter.notifyDataSetChanged();
        });
        recyclerView.setAdapter(healthFacilitiesAdapter);

        healthFacilitiesAdapter.setAdapterClicker(healthFacility -> {
            final String msg;
            final String yesText;
            if (healthFacility.isUserSelected()) {
                msg = getString(
                        R.string.health_facilities_activity_remove_from_list_dialog_message);
                yesText = getString(
                        R.string.health_facilities_activity_remove_from_list_dialog_yes_button);
            } else {
                msg = getString(R.string.health_facilities_activity_add_to_list_dialog_message);
                yesText = getString(
                        R.string.health_facilities_activity_add_to_list_dialog_yes_button);
            }
            new MaterialAlertDialogBuilder(HealthFacilitiesActivity.this)
                    .setTitle(healthFacility.getName())
                    .setMessage(msg)
                    .setCancelable(true).setPositiveButton(yesText, (dialogInterface, i) -> {
                healthFacility.setUserSelected(!healthFacility.isUserSelected());
                healthFacilityViewModel.updateFacility(healthFacility);
            })
                    .setNegativeButton(android.R.string.cancel, null)
                    .create()
                    .show();
        });
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return super.onSupportNavigateUp();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_health_facility, menu);

        // Associate searchable configuration with the SearchView
        SearchManager searchManager = (SearchManager) getSystemService(Context.SEARCH_SERVICE);
        searchView = (SearchView) menu.findItem(R.id.searchHealthFacility)
                .getActionView();
        searchView.setSearchableInfo(searchManager
                .getSearchableInfo(getComponentName()));
        searchView.setMaxWidth(Integer.MAX_VALUE);

        // listening to search query text change
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                // filter recycler view when query submitted
                healthFacilitiesAdapter.getFilter().filter(query);
                return false;
            }

            @Override
            public boolean onQueryTextChange(String query) {
                // filter recycler view when text is changed
                healthFacilitiesAdapter.getFilter().filter(query);
                return false;
            }
        });
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.searchPatients) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    public interface AdapterClicker {
        void onClick(HealthFacility healthFacility);
    }

}
