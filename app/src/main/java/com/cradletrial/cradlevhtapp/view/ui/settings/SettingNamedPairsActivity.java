package com.cradletrial.cradlevhtapp.view.ui.settings;

import android.content.Context;
import android.content.Intent;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.MenuItem;

import com.cradletrial.cradlevhtapp.R;
import com.cradletrial.cradlevhtapp.view.ui.settings.ui.settingnamedpairs.SettingNamedPairsFragment;


public class SettingNamedPairsActivity extends AppCompatActivity {
    public enum SelectPair {
        SELECT_PAIR_HEALTH_CENTRES,
        SELECT_PAIR_UPLOAD_SERVERS
    }

    public static Intent makeLaunchIntent(Context context, SelectPair selectPair) {
        Intent intent = new Intent(context, SettingNamedPairsActivity.class);
        intent.putExtra(EXTRA_SELECT, selectPair);
        return intent;
    }

    private SelectPair selectedPair;

    private static final String EXTRA_SELECT = "pick which named pairs to select";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // extract extra
        selectedPair = (SelectPair) getIntent().getSerializableExtra(EXTRA_SELECT);

        setContentView(R.layout.setting_named_pairs_activity);
        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.container, SettingNamedPairsFragment.newInstance())
                    .commitNow();
        }

        // enable left-arrow on menu bar
        ActionBar actionBar = getSupportActionBar();
        actionBar.setDisplayHomeAsUpEnabled(true);
    }

    // handle left-arrow on menu bar
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) {
            finish();
        }
        return super.onOptionsItemSelected(item);
    }

    public SelectPair getSelectedPair() {
        return selectedPair;
    }
}
