package com.cradletrial.cradlevhtapp.view;

import android.content.Intent;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.CardView;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import com.cradletrial.cradlevhtapp.R;
import com.cradletrial.cradlevhtapp.view.ui.settings.SettingsActivity;

import static com.cradletrial.cradlevhtapp.view.TabActivityBase.TAB_ACTIVITY_BASE_SETTINGS_DONE;

public class DashBoardActivity extends AppCompatActivity  implements View.OnClickListener {


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dash_board);
        setupOnClickListner();
        getSupportActionBar().setDisplayShowHomeEnabled(true);
        getSupportActionBar().setDisplayUseLogoEnabled(true);
        getSupportActionBar().setTitle("");
        //getSupportActionBar().setLogo(getResources().getDrawable(R.drawable.upload));

    }
    private void setupOnClickListner(){
        View  patientView = findViewById(R.id.patientLayout);
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
        switch (v.getId()){
            case R.id.readingCardView:
                Log.d("bugg","reading clicked");
                startActivity(ReadingsListActivity.makeIntent(this));
                break;
            case R.id.patientCardview:
                Log.d("bugg","patient clicked");
                startActivity(PatientsActivity.makeIntent(this));
                break;
            case R.id.syncCardView:
                Log.d("bugg","upload clicked");

                startActivity(UploadActivity.makeIntent(this));
                break;
            case R.id.fabHelpDashboard:
                Log.d("bugg","fab clicked");

                startActivity(HelpActivity.makeIntent(this));
                break;

        }
    }
}
