package com.cradletrial.cradlevhtapp.view;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
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
        //getSupportActionBar().setDisplayShowHomeEnabled(true);
        //getSupportActionBar().setDisplayUseLogoEnabled(true);
        //getSupportActionBar().setLogo(getResources().getDrawable(R.drawable.upload));

    }
    private void setupOnClickListner(){
         View  patientCard = findViewById(R.id.patientLayout);
         View statCard = findViewById(R.id.statLayout);
        View uploadCard = findViewById(R.id.uploadlayout);
        View readingLayout = findViewById(R.id.readingLayout);
//        uploadCard.setOnClickListener(this);
//        patientCard.setOnClickListener(this::onClick);
//        helpCard.setOnClickListener(this::onClick);
//        statCard.setOnClickListener(this);
//        referralCard.setOnClickListener(this::onClick);
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
//        switch (v.getId()){
//            case R.id.readingLayout:
//                startActivity(ReadingsListActivity.makeIntent(this));
//                break;
//            case R.id.patientLayout:
//                startActivity(PatientsActivity.makeIntent(this));
//                break;
//            case R.id.uploadlayout:
//                startActivity(UploadActivity.makeIntent(this));
//                break;
//            case R.id.helpLayout:
//                startActivity(HelpActivity.makeIntent(this));
//                break;
//
//        }
    }
}
