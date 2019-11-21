package com.cradle.neptune.view.ui.network_volley;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.util.Log;

import com.android.volley.NetworkResponse;
import com.android.volley.Response;
import com.cradle.neptune.model.Reading;
import com.cradle.neptune.model.Settings;
import com.cradle.neptune.utilitiles.DateUtil;
import com.cradle.neptune.utilitiles.GsonUtil;
import com.cradle.neptune.utilitiles.HybridFileEncrypter;
import com.cradle.neptune.utilitiles.Util;
import com.cradle.neptune.utilitiles.Zipper;
import com.cradle.neptune.view.LoginActivity;

import org.threeten.bp.ZonedDateTime;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.ConnectException;
import java.net.UnknownHostException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.List;

/**
 * Handle uploading multiple readings to the server.
 * Interface with client code (an activity) via callbacks.
 */
public class MultiReadingUploader {
    private static final String TAG = "MultiReadingUploader";
    private Context context;
    private Settings settings;
    private List<Reading> readings;
    private ProgressCallback progressCallback;
    private State state = State.IDLE;
    private int numCompleted = 0;

    public MultiReadingUploader(Context context, Settings settings, ProgressCallback progressCallback) {
        this.context = context;
        this.settings = settings;
        this.progressCallback = progressCallback;
    }

    // OPERATIONS
    public void startUpload(List<Reading> readings) {
        if (state != State.IDLE) {
            Log.e(TAG, "ERROR: Not in idle state");
        } else {
            Util.ensure(readings != null && readings.size() > 0);
            this.readings = readings;
            startUploadOfPendingReading();
            progressCallback.uploadProgress(numCompleted, getTotalNumReadings());
        }
    }

    public void abortUpload() {
        state = State.DONE;
        readings.clear();
    }

    public void resumeUploadBySkip() {
        if (state != State.PAUSED) {
            Log.e(TAG, "ERROR: Not in paused state");
        } else {
            Util.ensure(readings != null && readings.size() > 0);

            // skip
            readings.remove(0);
            if (readings.size() > 0) {
                startUploadOfPendingReading();
            } else {
                state = State.DONE;
            }
            progressCallback.uploadProgress(numCompleted, getTotalNumReadings());
        }
    }

    public void resumeUploadByRetry() {
        if (state != State.PAUSED) {
            Log.e(TAG, "ERROR: Not in paused state");
        } else {
            Util.ensure(readings != null && readings.size() > 0);
            startUploadOfPendingReading();
            progressCallback.uploadProgress(numCompleted, getTotalNumReadings());
        }
    }

    // State
    public boolean isUploadingReadingToStart() {
        return state == State.IDLE;
    }

    public boolean isUploading() {
        return state == State.UPLOADING;
    }

    public boolean isUploadPaused() {
        return state == State.PAUSED;
    }

    public boolean isUploadDone() {
        return state == State.DONE;
    }

    // info
    public int getNumCompleted() {
        return numCompleted;
    }

    public int getNumRemaining() {
        return readings.size();
    }

    public int getTotalNumReadings() {
        return numCompleted + getNumRemaining();
    }

    // CONSTRUCT DATA ZIP FILES
    private File zipReading(Reading reading) throws IOException {
        List<File> filesToZip = new ArrayList<>();

        // 1. CRADLE screenshot (if any)
        if (settings.shouldUploadImages()) {
            if (reading.pathToPhoto != null && reading.pathToPhoto.length() > 0) {
                filesToZip.add(new File(reading.pathToPhoto));
            }
        }

        // 2. JSON of reading data
        File jsonFile = new File(context.getCacheDir(),
                "reading_" + reading.patient.patientId + "@" + DateUtil.getISODateForFilename(ZonedDateTime.now()) + ".json");
        String jsonData = GsonUtil.getJsonForSyncingToServer(reading, settings);
        try (FileWriter writer = new FileWriter(jsonFile)) {
            writer.write(jsonData);
        }
        filesToZip.add(jsonFile);

        // zip to internal cache
        File zipFile = new File(context.getCacheDir(), "upload_reading_" + SystemClock.uptimeMillis() + ".zip");
        return Zipper.zip(filesToZip, zipFile);
    }

    // INTERNAL STATE MACHINE OPERATIONS
    private void startUploadOfPendingReading() {
        Util.ensure(readings.size() > 0);
        state = State.UPLOADING;

        File zipFile = null;
        File encryptedZip = null;
        try {
            // zip data
            zipFile = zipReading(readings.get(0));

            // generate zip of encrypted data
            String encryptedZipFileFolder = context.getCacheDir().getAbsolutePath();
            encryptedZip = HybridFileEncrypter.hybridEncryptFile(zipFile, encryptedZipFileFolder, settings.getRsaPubKey());

            String readingJson = Reading.getJsonObj(readings.get(0),context);
            // start upload
            SharedPreferences sharedPref = context.getSharedPreferences("login",Context.MODE_PRIVATE);
            String token = sharedPref.getString(LoginActivity.TOKEN,LoginActivity.DEFAULT_TOKEN);

            Uploader uploader = new Uploader(
                    settings.getReadingServerUrl(),
                    settings.getServerUserName(),
                    settings.getServerPassword(),token);
            uploader.doUpload(readingJson, getSuccessCallback(), getErrorCallback());

        } catch (IOException | GeneralSecurityException ex) {
            Log.e(TAG, "Exception with encrypting and transmitting data!", ex);
            state = State.PAUSED;
            progressCallback.uploadPausedOnError("Encrypting data for upload failed (" + ex.getMessage() + ")");
        } finally {
            // cleanup
            Util.deleteFile(encryptedZip);
            Util.deleteFile(zipFile);
        }

    }

    private Response.Listener<NetworkResponse> getSuccessCallback() {
        return response -> {
            // handle aborted upload:
            if (state == State.DONE) {
                return;
            }

            // current reading uploaded successfully
            Util.ensure(readings.size() > 0);
            progressCallback.uploadReadingSucceeded(readings.get(0));
            readings.remove(0);
            numCompleted++;
            progressCallback.uploadProgress(numCompleted, getTotalNumReadings());

            // advance to next reading
            if (readings.size() > 0) {
                startUploadOfPendingReading();
            } else {
                state = State.DONE;
            }
        };
    }

    private Response.ErrorListener getErrorCallback() {
        return error -> {
            // handle aborted upload:
            if (state == State.DONE) {
                return;
            }
            state = State.PAUSED;

            // error uploading current reading
            String message = "Unable to upload to server (network error)";
            if (error == null) {
                // do nothing special
            } else if (error.getCause() != null) {
                if (error.getCause().getClass() == UnknownHostException.class) {
                    message = "Unable to resolve server address; check server URL in settings.";
                } else if (error.getCause().getClass() == ConnectException.class) {
                    message = "Cannot reach server; check network connection.";
                } else {
                    message = error.getCause().getMessage();
                }
            } else if (error.networkResponse != null) {
                switch (error.networkResponse.statusCode) {
                    case 401:
                        message = "Server rejected username and password; check they are correct in settings.";
                        break;
                    case 400:
                        message = "Server rejected upload request; check server URL in settings.";
                        break;
                    case 404:
                        message = "Server rejected URL; check server URL in settings.";
                        break;
                    default:
                        message = "Server rejected upload; check server URL in settings. Code " + error.networkResponse.statusCode;
                }
            }
            progressCallback.uploadPausedOnError(message);
        };

    }

    private enum State {IDLE, UPLOADING, PAUSED, DONE}

    public interface ProgressCallback {
        void uploadProgress(int numCompleted, int numTotal);

        void uploadReadingSucceeded(Reading reading);

        void uploadPausedOnError(String message);
    }
}
