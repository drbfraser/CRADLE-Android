package com.cradle.neptune.view.ui.settings.ui.settingnamedpairs;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.os.Bundle;

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
        HealthFacilitiesAdapter healthFacilitiesAdapter = new HealthFacilitiesAdapter(healthFacilityEntities);
        RecyclerView.LayoutManager layoutManager = new LinearLayoutManager(this);

        recyclerView.setAdapter(healthFacilitiesAdapter);
        recyclerView.setLayoutManager(layoutManager);
        healthFacilitiesAdapter.notifyDataSetChanged();

        healthFacilitiesAdapter.setOnClick(new onClick() {
            @Override
            public void onClick(HealthFacilityEntity healthFacilityEntity) {
                new AlertDialog.Builder(HealthFacilitiesActivity.this)
                        .setTitle(healthFacilityEntity.getName()).setMessage("Would you live to add this facility to your list?")
                        .setCancelable(true).setPositiveButton("YES", (dialogInterface, i) -> {
                         healthFacilityEntity.setUserSelected(true);
                         readingManager.insert(healthFacilityEntity);
                        }).setNegativeButton("NO", (dialogInterface, i) -> {
                        })
                        .create().show();
            }
        });
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return super.onSupportNavigateUp();
    }


    public interface onClick{
        void onClick(HealthFacilityEntity healthFacilityEntity);
    }

}
