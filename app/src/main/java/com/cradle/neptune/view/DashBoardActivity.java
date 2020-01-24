package com.cradle.neptune.view;

import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import com.cradle.neptune.R;
import com.cradle.neptune.view.ui.settings.SettingsActivity;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import static com.cradle.neptune.view.TabActivityBase.TAB_ACTIVITY_BASE_SETTINGS_DONE;

public class DashBoardActivity extends AppCompatActivity implements View.OnClickListener {
    public static final int READING_ACTIVITY_DONE = 12345;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dash_board);
        setupOnClickListner();
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayShowHomeEnabled(true);
            actionBar.setDisplayUseLogoEnabled(true);
            actionBar.setTitle("");
        }

    }

    private void setupOnClickListner() {
        View patientView = findViewById(R.id.patientLayout);
        CardView patientCardView = patientView.findViewById(R.id.patientCardview);

        View statView = findViewById(R.id.statLayout);
        CardView statCardview = statView.findViewById(R.id.statCardView);

        View uploadCard = findViewById(R.id.uploadlayout);
        CardView syncCardview = uploadCard.findViewById(R.id.syncCardView);

        View readingLayout = findViewById(R.id.readingLayout);
        CardView readingCardView = readingLayout.findViewById(R.id.readingCardView);

        FloatingActionButton helpButton = findViewById(R.id.fabHelpDashboard);
        readingCardView.setOnClickListener(this);
        syncCardview.setOnClickListener(this);
        patientCardView.setOnClickListener(this);
        statCardview.setOnClickListener(this);
        helpButton.setOnClickListener(this);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        if (id == R.id.action_settings) {
            Intent intent = SettingsActivity.makeLaunchIntent(this);
            startActivityForResult(intent, TAB_ACTIVITY_BASE_SETTINGS_DONE);
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.readingCardView:
                Intent intent = ReadingActivity.makeIntentForNewReading(DashBoardActivity.this);
                startActivityForResult(intent, READING_ACTIVITY_DONE);
                break;
            case R.id.patientCardview:
                startActivity(PatientsActivity.makeIntent(this));
                break;
            case R.id.syncCardView:
                startActivity(UploadActivity.makeIntent(this));
                break;
            case R.id.fabHelpDashboard:
                startActivity(HelpActivity.makeIntent(this));
                break;
            case R.id.statCardView:
                startActivity(new Intent(this,StatsActivity.class));
                break;

        }
    }
}
