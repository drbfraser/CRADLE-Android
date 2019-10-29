package com.cradletrial.cradlevhtapp.view;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.webkit.WebView;

import com.cradletrial.cradlevhtapp.R;
import com.cradletrial.cradlevhtapp.dagger.MyApp;
import com.cradletrial.cradlevhtapp.model.Reading;
import com.cradletrial.cradlevhtapp.model.ReadingManager;
import com.cradletrial.cradlevhtapp.model.Settings;
import com.cradletrial.cradlevhtapp.viewmodel.ReadingViewAdapter;

import java.util.Collections;
import java.util.List;

import javax.inject.Inject;


public class ReadingsListActivity extends TabActivityBase {
    public static final int READING_ACTIVITY_DONE = 12345;

    // Data Model
    @Inject
    ReadingManager readingManager;
    @Inject
    SharedPreferences sharedPreferences;
    // ..inject this even if not needed because it forces it to load at startup and initialize.
    @Inject
    Settings settings;

    // UI Components
    private RecyclerView readingsRecyclerView;
    private ReadingViewAdapter listAdapter;

    public ReadingsListActivity() {
        super(R.id.nav_readings);
    }

    public static Intent makeIntent(Context context) {
        return new Intent(context, ReadingsListActivity.class);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // settings: load defaults if not previously set
        android.support.v7.preference.PreferenceManager.setDefaultValues(
                this, R.xml.preferences, false);

        // inject:
        ((MyApp) getApplication()).getAppComponent().inject(this);

        // setup UI
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_readings_list);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        this.setTitle(R.string.title_activity_readings);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }


        // floating action bar: create new currentReading
        FloatingActionButton fab = findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = ReadingActivity.makeIntentForNewReading(ReadingsListActivity.this);
                startActivityForResult(intent, READING_ACTIVITY_DONE);
            }
        });

        updateUi();
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }

    /**
     * List View
     */
    private void setupReadingsRecyclerView() {
        readingsRecyclerView = findViewById(R.id.reading_list_view);

        // Improve performance: size of each entry does not change.
        readingsRecyclerView.setHasFixedSize(true);

        // use linear layout
        RecyclerView.LayoutManager layoutManager = new LinearLayoutManager(this);
        readingsRecyclerView.setLayoutManager(layoutManager);

        // get content & sort
        List<Reading> readings = readingManager.getReadings(this);
        Collections.sort(readings, new Reading.ComparatorByDateReverse());

        // set adapter
        listAdapter = new ReadingViewAdapter(readings);
        listAdapter.setOnClickElementListener(new ReadingViewAdapter.OnClickElement() {
            @Override
            public void onClick(long readingId) {
                Intent intent = ReadingActivity.makeIntentForEdit(ReadingsListActivity.this, readingId);
                startActivityForResult(intent, READING_ACTIVITY_DONE);
            }

            @Override
            public boolean onLongClick(long readingId) {
                askToDeleteReading(readingId);
                return true;
            }

            @Override
            public void onClickRecheckReading(long readingId) {
                Intent intent = ReadingActivity.makeIntentForRecheck(ReadingsListActivity.this, readingId);
                startActivityForResult(intent, READING_ACTIVITY_DONE);
            }
        });

        readingsRecyclerView.setAdapter(listAdapter);
    }

    private void askToDeleteReading(long readingId) {
        AlertDialog.Builder dialog = new AlertDialog.Builder(this)
                .setMessage("Delete reading?")
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setPositiveButton(android.R.string.yes, (dialog1, whichButton) -> {
                    readingManager.deleteReadingById(this, readingId);
                    updateUi();
                })
                .setNegativeButton(android.R.string.no, null);
        dialog.show();
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if (requestCode == READING_ACTIVITY_DONE) {
            updateUi();
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    private void updateUi() {
        setupEmptyState();
        setupReadingsRecyclerView();
    }

    private void setupEmptyState() {
        WebView wv = findViewById(R.id.webViewEmptyState);
        String emptyMessageHtml = getString(R.string.reading_tab_empty_state);

        wv.loadDataWithBaseURL(null, emptyMessageHtml, "text/html", "utf-8", null);
        if (readingManager.getReadings(this).size() == 0) {
            wv.setVisibility(View.VISIBLE);
        } else {
            wv.setVisibility(View.INVISIBLE);
        }
    }


}
