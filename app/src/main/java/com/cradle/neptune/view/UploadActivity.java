package com.cradle.neptune.view;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonArrayRequest;
import com.android.volley.toolbox.JsonRequest;
import com.android.volley.toolbox.Volley;
import com.cradle.neptune.R;
import com.cradle.neptune.dagger.MyApp;
import com.cradle.neptune.manager.PatientManager;
import com.cradle.neptune.manager.UrlManager;
import com.cradle.neptune.model.*;
import com.cradle.neptune.manager.ReadingManager;
import com.cradle.neptune.utilitiles.DateUtil;
import com.cradle.neptune.view.ui.network_volley.MultiReadingUploader;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import kotlin.Pair;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.threeten.bp.ZonedDateTime;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;

public class UploadActivity extends AppCompatActivity {

    private static final String TAG = "UploadActivity";
    private static final String LAST_UPLOAD_DATE = "pref last upload date";

    // Data Model
    @Inject
    ReadingManager readingManager;
    @Inject
    SharedPreferences sharedPreferences;
    @Inject
    Settings settings;
    @Inject
    UrlManager urlManager;
    @Inject
    PatientManager patientManager;

    MultiReadingUploader multiUploader;


    public static Intent makeIntent(Context context) {
        return new Intent(context, UploadActivity.class);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // inject:
        ((MyApp) getApplication()).getAppComponent().inject(this);

        // setup UI
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_upload);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        // bottom bar nav in base class
        // setupBottomBarNavigation();

        // buttons
        setupUploadDataButton();
        setupErrorHandlingButtons();
        updateReadingUploadLabels();
        setupSyncFollowupButton();

        setupLastFollowupDownloadDate();

        setupUploadImageButton();

        setupGettingAllReadingsFromServer();
    }

    private void setupGettingAllReadingsFromServer() {
        Button uploadBtn = findViewById(R.id.updateAllPatientsBtn);
        uploadBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                new AlertDialog.Builder(UploadActivity.this)
                        .setMessage("Downloading the patient data might take a while, please " +
                                "do not close the application. Check the status of download" +
                                " status in the notification bar.")
                        .setTitle("Downloading patient data")
                        .setPositiveButton("OK", (dialogInterface, i)
                                -> {
                            LoginActivity.Companion.getAllMyPatients(sharedPreferences, readingManager, urlManager, patientManager,UploadActivity.this);
                        })
                        .setNegativeButton("Cancel", (dialogInterface, i) -> {

                        }).create().show();

            }
        });
    }

    private void setupLastFollowupDownloadDate() {
        //get last updated time
        TextView lastDownloadText = findViewById(R.id.lastDownloadTimeTxt);
        try {
            //todo: this will probably be removed with new sync but also need to save long rather than string
            ZonedDateTime zonedDateTime = ZonedDateTime.parse(settings.getLastTimeFollowUpDownloaded());
            lastDownloadText.setText(DateUtil.getFullDateFromMilliSeconds(zonedDateTime.toEpochSecond()*Referral.MS_IN_SECOND));

        } catch (Exception e) {
            lastDownloadText.setText(settings.getLastTimeFollowUpDownloaded());
        }
    }

    private void setupSyncFollowupButton() {

        Button syncButton = findViewById(R.id.downloadReadingButton);
        syncButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // setup the network call here
                requestFollowupFromNetwork();
            }
        });
    }

    private void requestFollowupFromNetwork() {
        String token = sharedPreferences.getString(LoginActivity.TOKEN, "");

        if (token.equals("")) {
            Snackbar.make(findViewById(R.id.cordinatorLayout), R.string.userNotAuthenticated, Snackbar.LENGTH_LONG).show();
            return;
        }

        Dialog dialog = new Dialog(this);
        dialog.setTitle("Syncing");
        dialog.setCancelable(false);
        dialog.show();
        JsonRequest<JSONArray> jsonArrayRequest = new JsonArrayRequest(Request.Method.GET, urlManager.getFollowUp(),
                null, response -> {
            getReadingFollowFromTheResponse(response);
            dialog.cancel();
            upDateLastDownloadTime(ZonedDateTime.now());
            setupLastFollowupDownloadDate();
            Snackbar.make(findViewById(R.id.cordinatorLayout), R.string.followUpDownloaded, Snackbar.LENGTH_LONG)
                    .show();
        }, error -> {

            dialog.cancel();
            Snackbar.make(findViewById(R.id.cordinatorLayout), R.string.followUpCheckInternet,
                    Snackbar.LENGTH_LONG).setActionTextColor(Color.RED).setAction("Try Again", null)
                    .show();
        }) {

            /**
             * Passing some request headers
             */
            @Override
            public Map<String, String> getHeaders() {
                HashMap<String, String> headers = new HashMap<>();
                //headers.put("Content-Type", "application/json");
                headers.put(LoginActivity.AUTH, "Bearer " + token);
                return headers;
            }
        };

        // add to volley queue
        RequestQueue queue = Volley.newRequestQueue(MyApp.getInstance());
        queue.add(jsonArrayRequest);

    }

    private void getReadingFollowFromTheResponse(JSONArray response) {
            //TODO update based on the new model
    }

    private void upDateLastDownloadTime(ZonedDateTime now) {
        settings.setLastTimeFollowUpDownloaded(now.toString());
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (multiUploader != null && multiUploader.isUploading()) {
            multiUploader.abortUpload();
            setUploadUiElementVisibility(false);
            updateReadingUploadLabels();
        }
    }

    private void updateReadingUploadLabels() {
        // reading count
        int numReadingsToUpload = readingManager.getUnUploadedReadingsBlocking().size();
//        int numReadingsToUpload = readingManager.getUnuploadedReadings().size();
        TextView tvReadingCount = findViewById(R.id.tvReadingsToUpload);
        tvReadingCount.setText(String.format("%d patient readings ready to upload", numReadingsToUpload));

        // upload date
        String uploadDate = sharedPreferences.getString(LAST_UPLOAD_DATE, null);
        String message = "No readings uploaded to server yet";
        if (uploadDate != null) {
            message = "Last upload: " + uploadDate;
        }
        TextView tvUploadDate = findViewById(R.id.tvLastUpdate);
        tvUploadDate.setText(message);
    }


    private void setupUploadDataButton() {
        Button btnStart = findViewById(R.id.btnUploadReadings);
        btnStart.setOnClickListener(view -> {
            // start upload
            uploadData();
        });
        setUploadUiElementVisibility(false);
    }

    /*
    uploads image to firebase
    todo: remove uploading directly to firebase and send to server for authentication first and find a better way to do this
     */
    private void setupUploadImageButton() {
        Button btnStart = findViewById(R.id.uploadImagesButton);
        btnStart.setOnClickListener(view -> {
            List<Reading> readings = readingManager.getAllReadingBlocking();

            List<Reading> readingsToUpload = new ArrayList<>();
            for (int i = 0; i < readings.size(); i++) {
                Reading reading = readings.get(i);
                if (reading.getMetadata().getPhotoPath() != null) {
                    File file = new File(reading.getMetadata().getPhotoPath());
                    if (!reading.getMetadata().isImageUploaded() && file.exists()) {

                        readingsToUpload.add(reading);
                    }
                }
            }
            if (readingsToUpload.size() == 0) {
                Toast.makeText(this, "No more Images to upload!", Toast.LENGTH_LONG).show();
                return;
            }
            final boolean[] stopuploading = {false};

            ProgressBar progressBar = findViewById(R.id.progressBar);
            Button stopButton = findViewById(R.id.stopuploading);
            stopButton.setVisibility(View.VISIBLE);
            stopButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    stopuploading[0] = true;
                    progressBar.setVisibility(View.INVISIBLE);
                    stopButton.setVisibility(View.GONE);
                }
            });
            progressBar.setMax(readingsToUpload.size());
            progressBar.setProgress(0);
            progressBar.setVisibility(View.VISIBLE);

            FirebaseStorage firebaseStorage = FirebaseStorage.getInstance();
            StorageReference storageReference = firebaseStorage.getReference();

            for (int i = 0; i < readingsToUpload.size(); i++) {
                if (stopuploading[0]) {
                    return;
                }

                Reading r = readingsToUpload.get(i);
                Uri file = Uri.fromFile(new File(r.getMetadata().getPhotoPath()));

                StorageReference storageReference1 = storageReference.child("cradle-test-images/" + file.getLastPathSegment());
                UploadTask uploadTask = storageReference1.putFile(file);
                uploadTask.addOnSuccessListener(taskSnapshot -> {
                    r.getMetadata().setImageUploaded(true);
                    readingManager.updateReading(r);
                    progressBar.setProgress(progressBar.getProgress() + 1);
                    if (stopuploading[0]) {
                        progressBar.setVisibility(View.INVISIBLE);
                        stopButton.setVisibility(View.GONE);
                        Toast.makeText(this, "All Images uploaded.", Toast.LENGTH_LONG).show();
                    }
                });
                if (readingsToUpload.size() - 1 == i) {
                    stopuploading[0] = true;
                }
            }
        });
    }

    private void setupErrorHandlingButtons() {
        setErrorUiElementsVisible(View.GONE);
        Button btnSkip = findViewById(R.id.btnSkip);
        btnSkip.setOnClickListener(view -> {
            Toast.makeText(this, "Skipping uploading reading...", Toast.LENGTH_SHORT).show();
            multiUploader.resumeUploadBySkip();
            setErrorUiElementsVisible(View.GONE);
        });

        Button btnRetry = findViewById(R.id.btnRetry);
        btnRetry.setOnClickListener(view -> {
            Toast.makeText(this, "Retrying uploading reading...", Toast.LENGTH_SHORT).show();
            multiUploader.resumeUploadByRetry();
            setErrorUiElementsVisible(View.GONE);
        });

        Button btnAbort = findViewById(R.id.btnStopUpload);
        btnAbort.setOnClickListener(view -> {
            Toast.makeText(this, "Stopping upload...", Toast.LENGTH_SHORT).show();
            multiUploader.abortUpload();

            setUploadUiElementVisibility(false);
            updateReadingUploadLabels();
        });
    }

    private void setUploadUiElementVisibility(boolean doingUpload) {
        Button btnStartUploading = findViewById(R.id.btnUploadReadings);
        Button btnAbort = findViewById(R.id.btnStopUpload);
        View groupUploading = findViewById(R.id.layoutUploadingReadings);

        btnStartUploading.setVisibility(doingUpload ? View.INVISIBLE : View.VISIBLE);
        groupUploading.setVisibility(doingUpload ? View.VISIBLE : View.GONE);
        btnAbort.setVisibility(View.VISIBLE);
        if (!doingUpload) {
            setErrorUiElementsVisible(View.GONE);
        }

    }

    private void setErrorUiElementsVisible(int visible) {
        Button btnSkip = findViewById(R.id.btnSkip);
        btnSkip.setVisibility(visible);
        Button btnRetry = findViewById(R.id.btnRetry);
        btnRetry.setVisibility(visible);
        TextView tv = findViewById(R.id.tvUploadErrorMessage);
        tv.setVisibility(visible);

        // upload icon
        ImageView iv = findViewById(R.id.ivUploadAction);
        if (visible == View.VISIBLE) {
            iv.setImageResource(R.drawable.arrow_right_with_x);
        } else {
            iv.setImageResource(R.drawable.arrow_right);
        }

    }


    private void uploadData() {
        if (multiUploader != null && !multiUploader.isUploadDone()) {
            Toast.makeText(this, "Already uploading; cannot restart", Toast.LENGTH_LONG).show();
            return;
        }
        // ensure settings OK
        if (urlManager.getReading() == null || urlManager.getReading().length() == 0) {
            Toast.makeText(this, "Error: Must set server URL in settings", Toast.LENGTH_LONG).show();
            return;
        }

        List<Reading> unUploadedReadings = readingManager.getUnUploadedReadingsBlocking();

        if (unUploadedReadings.size() == 0) {
            Toast.makeText(this, "No readings needing to be uploaded.", Toast.LENGTH_LONG).show();
            return;
        }

        // upload multiple readings
        multiUploader = new MultiReadingUploader(this, settings, sharedPreferences.getString(LoginActivity.TOKEN, ""), getProgressCallbackListener());
        multiUploader.startUpload(unUploadedReadings);
        setUploadUiElementVisibility(true);
    }

    MultiReadingUploader.ProgressCallback getProgressCallbackListener() {
        return new MultiReadingUploader.ProgressCallback() {
            @Override
            public void uploadProgress(int numCompleted, int numTotal) {
                if (numCompleted == numTotal) {
                    TextView tv = UploadActivity.this.findViewById(R.id.tvUploadMessage);
                    tv.setText("Done uploading " + numCompleted + " readings to server.");
                    updateReadingUploadLabels();

                    // button visibility
                    Button btnAbort = findViewById(R.id.btnStopUpload);
                    btnAbort.setVisibility(View.GONE);
                    Button btnStart = findViewById(R.id.btnUploadReadings);
                    btnStart.setVisibility(View.VISIBLE);

                    // upload icon
                    ImageView iv = findViewById(R.id.ivUploadAction);
                    iv.setImageResource(R.drawable.arrow_right_with_check);

                    Toast.makeText(UploadActivity.this, "Done uploading readings!", Toast.LENGTH_LONG).show();
                } else {
                    TextView tv = UploadActivity.this.findViewById(R.id.tvUploadMessage);
                    tv.setText("Uploading reading " + (numCompleted + 1) + " of " + numTotal + "...");
                }
            }

            @Override
            public void uploadReadingSucceeded(Pair<Patient, Reading> pair) {
                // mark reading as uploaded
                pair.getSecond().getMetadata().setDateUploadedToServer(ZonedDateTime.now().toEpochSecond());
                readingManager.updateReading(pair.getSecond());

                // record that we did a successful upload
                sharedPreferences.edit().putLong(LAST_UPLOAD_DATE,
                        ZonedDateTime.now().toEpochSecond()).apply();
            }

            @Override
            public void uploadPausedOnError(String message) {
                TextView tv = UploadActivity.this.findViewById(R.id.tvUploadErrorMessage);
                tv.setText("Error when uploading reading: \r\n" + message);

                setErrorUiElementsVisible(View.VISIBLE);
            }
        };
    }

}
