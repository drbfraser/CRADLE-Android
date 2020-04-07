package com.cradle.neptune.view.ui.settings.ui.settingnamedpairs;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;

import com.cradle.neptune.R;
import com.cradle.neptune.dagger.MyApp;
import com.cradle.neptune.model.ReadingManager;

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
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return super.onSupportNavigateUp();
    }

}
