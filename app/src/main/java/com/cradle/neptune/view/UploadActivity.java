package com.cradle.neptune.view;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
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

import com.cradle.neptune.R;
import com.cradle.neptune.dagger.MyApp;
import com.cradle.neptune.manager.PatientManager;
import com.cradle.neptune.manager.UrlManager;
import com.cradle.neptune.manager.VolleyRequestManager;
import com.cradle.neptune.model.*;
import com.cradle.neptune.manager.ReadingManager;
import com.cradle.neptune.utilitiles.DateUtil;
import com.cradle.neptune.view.ui.network_volley.MultiReadingUploader;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import kotlin.Pair;

import org.threeten.bp.ZonedDateTime;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

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
    @Inject
    VolleyRequestManager volleyRequestManager;

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
        updateReadingUploadLabels();

        setupLastFollowupDownloadDate();

        setupUploadImageButton();

    }



    private void setupLastFollowupDownloadDate() {
        //get last updated time
        TextView lastDownloadText = findViewById(R.id.lastDownloadTimeTxt);
        try {
            //todo: this will probably be removed with new sync but also need to save long rather than string
            ZonedDateTime zonedDateTime = ZonedDateTime.parse(settings.getLastTimeFollowUpDownloaded());
            lastDownloadText.setText(DateUtil.getFullDateFromUnix(zonedDateTime.toEpochSecond()));

        } catch (Exception e) {
            lastDownloadText.setText(settings.getLastTimeFollowUpDownloaded());
        }
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
        if (urlManager.getReadings() == null || urlManager.getReadings().length() == 0) {
            Toast.makeText(this, "Error: Must set server URL in settings", Toast.LENGTH_LONG).show();
            return;
        }

        List<Reading> unUploadedReadings = readingManager.getUnUploadedReadingsBlocking();

        if (unUploadedReadings.size() == 0) {
            Toast.makeText(this, "No readings needing to be uploaded.", Toast.LENGTH_LONG).show();
            return;
        }

        // upload multiple readings
        multiUploader = new MultiReadingUploader(this, getProgressCallbackListener());
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
