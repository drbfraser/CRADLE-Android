package com.cradle.neptune.view.ui.settings.ui.healthFacility;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.app.SearchManager;
import android.content.Context;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;

import com.cradle.neptune.R;
import com.cradle.neptune.dagger.MyApp;
import com.cradle.neptune.database.HealthFacilityEntity;
import com.cradle.neptune.model.ReadingManager;
import com.cradle.neptune.viewmodel.HealthFacilitiesAdapter;

import java.util.List;

import javax.inject.Inject;

public class HealthFacilitiesActivity extends AppCompatActivity {

    @Inject
    ReadingManager readingManager;
    private SearchView searchView;
    private HealthFacilitiesAdapter healthFacilitiesAdapter;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_health_facilities);

        ((MyApp) getApplication()).getAppComponent().inject(this);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Health Facilities");
        }
        setupRecyclerview();
    }

    private void setupRecyclerview() {
        RecyclerView recyclerView = findViewById(R.id.hfRecyclerView);
        List<HealthFacilityEntity> healthFacilityEntities = readingManager.getAllFacilities();
        healthFacilitiesAdapter = new HealthFacilitiesAdapter(healthFacilityEntities);
        RecyclerView.LayoutManager layoutManager = new LinearLayoutManager(this);

        recyclerView.setAdapter(healthFacilitiesAdapter);
        recyclerView.setLayoutManager(layoutManager);
        healthFacilitiesAdapter.notifyDataSetChanged();

        healthFacilitiesAdapter.setAdapterClicker(healthFacilityEntity -> {
            String msg ="Add this facility to your list?";

            if (healthFacilityEntity.isUserSelected()){
                msg ="Remove this facility from your list?";
            }
            new AlertDialog.Builder(HealthFacilitiesActivity.this)
                    .setTitle(healthFacilityEntity.getName()).setMessage(msg)
                    .setCancelable(true).setPositiveButton("YES", (dialogInterface, i) -> {
                     healthFacilityEntity.setUserSelected(!healthFacilityEntity.isUserSelected());
                     readingManager.updateFacility(healthFacilityEntity);
                     healthFacilitiesAdapter.notifyDataSetChanged();
                    }).setNegativeButton("NO", (dialogInterface, i) -> {
                    })
                    .create().show();
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
        void onClick(HealthFacilityEntity healthFacilityEntity);
    }

}
