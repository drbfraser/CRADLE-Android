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
import com.cradle.neptune.model.*;
import com.cradle.neptune.service.ReadingService;
import com.cradle.neptune.utilitiles.DateUtil;
import com.cradle.neptune.view.ui.network_volley.MultiReadingUploader;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import kotlin.NotImplementedError;
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
    ReadingService readingService;
    @Inject
    SharedPreferences sharedPreferences;
    @Inject
    Settings settings;

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
                String token = sharedPreferences.getString(LoginActivity.TOKEN, "");
                new AlertDialog.Builder(UploadActivity.this)
                        .setMessage("Downloading the patient data might take a while, please " +
                                "do not close the application. Check the status of download" +
                                " status in the notification bar.")
                        .setTitle("Downloading patient data")
                        .setPositiveButton("OK", (dialogInterface, i)
                                -> {
                            LoginActivity.getAllMyPatients(token, readingService, UploadActivity.this);
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
            ZonedDateTime zonedDateTime = ZonedDateTime.parse(settings.getLastTimeFollowUpDownloaded());
            lastDownloadText.setText(DateUtil.getFullDateString(zonedDateTime));

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
        JsonRequest<JSONArray> jsonArrayRequest = new JsonArrayRequest(Request.Method.GET, settings.getReferralSummeriesServerUrl(),
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
        List<FollowUp> readingsFollowUps = new ArrayList<>();
        for (int i = 0; i < response.length(); i++) {
            try {
                JSONObject jsonObject = response.getJSONObject(i);

                String readingServerId = jsonObject.getString("readingId");
                //follow up info
                String followUpAction = jsonObject.optString("followUpAction","N/A");
                String treatment = jsonObject.getString("treatment");
                String diagnosis = jsonObject.getString("diagnosis");
                String referredBy = jsonObject.getString("referredBy");
                String dateAssessed = jsonObject.getString("dateAssessed");

                //follow up actions
                boolean followUpNeeded = jsonObject.optBoolean("followupNeeded",false);
                String followupNeededTill = jsonObject.optString("followUpNeededTill","N/A");
                String medicationPrescribed = jsonObject.optString("medicationPrescribed","N/A");
                String followupFrequencyUnit = jsonObject.optString("followupFrequencyUnit","N/A");
                int followupFrequencyValue = jsonObject.optInt("followupFrequencyValue");
                String specialInvestigation = jsonObject.optString("specialInvestigations","N/A");

                // health facility info
                JSONObject healthFacility = jsonObject.getJSONObject("healthFacility");
                String hfName = healthFacility.getString("name");
                String assessedBy = healthFacility
                        .getJSONObject("healthcareWorker").getString("email");
                //patient info
                JSONObject patient = jsonObject.getJSONObject("patient");
                String medicalInfo = patient.getString("medicalHistory");
                String drugInfo = patient.getString("drugHistory");
                String patientId = patient.getString("patientId");

                FollowUp readingFollowUp = new FollowUp(readingServerId, followUpAction,
                        treatment, diagnosis, hfName, dateAssessed, assessedBy, referredBy);
                readingFollowUp.setPatientDrugInfoUpdate(drugInfo);
                readingFollowUp.setPatientMedInfoUpdate(medicalInfo);
                readingFollowUp.setPatientId(patientId);
                readingsFollowUps.add(readingFollowUp);

                readingFollowUp.setFollowUpNeeded(followUpNeeded);
                readingFollowUp.setFollowUpNeededTill(followupNeededTill);
                readingFollowUp.setFollowUpFrequencyUnit(followupFrequencyUnit);
                readingFollowUp.setFollowUpFrequencyValue(followupFrequencyValue);
                readingFollowUp.setMedicationPrescribed(medicationPrescribed);
                readingFollowUp.setSpecialInvestigation(specialInvestigation);

            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
//        List<Reading> readings = readingManager.getReadings(this);
        List<Pair<Patient, Reading>> pairs = readingService.getAllReadingsBlocking();
        Map<String, Pair<Patient, Reading>> readingMap = new HashMap<>();
        for (Pair<Patient, Reading> pair : pairs) {
            readingMap.put(pair.getSecond().getId(), pair);
        }
        //update the followups
        for (FollowUp followUp : readingsFollowUps) {
            Pair<Patient, Reading> pair = readingMap.get(followUp.getReadingServerId());
            if (pair != null) {
                pair.getSecond().setFollowUp(followUp);
                pair.getFirst().getMedicalHistoryList().add(followUp.getPatientMedInfoUpdate().toLowerCase());
                pair.getFirst().getDrugHistoryList().add(followUp.getPatientDrugInfoUpdate().toLowerCase());
                readingService.updateReadingAsync(pair.getFirst(), pair.getSecond());
//                reading.patient.medicalHistoryList = new ArrayList<>();
//                reading.patient.drugHistoryList = new ArrayList<>();
//                reading.patient.medicalHistoryList.add(followUp.getPatientMedInfoUpdate().toLowerCase());
//                reading.patient.drugHistoryList.add(followUp.getPatientDrugInfoUpdate().toLowerCase());
//                readingManager.updateReading(this, reading);
            }
        }
    }

    private void upDateLastDownloadTime(ZonedDateTime now) {
        settings.saveLastTimeFollowUpDownloaded(now.toString());
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
        int numReadingsToUpload = readingService.getUnUploadedReadingsBlocking().size();
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
        throw new NotImplementedError("TODO: Implement Me!");
//        Button btnStart = findViewById(R.id.uploadImagesButton);
//        btnStart.setOnClickListener(view -> {
//            List<Reading> readings = readingManager.getReadings(this);
//            List<Reading> readingsToUpload = new ArrayList<>();
//            for (int i = 0; i < readings.size(); i++) {
//                Reading reading = readings.get(i);
//                if (reading.pathToPhoto != null) {
//                    File file = new File(reading.pathToPhoto);
//                    if (!reading.isImageUploaded && file.exists()) {
//
//                        readingsToUpload.add(reading);
//                    }
//                }
//            }
//            if (readingsToUpload.size() == 0) {
//                Toast.makeText(this, "No more Images to upload!", Toast.LENGTH_LONG).show();
//                return;
//            }
//            final boolean[] stopuploading = {false};
//
//            ProgressBar progressBar = findViewById(R.id.progressBar);
//            Button stopButton = findViewById(R.id.stopuploading);
//            stopButton.setVisibility(View.VISIBLE);
//            stopButton.setOnClickListener(new View.OnClickListener() {
//                @Override
//                public void onClick(View view) {
//                    stopuploading[0] = true;
//                    progressBar.setVisibility(View.INVISIBLE);
//                    stopButton.setVisibility(View.GONE);
//                }
//            });
//            progressBar.setMax(readingsToUpload.size());
//            progressBar.setProgress(0);
//            progressBar.setVisibility(View.VISIBLE);
//
//            FirebaseStorage firebaseStorage = FirebaseStorage.getInstance();
//            StorageReference storageReference = firebaseStorage.getReference();
//
//            for (int i = 0; i < readingsToUpload.size(); i++) {
//                if (stopuploading[0]) {
//                    return;
//                }
//
//                Reading r = readingsToUpload.get(i);
//                Uri file = Uri.fromFile(new File(r.pathToPhoto));
//
//                StorageReference storageReference1 = storageReference.child("cradle-test-images/" + file.getLastPathSegment());
//                UploadTask uploadTask = storageReference1.putFile(file);
//                uploadTask.addOnSuccessListener(taskSnapshot -> {
//                    r.isImageUploaded = true;
//                    readingManager.updateReading(this, r);
//                    progressBar.setProgress(progressBar.getProgress() + 1);
//                    if (stopuploading[0]) {
//                        progressBar.setVisibility(View.INVISIBLE);
//                        stopButton.setVisibility(View.GONE);
//                        Toast.makeText(this, "All Images uploaded.", Toast.LENGTH_LONG).show();
//                    }
//                });
//                if (readingsToUpload.size() - 1 == i) {
//                    stopuploading[0] = true;
//                }
//            }
//        });
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
        if (settings.getReadingServerUrl() == null || settings.getReadingServerUrl().length() == 0) {
            Toast.makeText(this, "Error: Must set server URL in settings", Toast.LENGTH_LONG).show();
            return;
        }
//        if (settings.getRsaPubKey() == null || settings.getRsaPubKey().length() == 0) {
//            Toast.makeText(this, "Error: Must set RSA Public Key in settings", Toast.LENGTH_LONG).show();
//            return;
//        }
        // do a small sanity check on key
        // note: Many errors in key will seem valid here! No way to validate.
//        try {
//            HybridFileEncrypter.convertRsaPemToPublicKey(settings.getRsaPubKey());
//        } catch (Exception e) {
//            Toast.makeText(this, "Error: Invalid public key in settings: \r\n" + e.getMessage(), Toast.LENGTH_LONG).show();
//            return;
//        }

        // discover un-uploaded readings
        List<Pair<Patient, Reading>> pairs = readingService.getUnUploadedReadingsBlocking();
//        List<Reading> readingsToUpload = readingManager.getUnuploadedReadings();
        // abort if no readings
        if (pairs.size() == 0) {
            Toast.makeText(this, "No readings needing to be uploaded.", Toast.LENGTH_LONG).show();
            return;
        }

        // upload multiple readings
        multiUploader = new MultiReadingUploader(this, settings, sharedPreferences.getString(LoginActivity.TOKEN, ""), getProgressCallbackListener());
        multiUploader.startUpload(pairs);
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
                pair.getSecond().getMetadata().setDateUploadedToServer(ZonedDateTime.now());
                readingService.updateReadingAsync(pair.getFirst(), pair.getSecond());

                // record that we did a successful upload
                String dateStr = DateUtil.getFullDateString(ZonedDateTime.now());
                sharedPreferences.edit().putString(LAST_UPLOAD_DATE, dateStr).apply();
            }

            @Override
            public void uploadPausedOnError(String message) {
                TextView tv = UploadActivity.this.findViewById(R.id.tvUploadErrorMessage);
                tv.setText("Error when uploading reading: \r\n" + message);

                setErrorUiElementsVisible(View.VISIBLE);
            }
        };
    }


//    private void downloadPage() {
//        // Instantiate the RequestQueue.
//        RequestQueue queue = Volley.newRequestQueue(this);
//
//        // Request a string response from the provided URL.
//        StringRequest stringRequest = new StringRequest(Request.Method.GET, SERVER_URL,
//                new Response.Listener<String>() {
//                    @Override
//                    public void onResponse(String response) {
//                        // Display the first 500 characters of the response string.
//                        Log.d(TAG, "Response is: "+ response.substring(0,500));
//                        Toast.makeText(UploadActivity.this, "Completed GET", Toast.LENGTH_SHORT).show();
//                    }
//                }, new Response.ErrorListener() {
//            @Override
//            public void onErrorResponse(VolleyError error) {
//                Toast.makeText(UploadActivity.this, "GET failed: " + error.networkResponse, Toast.LENGTH_SHORT).show();
//            }
//        });
//
//        // Add the request to the RequestQueue.
//        queue.add(stringRequest);
//    }
}
