package com.cradle.neptune.view.ui.network_volley;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.util.Log;

import com.android.volley.NetworkResponse;
import com.android.volley.Response;
import com.cradle.neptune.dagger.MyApp;
import com.cradle.neptune.manager.UrlManager;
import com.cradle.neptune.model.Patient;
import com.cradle.neptune.model.Reading;
import com.cradle.neptune.model.Settings;
import com.cradle.neptune.manager.MarshalManager;
import com.cradle.neptune.utilitiles.DateUtil;
import com.cradle.neptune.utilitiles.Util;
import com.cradle.neptune.utilitiles.Zipper;

import kotlin.Pair;
import org.threeten.bp.ZonedDateTime;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.ConnectException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

/**
 * Handle uploading multiple readings to the server.
 * Interface with client code (an activity) via callbacks.
 */
public class MultiReadingUploader {
    private static final String TAG = "MultiReadingUploader";
    @Inject
    SharedPreferences sharedPreferences;

    @Inject
    MarshalManager marshalManager;

    @Inject
    UrlManager urlManager;

    private Context context;
    private Settings settings;
    private List<Pair<Patient, Reading>> pairs;
    private ProgressCallback progressCallback;
    private State state = State.IDLE;
    private int numCompleted = 0;
    private String token;

    public MultiReadingUploader(Context context, Settings settings, String token, ProgressCallback progressCallback) {
        this.context = context;
        this.settings = settings;
        this.progressCallback = progressCallback;
        this.token = token;
        ((MyApp) context.getApplicationContext()).getAppComponent().inject(this);


    }

    // OPERATIONS
    public void startUpload(List<Pair<Patient, Reading>> pairs) {
        if (state != State.IDLE) {
            Log.e(TAG, "ERROR: Not in idle state");
        } else {
            Util.ensure(pairs != null && pairs.size() > 0);
            this.pairs = pairs;
            startUploadOfPendingReading();
            progressCallback.uploadProgress(numCompleted, getTotalNumReadings());
        }
    }

    public void abortUpload() {
        state = State.DONE;
        pairs.clear();
    }

    public void resumeUploadBySkip() {
        if (state != State.PAUSED) {
            Log.e(TAG, "ERROR: Not in paused state");
        } else {
            Util.ensure(pairs != null && pairs.size() > 0);

            // skip
            pairs.remove(0);
            if (pairs.size() > 0) {
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
            Util.ensure(pairs != null && pairs.size() > 0);
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
        return pairs.size();
    }

    public int getTotalNumReadings() {
        return numCompleted + getNumRemaining();
    }

    // CONSTRUCT DATA ZIP FILES
    private File zipReading(Pair<Patient, Reading> pair) throws IOException {
        List<File> filesToZip = new ArrayList<>();

        // 1. CRADLE screenshot (if any)
        // TODO: Re-enable this once we actually have this figured out
//        if (settings.shouldUploadImages()) {
//            if (pair.getSecond().getMetadata().getPhotoPath() != null && pair.getSecond().getMetadata().getPhotoPath().length() > 0) {
//                filesToZip.add(new File(pair.getSecond().getMetadata().getPhotoPath()));
//            }
//        }

        // 2. JSON of reading data
        File jsonFile = new File(context.getCacheDir(),
                "reading_" + pair.getFirst().getId() + "@" + DateUtil.getISODateForFilename(ZonedDateTime.now()) + ".json");
        String jsonData = marshalManager.marshalToUploadJson(pair.getFirst(), pair.getSecond()).toString();
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
        Util.ensure(pairs.size() > 0);
        state = State.UPLOADING;

        File zipFile = null;
        try {
            // zip data
            zipFile = zipReading(pairs.get(0));

            // generate zip of encrypted data
            String encryptedZipFileFolder = context.getCacheDir().getAbsolutePath();

            Patient patient = pairs.get(0).getFirst();
            Reading reading = pairs.get(0).getSecond();
            String readingJson = marshalManager.marshalToUploadJson(patient, reading).toString();
//            String readingJson = Reading.getJsonObj(readings.get(0), sharedPreferences.getString(LoginActivity.USER_ID, ""));
            // start upload
            Uploader uploader = new Uploader(urlManager.getReading(), "", "", token);
            uploader.doUpload(readingJson, getSuccessCallback(), getErrorCallback());

        } catch (IOException ex) {
            Log.e(TAG, "Exception with encrypting and transmitting data!", ex);
            state = State.PAUSED;
            progressCallback.uploadPausedOnError("Encrypting data for upload failed (" + ex.getMessage() + ")");
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            // cleanup
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
            Util.ensure(pairs.size() > 0);
            progressCallback.uploadReadingSucceeded(pairs.get(0));
            pairs.remove(0);
            numCompleted++;
            progressCallback.uploadProgress(numCompleted, getTotalNumReadings());

            // advance to next reading
            if (pairs.size() > 0) {
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

        void uploadReadingSucceeded(Pair<Patient, Reading> pair);

        void uploadPausedOnError(String message);
    }
}
